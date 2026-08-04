package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraField;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
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
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraFieldMetadataAdapterTest {

    private static final int TOO_MANY_REQUESTS = 429;

    private JiraFieldMetadataAdapter adapter() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new JiraFieldMetadataAdapter(restClient);
    }

    private JiraAccessContext context(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "acct-1", "rm@acme.com", "tok-123");
    }

    @Test
    void mapsRealisticArrayIgnoringUnknownProperties(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(okJson(
                "[{\"id\":\"summary\",\"key\":\"summary\",\"name\":\"Summary\",\"custom\":false},"
                        + "{\"id\":\"customfield_10011\",\"name\":\"Epic Name\",\"custom\":true}]")));

        List<JiraField> fields = adapter().fetchAllFields(context(wm));

        assertThat(fields).containsExactly(
                new JiraField("summary", "Summary", false),
                new JiraField("customfield_10011", "Epic Name", true));
    }

    @Test
    void sendsBasicAuthHeader(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(okJson("[]")));

        adapter().fetchAllFields(context(wm));

        String expectedBase64 = Base64.getEncoder().encodeToString("rm@acme.com:tok-123".getBytes(StandardCharsets.UTF_8));
        verify(getRequestedFor(urlEqualTo("/rest/api/3/field"))
                .withHeader("Authorization", equalTo("Basic " + expectedBase64)));
    }

    @Test
    void emptyArrayYieldsEmptyList(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(okJson("[]")));

        List<JiraField> fields = adapter().fetchAllFields(context(wm));

        assertThat(fields).isEmpty();
    }

    @Test
    void mapsUnauthorizedToInvalidCredentials(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(unauthorized()));

        assertThatThrownBy(() -> adapter().fetchAllFields(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }

    @Test
    void mapsForbiddenToJiraForbidden(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(forbidden()));

        assertThatThrownBy(() -> adapter().fetchAllFields(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_FORBIDDEN));
    }

    @Test
    void mapsTooManyRequestsToRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(aResponse().withStatus(TOO_MANY_REQUESTS)));

        assertThatThrownBy(() -> adapter().fetchAllFields(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
    }

    @Test
    void mapsServerErrorToJiraError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter().fetchAllFields(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }

    @Test
    void mapsTimeoutToJiraUnreachable(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(aResponse().withFixedDelay(3000)));

        assertThatThrownBy(() -> adapter().fetchAllFields(context(wm)))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_UNREACHABLE));
    }
}
