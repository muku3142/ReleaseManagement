package com.evision.releasemgmt.adapters.ado;

import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoFailureReason;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

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

    private AdoRestAdapter adapter(WireMockRuntimeInfo wm) {
        // No redirects, like production: HttpClient's default is Redirect.NEVER.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new AdoRestAdapter(RestClient.builder().requestFactory(requestFactory).build(), wm.getHttpBaseUrl());
    }

    private void assertReason(WireMockRuntimeInfo wm, AdoFailureReason reason) {
        assertThatThrownBy(() -> adapter(wm).validate("example-org", "pat-123"))
                .isInstanceOfSatisfying(AdoAccessException.class, e -> {
                    assertThat(e.reason()).isEqualTo(reason);
                    assertThat(e.getMessage()).doesNotContain("pat-123").doesNotContain("OnBhdC0xMjM=");
                });
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
}
