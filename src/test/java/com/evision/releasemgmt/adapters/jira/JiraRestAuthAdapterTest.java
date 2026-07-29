package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

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
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraRestAuthAdapterTest {

    private static final int TOO_MANY_REQUESTS = 429;

    private JiraRestAuthAdapter adapter(WireMockRuntimeInfo wm) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new JiraRestAuthAdapter(restClient);
    }

    @Test
    void returnsIdentityOn200AndSendsExpectedBasicHeader(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(okJson(
                "{\"accountId\":\"557058:abc-123\",\"emailAddress\":\"rm@acme.com\",\"displayName\":\"Release Manager\",\"active\":true}")));

        JiraIdentity identity = adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123");

        assertThat(identity).isEqualTo(new JiraIdentity("557058:abc-123", "rm@acme.com", "Release Manager"));
        String expectedBase64 = Base64.getEncoder().encodeToString("rm@acme.com:tok-123".getBytes(StandardCharsets.UTF_8));
        verify(getRequestedFor(urlEqualTo("/rest/api/3/myself"))
                .withHeader("Authorization", equalTo("Basic " + expectedBase64)));
    }

    @Test
    void toleratesAbsentEmailAddress(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(okJson(
                "{\"accountId\":\"557058:abc-123\",\"displayName\":\"Release Manager\"}")));

        JiraIdentity identity = adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123");

        assertThat(identity.accountId()).isEqualTo("557058:abc-123");
        assertThat(identity.email()).isNull();
    }

    @Test
    void ignoresUnknownFields(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(okJson(
                "{\"accountId\":\"x\",\"somethingNew\":true}")));

        JiraIdentity identity = adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123");

        assertThat(identity.accountId()).isEqualTo("x");
    }

    @Test
    void mapsUnauthorizedToInvalidCredentials(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(unauthorized()));

        assertThatThrownBy(() -> adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }

    @Test
    void mapsForbiddenToJiraForbidden(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(forbidden()));

        assertThatThrownBy(() -> adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_FORBIDDEN));
    }

    @Test
    void mapsTooManyRequestsToRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(aResponse().withStatus(TOO_MANY_REQUESTS)));

        assertThatThrownBy(() -> adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
    }

    @Test
    void mapsServerErrorToJiraError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }

    @Test
    void mapsTimeoutToJiraUnreachable(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(aResponse().withFixedDelay(3000)));

        assertThatThrownBy(() -> adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_UNREACHABLE));
    }

    @Test
    void mapsMissingAccountIdToJiraError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/myself")).willReturn(okJson("{}")));

        assertThatThrownBy(() -> adapter(wm).validate(wm.getHttpBaseUrl(), "rm@acme.com", "tok-123"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }
}
