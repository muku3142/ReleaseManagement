package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.enrichment.StatusTransition;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraIssueHistoryAdapterTest {

    private static final String CHANGELOG_PATH = "/rest/api/3/issue/EXAMPLE-1/changelog";
    private static final String KEY = "EXAMPLE-1";

    private JiraIssueHistoryAdapter adapter(int maxPages) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new JiraIssueHistoryAdapter(RestClient.builder().requestFactory(requestFactory).build(), maxPages);
    }

    private JiraAccessContext access(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "557058:abc-123", "rm@acme.com", "tok-123");
    }

    private String fixture(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void keepsOnlyStatusChangesAndParsesTheTenantOffset(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(CHANGELOG_PATH)).willReturn(okJson(fixture("jira-changelog-page1.json"))));

        List<StatusTransition> transitions = adapter(20).statusTransitions(access(wm), KEY);

        assertThat(transitions).containsExactly(
                new StatusTransition(Instant.parse("2026-07-05T05:48:47.457Z"), "10880", "10881"),
                new StatusTransition(Instant.parse("2026-07-26T10:39:58.038Z"), "10881", "11071"),
                new StatusTransition(Instant.parse("2026-08-13T13:31:06.042Z"), "11071", "10882"));
        verify(getRequestedFor(urlPathEqualTo(CHANGELOG_PATH)).withQueryParam("startAt", equalTo("0")));
    }

    @Test
    void followsStartAtUntilTheLastPage(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(CHANGELOG_PATH)).withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson(fixture("jira-changelog-small-page.json"))));
        stubFor(get(urlPathEqualTo(CHANGELOG_PATH)).withQueryParam("startAt", equalTo("2"))
                .willReturn(okJson("""
                        {"startAt":2,"maxResults":2,"total":8,"isLast":true,"values":[
                          {"created":"2026-07-26T13:39:58.038+0300",
                           "items":[{"fieldId":"status","from":"10881","to":"11071"}]}]}
                        """)));

        List<StatusTransition> transitions = adapter(20).statusTransitions(access(wm), KEY);

        assertThat(transitions).extracting(StatusTransition::toStatusId).containsExactly("10881", "11071");
        verify(2, getRequestedFor(urlPathEqualTo(CHANGELOG_PATH)));
    }

    @Test
    void stopsOnAnEmptyPageEvenWhenNotMarkedLast(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(CHANGELOG_PATH))
                .willReturn(okJson("{\"startAt\":0,\"total\":8,\"isLast\":false,\"values\":[]}")));

        assertThat(adapter(20).statusTransitions(access(wm), KEY)).isEmpty();
        verify(1, getRequestedFor(urlPathEqualTo(CHANGELOG_PATH)));
    }

    @Test
    void refusesAnOverlongChangelogPastThePageCeiling(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(CHANGELOG_PATH)).willReturn(okJson(fixture("jira-changelog-small-page.json"))));

        assertThatThrownBy(() -> adapter(2).statusTransitions(access(wm), KEY))
                .isInstanceOfSatisfying(JiraTicketReadException.class,
                        e -> assertThat(e.reason()).isEqualTo(JiraAuthFailureReason.RESULT_SET_TOO_LARGE));
    }

    @Test
    void mapsUnauthorizedAndRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(CHANGELOG_PATH)).willReturn(unauthorized()));
        assertThatThrownBy(() -> adapter(20).statusTransitions(access(wm), KEY))
                .isInstanceOfSatisfying(JiraTicketReadException.class,
                        e -> assertThat(e.reason()).isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));

        stubFor(get(urlPathEqualTo(CHANGELOG_PATH)).willReturn(aResponse().withStatus(429)));
        assertThatThrownBy(() -> adapter(20).statusTransitions(access(wm), KEY))
                .isInstanceOfSatisfying(JiraTicketReadException.class,
                        e -> assertThat(e.reason()).isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
    }

    @Test
    void mapsStatusCategoriesByIdSoSameNamedStatusesKeepTheirOwn(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo("/rest/api/3/status")).willReturn(okJson(fixture("jira-statuses.json"))));

        Map<String, StatusCategory> categories = adapter(20).statusCategories(access(wm));

        assertThat(categories).containsEntry("10889", StatusCategory.IN_PROGRESS)
                .containsEntry("5", StatusCategory.DONE)
                .containsEntry("11105", StatusCategory.TO_DO)
                .containsEntry("10880", StatusCategory.TO_DO)
                .containsEntry("10881", StatusCategory.IN_PROGRESS)
                .containsEntry("10882", StatusCategory.DONE);
    }

    @Test
    void omitsStatusesWithAnUnrecognisedCategoryKey(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/status")).willReturn(okJson("""
                [{"id":"1","statusCategory":{"key":"new"}},{"id":"2","statusCategory":{"key":"undefined"}}]
                """)));

        assertThat(adapter(20).statusCategories(access(wm))).containsOnlyKeys("1");
    }
}
