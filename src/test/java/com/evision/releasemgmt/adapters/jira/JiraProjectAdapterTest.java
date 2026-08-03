package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.JiraVersion;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraProjectAdapterTest {

    private static final int TOO_MANY_REQUESTS = 429;

    private JiraProjectAdapter adapter(WireMockRuntimeInfo wm) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new JiraProjectAdapter(restClient);
    }

    private JiraAccessContext context(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "acct-1", "rm@acme.com", "tok-123");
    }

    @Test
    void singlePageOfProjectsIsMappedAndAuthorized(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,"
                        + "\"values\":[{\"id\":\"10023\",\"key\":\"CNV\",\"name\":\"Conversion\"}]}")));

        List<JiraProjectSummary> projects = adapter(wm).listProjects(context(wm));

        assertThat(projects).containsExactly(new JiraProjectSummary("10023", "CNV", "Conversion"));
        String expectedBase64 = Base64.getEncoder().encodeToString("rm@acme.com:tok-123".getBytes(StandardCharsets.UTF_8));
        verify(getRequestedFor(urlPathEqualTo("/rest/api/3/project/search"))
                .withHeader("Authorization", equalTo("Basic " + expectedBase64)));
    }

    @Test
    void twoPagesAreAccumulatedInOrder(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/project/search?startAt=0&maxResults=50")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":3,\"isLast\":false,"
                        + "\"values\":[{\"id\":\"1\",\"key\":\"A\",\"name\":\"Alpha\"},"
                        + "{\"id\":\"2\",\"key\":\"B\",\"name\":\"Beta\"}]}")));
        stubFor(get(urlEqualTo("/rest/api/3/project/search?startAt=2&maxResults=50")).willReturn(okJson(
                "{\"startAt\":2,\"maxResults\":50,\"total\":3,\"isLast\":true,"
                        + "\"values\":[{\"id\":\"3\",\"key\":\"C\",\"name\":\"Gamma\"}]}")));

        List<JiraProjectSummary> projects = adapter(wm).listProjects(context(wm));

        assertThat(projects).containsExactly(
                new JiraProjectSummary("1", "A", "Alpha"),
                new JiraProjectSummary("2", "B", "Beta"),
                new JiraProjectSummary("3", "C", "Gamma"));
    }

    @Test
    void isLastTrueStopsLoopEvenWhenTotalIsLarger(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":100,\"isLast\":true,"
                        + "\"values\":[{\"id\":\"1\",\"key\":\"A\",\"name\":\"Alpha\"}]}")));

        List<JiraProjectSummary> projects = adapter(wm).listProjects(context(wm));

        assertThat(projects).containsExactly(new JiraProjectSummary("1", "A", "Alpha"));
    }

    @Test
    void versionsCallReturnsBareArrayMappedInOrder(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/project/10023/versions")).willReturn(okJson(
                "[{\"id\":\"1\",\"name\":\"2026.1\"},{\"id\":\"2\",\"name\":\"2026.2\"}]")));

        List<JiraVersion> versions = adapter(wm).listVersions(context(wm), "10023");

        assertThat(versions).containsExactly(new JiraVersion("1", "2026.1"), new JiraVersion("2", "2026.2"));
    }

    @Test
    void versionsCallReturningEmptyArrayYieldsEmptyList(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/project/10023/versions")).willReturn(okJson("[]")));

        List<JiraVersion> versions = adapter(wm).listVersions(context(wm), "10023");

        assertThat(versions).isEmpty();
    }

    @Test
    void mapsUnauthorizedToInvalidCredentials(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search")).willReturn(unauthorized()));

        assertThatThrownBy(() -> adapter(wm).listProjects(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }

    @Test
    void mapsForbiddenToJiraForbidden(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search")).willReturn(forbidden()));

        assertThatThrownBy(() -> adapter(wm).listProjects(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_FORBIDDEN));
    }

    @Test
    void mapsTooManyRequestsToRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search"))
                .willReturn(aResponse().withStatus(TOO_MANY_REQUESTS)));

        assertThatThrownBy(() -> adapter(wm).listProjects(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
    }

    @Test
    void mapsServerErrorToJiraError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter(wm).listProjects(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }

    @Test
    void mapsTimeoutToJiraUnreachable(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/api/3/project/search"))
                .willReturn(aResponse().withFixedDelay(3000)));

        assertThatThrownBy(() -> adapter(wm).listProjects(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_UNREACHABLE));
    }
}
