package com.evision.releasemgmt.adapters.jira.devstatus;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.enrichment.DevStatusLinks;
import com.evision.releasemgmt.domain.enrichment.DevStatusUnrecognisedException;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
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
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraDevStatusAdapterTest {

    private static final String SUMMARY_PATH = "/rest/dev-status/1.0/issue/summary";
    private static final String DETAIL_PATH = "/rest/dev-status/1.0/issue/detail";
    private static final String ISSUE_ID = "10001";
    private static final String KEY = "EXAMPLE-1";

    private JiraDevStatusAdapter adapter() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new JiraDevStatusAdapter(RestClient.builder().requestFactory(requestFactory).build());
    }

    private JiraAccessContext access(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "557058:abc-123", "rm@acme.com", "tok-123");
    }

    private String fixture(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void stubFixtures() throws IOException {
        stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(okJson(fixture("devstatus-cnv-2896-summary.json"))));
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).withQueryParam("dataType", equalTo("pullrequest"))
                .willReturn(okJson(fixture("devstatus-cnv-2896-detail.json"))));
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).withQueryParam("dataType", equalTo("repository"))
                .willReturn(okJson(fixture("devstatus-cnv-2896-repository.json"))));
    }

    private void stubSummaryAdvertisingAzureRepos() throws IOException {
        stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(okJson(fixture("devstatus-cnv-2896-summary.json"))));
    }

    @Test
    void turnsTheCapturedFixturesIntoDomainLinks(WireMockRuntimeInfo wm) throws IOException {
        stubFixtures();

        DevStatusLinks links = adapter().fetchLinks(access(wm), ISSUE_ID, KEY);

        assertThat(links.pullRequests()).extracting(LinkedPullRequest::id).containsExactly("1001", "1002", "1003");
        assertThat(links.pullRequests()).extracting(LinkedPullRequest::countsTowardCommits)
                .containsExactly(false, true, true);
        assertThat(links.keyTaggedCommits()).hasSize(1);
        assertThat(links.keyTaggedCommits().get(0).authoredAt()).isEqualTo(Instant.parse("2026-07-26T10:26:25Z"));
        assertThat(links.keyTaggedCommits().get(0).message()).isEqualTo("EXAMPLE-1: example commit message.");
        assertThat(links.branches()).containsExactly("feature/EXAMPLE-1");
        assertThat(links.hasDisplayableLinks()).isTrue();
    }

    @Test
    void sendsBasicHeaderAndQueryParameters(WireMockRuntimeInfo wm) throws IOException {
        stubFixtures();

        adapter().fetchLinks(access(wm), ISSUE_ID, KEY);

        String expected = Base64.getEncoder().encodeToString("rm@acme.com:tok-123".getBytes(StandardCharsets.UTF_8));
        verify(getRequestedFor(urlPathEqualTo(SUMMARY_PATH)).withQueryParam("issueId", equalTo(ISSUE_ID))
                .withHeader("Authorization", equalTo("Basic " + expected)));
        for (String dataType : new String[]{"pullrequest", "repository"}) {
            verify(getRequestedFor(urlPathEqualTo(DETAIL_PATH))
                    .withQueryParam("issueId", equalTo(ISSUE_ID))
                    .withQueryParam("applicationType", equalTo("AzureRepos"))
                    .withQueryParam("dataType", equalTo(dataType)));
        }
    }

    @Test
    void summaryWithoutAzureReposReturnsNoneAfterOneCall(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(okJson(
                "{\"errors\":[],\"summary\":{\"pullrequest\":{\"byInstanceType\":{}},"
                        + "\"repository\":{\"byInstanceType\":{\"GitHub\":{\"count\":1}}},"
                        + "\"branch\":{\"byInstanceType\":{}}}}")));

        DevStatusLinks links = adapter().fetchLinks(access(wm), ISSUE_ID, KEY);

        assertThat(links.hasDisplayableLinks()).isFalse();
        assertThat(links.branches()).isEmpty();
        verify(1, getRequestedFor(urlPathEqualTo(SUMMARY_PATH)));
        verify(0, getRequestedFor(urlPathEqualTo(DETAIL_PATH)));
    }

    @Test
    void emptyDetailDespiteAdvertisedAzureReposIsUnrecognised(WireMockRuntimeInfo wm) throws IOException {
        stubSummaryAdvertisingAzureRepos();
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(okJson("{\"errors\":[],\"detail\":[]}")));

        assertThatThrownBy(() -> adapter().fetchLinks(access(wm), ISSUE_ID, KEY))
                .isInstanceOf(DevStatusUnrecognisedException.class);
    }

    @Test
    void detailOfAnotherInstanceTypeIsUnrecognised(WireMockRuntimeInfo wm) throws IOException {
        stubSummaryAdvertisingAzureRepos();
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(okJson(
                fixture("devstatus-cnv-2896-detail.json").replace("\"type\": \"AzureRepos\"", "\"type\": \"github\""))));

        assertThatThrownBy(() -> adapter().fetchLinks(access(wm), ISSUE_ID, KEY))
                .isInstanceOf(DevStatusUnrecognisedException.class);
    }

    @Test
    void dropsPullRequestsAndCommitsWithUntrustedUrls(WireMockRuntimeInfo wm) throws IOException {
        stubSummaryAdvertisingAzureRepos();
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).withQueryParam("dataType", equalTo("pullrequest"))
                .willReturn(okJson(fixture("devstatus-cnv-2896-detail.json")
                        .replace("https://dev.azure.com/example-org/example-project/_git/example-repo/pullrequest/1002",
                                "https://evil.example.com/pullrequest/1002"))));
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).withQueryParam("dataType", equalTo("repository"))
                .willReturn(okJson(fixture("devstatus-cnv-2896-repository.json")
                        .replace("https://dev.azure.com/example-org/example-project/_git/example-repo/commit/",
                                "https://evil.example.com/commit/"))));

        DevStatusLinks links = adapter().fetchLinks(access(wm), ISSUE_ID, KEY);

        assertThat(links.pullRequests()).extracting(LinkedPullRequest::id).containsExactly("1001", "1003");
        assertThat(links.keyTaggedCommits()).isEmpty();
    }

    @Test
    void mapsUnauthorizedToInvalidCredentials(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(unauthorized()));

        assertThatThrownBy(() -> adapter().fetchLinks(access(wm), ISSUE_ID, KEY))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }

    @Test
    void mapsTooManyRequestsToRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter().fetchLinks(access(wm), ISSUE_ID, KEY))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
    }

    @Test
    void mapsServerErrorToJiraError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(serverError()));

        assertThatThrownBy(() -> adapter().fetchLinks(access(wm), ISSUE_ID, KEY))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }
}
