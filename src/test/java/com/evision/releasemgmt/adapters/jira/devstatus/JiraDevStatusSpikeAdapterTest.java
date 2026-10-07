package com.evision.releasemgmt.adapters.jira.devstatus;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
class JiraDevStatusSpikeAdapterTest {

    private static final String DETAIL_PATH = "/rest/dev-status/1.0/issue/detail";
    private static final String APPLICATION_TYPE = "AzureRepos";

    private JiraDevStatusSpikeAdapter adapter() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new JiraDevStatusSpikeAdapter(RestClient.builder().requestFactory(requestFactory).build());
    }

    private JiraAccessContext access(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "557058:abc-123", "rm@acme.com", "tok-123");
    }

    private String fixture(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static java.util.List<String> keys(JsonNode node) {
        return StreamSupport.stream(node.propertyNames().spliterator(), false).toList();
    }

    @Test
    void resolvesNumericIssueIdAndSendsBasicHeader(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/issue/EXAMPLE-1?fields=id")).willReturn(okJson(
                "{\"expand\":\"renderedFields\",\"id\":\"123456\",\"self\":\"x\",\"key\":\"EXAMPLE-1\"}")));

        String id = adapter().resolveNumericIssueId(access(wm), "EXAMPLE-1");

        assertThat(id).isEqualTo("123456");
        String expected = Base64.getEncoder().encodeToString("rm@acme.com:tok-123".getBytes(StandardCharsets.UTF_8));
        verify(getRequestedFor(urlEqualTo("/rest/api/3/issue/EXAMPLE-1?fields=id"))
                .withHeader("Authorization", equalTo("Basic " + expected)));
    }

    @Test
    void capturedBranchPayloadKeepsTheShapeObservedOnTheTenant(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(okJson(fixture("devstatus-cnv-2896-detail.json"))));

        JsonNode body = adapter().fetchDevStatusDetail(access(wm), "123456", APPLICATION_TYPE, "branch");

        assertThat(keys(body)).containsExactlyInAnyOrder("errors", "detail");
        JsonNode element = body.get("detail").get(0);
        assertThat(keys(element)).containsExactlyInAnyOrder("branches", "pullRequests", "repositories", "_instance");
        assertThat(element.get("branches")).hasSize(1);
        assertThat(element.get("pullRequests")).hasSize(3);
        assertThat(element.get("repositories")).isEmpty();
        assertThat(element.get("branches").get(0).get("lastCommit").has("message")).isTrue();
        assertThat(element.get("pullRequests").get(0).get("source").get("branch").asString()).isEqualTo("develop");
        assertThat(element.get("_instance").get("type").asString()).isEqualTo(APPLICATION_TYPE);
    }

    @Test
    void capturedRepositoryPayloadCarriesCommitsUnderRepositories(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(okJson(fixture("devstatus-cnv-2896-repository.json"))));

        JsonNode body = adapter().fetchDevStatusDetail(access(wm), "123456", APPLICATION_TYPE, "repository");

        JsonNode commit = body.get("detail").get(0).get("repositories").get(0).get("commits").get(0);
        assertThat(commit.has("id")).isTrue();
        assertThat(commit.has("message")).isTrue();
        assertThat(commit.get("files")).isEmpty();
    }

    @Test
    void sendsIssueIdApplicationTypeAndDataTypeAsQueryParameters(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(okJson(fixture("devstatus-cnv-2896-detail.json"))));

        adapter().fetchDevStatusDetail(access(wm), "123456", APPLICATION_TYPE, "branch");

        verify(getRequestedFor(urlPathEqualTo(DETAIL_PATH))
                .withQueryParam("issueId", equalTo("123456"))
                .withQueryParam("applicationType", equalTo(APPLICATION_TYPE))
                .withQueryParam("dataType", equalTo("branch")));
    }

    @Test
    void mapsUnauthorizedToInvalidCredentials(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(unauthorized()));

        assertThatThrownBy(() -> adapter().fetchDevStatusDetail(access(wm), "123456", APPLICATION_TYPE, "branch"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }

    @Test
    void mapsServerErrorToJiraError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(serverError()));

        assertThatThrownBy(() -> adapter().fetchDevStatusDetail(access(wm), "123456", APPLICATION_TYPE, "branch"))
                .isInstanceOf(JiraAuthenticationException.class)
                .satisfies(ex -> assertThat(((JiraAuthenticationException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }

    // Jira answers 200 with an empty detail for a wrong applicationType, so "no ADO link" must be an ordinary result.
    @Test
    void emptyDetailIsAnOrdinaryResultNotAnError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(okJson("{\"errors\":[],\"detail\":[]}")));

        JsonNode body = adapter().fetchDevStatusDetail(access(wm), "123456", "azure", "branch");

        assertThat(body.get("detail")).isEmpty();
    }
}
