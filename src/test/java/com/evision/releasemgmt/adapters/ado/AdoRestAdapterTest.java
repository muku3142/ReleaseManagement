package com.evision.releasemgmt.adapters.ado;

import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.domain.ado.AdoFailureReason;
import com.evision.releasemgmt.domain.enrichment.AdoRepoRef;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class AdoRestAdapterTest {

    private static final String PATH = "/example-org/_apis/git/repositories";
    private static final String HTML = "<html></html>";
    private static final String REPO_ID = "00000000-0000-0000-0000-000000000001";
    private static final String PR_PATH = "/example-org/example-project/_apis/git/repositories/" + REPO_ID
            + "/pullRequests/1002/commits";
    private static final String WEB_URL = "https://dev.azure.com/example-org/example-project/_git/example-repo";
    private static final AdoCredential CREDENTIAL =
            new AdoCredential("example-org", "pat-123", LocalDate.of(2030, 1, 1));
    private static final AdoRepoRef REPO = new AdoRepoRef("example-org", "example-project", WEB_URL);

    private AdoRestAdapter adapter(WireMockRuntimeInfo wm) {
        return adapter(wm, 5);
    }

    private AdoRestAdapter adapter(WireMockRuntimeInfo wm, int maxPages) {
        // No redirects, like production: HttpClient's default is Redirect.NEVER.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new AdoRestAdapter(RestClient.builder().requestFactory(requestFactory).build(), wm.getHttpBaseUrl(),
                maxPages);
    }

    private void assertReason(WireMockRuntimeInfo wm, AdoFailureReason reason) {
        assertThatThrownBy(() -> adapter(wm).validate("example-org", "pat-123"))
                .isInstanceOfSatisfying(AdoAccessException.class, e -> {
                    assertThat(e.reason()).isEqualTo(reason);
                    assertThat(e.getMessage()).doesNotContain("pat-123").doesNotContain("OnBhdC0xMjM=");
                });
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures", name));
    }

    private static List<LinkedCommit> commits(AdoRestAdapter adapter) {
        return adapter.pullRequestCommits(CREDENTIAL, REPO, REPO_ID, "1002");
    }

    private static void assertCommitsReason(AdoRestAdapter adapter, AdoFailureReason reason) {
        assertThatThrownBy(() -> commits(adapter)).isInstanceOfSatisfying(AdoAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void validTokenPassesAndSendsEmptyUsernameBasicHeader(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"count\":0,\"value\":[]}")));

        assertThatCode(() -> adapter(wm).validate("example-org", "pat-123")).doesNotThrowAnyException();

        verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("api-version", equalTo("7.1"))
                .withHeader("Authorization", equalTo("Basic OnBhdC0xMjM=")));
    }

    @Test
    void redirectToSignInIsUnauthorized(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(302)
                .withHeader("Location", "https://example.visualstudio.com/_signin")
                .withHeader("Content-Type", "text/html").withBody(HTML)));
        assertReason(wm, AdoFailureReason.UNAUTHORIZED);
    }

    @Test
    void nonAuthoritative203IsUnauthorized(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(203)
                .withHeader("Content-Type", "text/html").withBody(HTML)));
        assertReason(wm, AdoFailureReason.UNAUTHORIZED);
    }

    @Test
    void status401IsUnauthorized(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)));
        assertReason(wm, AdoFailureReason.UNAUTHORIZED);
    }

    @Test
    void status403IsUnauthorized(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403)));
        assertReason(wm, AdoFailureReason.UNAUTHORIZED);
    }

    @Test
    void status404IsNotFound(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));
        assertReason(wm, AdoFailureReason.NOT_FOUND);
    }

    @Test
    void status429IsRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(429)));
        assertReason(wm, AdoFailureReason.RATE_LIMITED);
    }

    @Test
    void status500IsError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
        assertReason(wm, AdoFailureReason.ERROR);
    }

    @Test
    void slowResponseIsUnreachable(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{}").withFixedDelay(2500)));
        assertReason(wm, AdoFailureReason.UNREACHABLE);
    }

    @Test
    void pullRequestCommitsFollowsContinuationTokenAndDeduplicates(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PR_PATH)).atPriority(2)
                .willReturn(okJson(fixture("ado-pr-commits-top3.json")).withHeader("x-ms-continuationtoken", "tok-2")));
        stubFor(get(urlPathEqualTo(PR_PATH)).atPriority(1).withQueryParam("continuationToken", equalTo("tok-2"))
                .willReturn(okJson(fixture("ado-pr-commits-all.json"))));

        List<LinkedCommit> result = commits(adapter(wm));

        assertThat(result).extracting(LinkedCommit::sha).doesNotHaveDuplicates().hasSizeGreaterThanOrEqualTo(3);
        verify(2, getRequestedFor(urlPathEqualTo(PR_PATH)));
        verify(getRequestedFor(urlPathEqualTo(PR_PATH)).withQueryParam("continuationToken", equalTo("tok-2"))
                .withQueryParam("$top", equalTo("100")).withQueryParam("api-version", equalTo("7.1"))
                .withHeader("Authorization", equalTo("Basic OnBhdC0xMjM=")));
    }

    @Test
    void commitsCarryBrowserLinkAndAuthorDate(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PR_PATH)).willReturn(okJson(fixture("ado-pr-commits-top3.json"))));

        List<LinkedCommit> result = commits(adapter(wm));

        assertThat(result).hasSize(3);
        LinkedCommit first = result.get(0);
        assertThat(first.url()).isEqualTo(WEB_URL + "/commit/" + first.sha()).doesNotContain("_apis");
        assertThat(first.authoredAt()).isEqualTo(Instant.parse("2026-07-12T09:35:11Z"));
    }

    @Test
    void morePagesThanTheCeilingIsTooLarge(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PR_PATH)).willReturn(okJson(fixture("ado-pr-commits-top3.json"))
                .withHeader("x-ms-continuationtoken", "again")));

        assertCommitsReason(adapter(wm, 2), AdoFailureReason.TOO_LARGE);
    }

    @Test
    void pullRequestCommitsMapsRedirectAndNotFound(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PR_PATH)).willReturn(aResponse().withStatus(302)
                .withHeader("Location", "https://example.visualstudio.com/_signin")));
        assertCommitsReason(adapter(wm), AdoFailureReason.UNAUTHORIZED);

        stubFor(get(urlPathEqualTo(PR_PATH)).willReturn(aResponse().withStatus(404)));
        assertCommitsReason(adapter(wm), AdoFailureReason.NOT_FOUND);
    }

    @Test
    void throttledOnceRetriesAfterRetryAfterAndSucceeds(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PR_PATH)).inScenario("throttle").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
                .willSetStateTo("open"));
        stubFor(get(urlPathEqualTo(PR_PATH)).inScenario("throttle").whenScenarioStateIs("open")
                .willReturn(okJson(fixture("ado-pr-commits-top3.json"))));

        long start = System.nanoTime();
        List<LinkedCommit> result = commits(adapter(wm));

        assertThat(result).hasSize(3);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
        verify(2, getRequestedFor(urlPathEqualTo(PR_PATH)));
    }

    @Test
    void throttledTwiceIsRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PR_PATH)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")));

        assertCommitsReason(adapter(wm), AdoFailureReason.RATE_LIMITED);
    }

    @Test
    void retryAfterOnASuccessfulResponseDelaysTheNextCall(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PR_PATH)).willReturn(okJson(fixture("ado-pr-commits-top3.json"))
                .withHeader("Retry-After", "1")));
        AdoRestAdapter adapter = adapter(wm);
        commits(adapter);

        long start = System.nanoTime();
        commits(adapter);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(800));
    }

    @Test
    void malformedIdentifiersFailBeforeAnyHttpCall(WireMockRuntimeInfo wm) {
        AdoRestAdapter adapter = adapter(wm);

        assertThatThrownBy(() -> adapter.pullRequestCommits(CREDENTIAL, REPO, "../x", "1002"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.pullRequestCommits(CREDENTIAL, REPO, REPO_ID, "12a"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(0, getRequestedFor(urlPathEqualTo(PR_PATH)));
    }
}
