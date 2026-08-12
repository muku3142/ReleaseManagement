package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.jira.JiraFieldMetadataAdapter;
import com.evision.releasemgmt.adapters.jira.JiraRestTicketAdapter;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Wires the real field-metadata resolution, JQL composition and search adapter together against
 * a stubbed Jira carrying the eVision tenant's actual field configuration. The unit tests prove
 * each piece; this proves the composition, which is where the 502 the user hit actually lived.
 */
@WireMockTest
@ExtendWith(MockitoExtension.class)
class TicketSearchPathTest {

    @Mock
    private JiraAccessContextService accessContextService;

    @Mock
    private ReleaseRepository releaseRepository;

    // The tenant's real shape: 'Resolution' is carried by the system field plus two custom
    // fields, which is what made every search fail with JIRA_ERROR.
    private static final String TENANT_FIELDS = """
            [
              {"id":"summary","name":"Summary","custom":false},
              {"id":"issuetype","name":"Issue Type","custom":false},
              {"id":"status","name":"Status","custom":false},
              {"id":"resolution","name":"Resolution","custom":false},
              {"id":"customfield_11864","name":"Resolution","custom":true},
              {"id":"customfield_11863","name":"Resolution","custom":true},
              {"id":"assignee","name":"Assignee","custom":false},
              {"id":"reporter","name":"Reporter","custom":false},
              {"id":"description","name":"Description","custom":false},
              {"id":"comment","name":"Comment","custom":false},
              {"id":"fixVersions","name":"Fix versions","custom":false},
              {"id":"components","name":"Components","custom":false}
            ]
            """;

    private TicketSearchService service(WireMockRuntimeInfo wm) {
        // HTTP/1.1 pinned: the JDK default HTTP_2 attempts an h2c upgrade that WireMock's Jetty
        // answers by closing a POST with a body. Production reaches Jira over HTTPS via ALPN.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();

        when(accessContextService.currentContext()).thenReturn(
                new JiraAccessContext(wm.getHttpBaseUrl(), "acct-1", "rm@acme.com", "tok-123"));

        return new TicketSearchService(
                accessContextService,
                new FieldMetadataService(accessContextService, new JiraFieldMetadataAdapter(restClient)),
                new JiraRestTicketAdapter(restClient, 100, 50),
                releaseRepository);
    }

    @Test
    void searchSucceedsOnATenantWhoseResolutionNameIsShadowedByCustomFields(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(okJson(TENANT_FIELDS)));
        stubFor(post(urlEqualTo("/rest/api/3/search/jql")).willReturn(okJson("""
                {"issues":[{"key":"CNV-2896","fields":{
                  "summary":"Ship the thing",
                  "issuetype":{"name":"Story"},
                  "status":{"name":"Done"},
                  "resolution":{"name":"Fixed"},
                  "assignee":{"displayName":"Dev Person"},
                  "reporter":{"displayName":"Release Manager"},
                  "fixVersions":[{"name":"2026.1"}],
                  "description":null,
                  "comment":{"comments":[],"total":0}
                }}]}
                """)));

        List<TicketDetail> tickets = service(wm).search(
                TicketSearchCriteria.byProject("10023", null, EnumSet.of(StatusCategory.DONE)));

        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).key()).isEqualTo("CNV-2896");
        assertThat(tickets.get(0).resolution()).isEqualTo("Fixed");
        assertThat(tickets.get(0).status()).isEqualTo("Done");

        // The system field id is what got requested, not either shadowing custom field.
        verify(postRequestedFor(urlEqualTo("/rest/api/3/search/jql"))
                .withRequestBody(containing("\"resolution\"")));
        verify(postRequestedFor(urlEqualTo("/rest/api/3/search/jql"))
                .withRequestBody(containing("project = 10023 AND statusCategory IN (\\\"Done\\\") ORDER BY key ASC")));
    }

    @Test
    void selectingASprintAddsItsClauseAndStillResolvesFields(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(okJson(TENANT_FIELDS)));
        stubFor(post(urlEqualTo("/rest/api/3/search/jql")).willReturn(okJson("{\"issues\":[]}")));

        List<TicketDetail> tickets = service(wm).search(
                TicketSearchCriteria.byProject("10023", "10891", EnumSet.of(StatusCategory.DONE)));

        // An empty result is a success, not a failure — the JIRA-06 distinction.
        assertThat(tickets).isEmpty();
        // Asserting the whole composed query pins clause order and proves no clause leaked in:
        // "fixVersions" also appears in this body as a requested field id, so a bare
        // "does it mention fixVersion" check would be meaningless here.
        verify(postRequestedFor(urlEqualTo("/rest/api/3/search/jql")).withRequestBody(containing(
                "project = 10023 AND sprint = 10891 AND statusCategory IN (\\\"Done\\\") ORDER BY key ASC")));
    }

    @Test
    void releaseScopedSearchHidesMembersWithoutChangingTheQuerySentToJira(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/rest/api/3/field")).willReturn(okJson(TENANT_FIELDS)));
        stubFor(post(urlEqualTo("/rest/api/3/search/jql")).willReturn(okJson("""
                {"issues":[{"key":"CNV-2896","fields":{
                  "summary":"Ship the thing",
                  "issuetype":{"name":"Story"},
                  "status":{"name":"Done"},
                  "resolution":{"name":"Fixed"},
                  "assignee":{"displayName":"Dev Person"},
                  "reporter":{"displayName":"Release Manager"},
                  "fixVersions":[{"name":"2026.1"}],
                  "description":null,
                  "comment":{"comments":[],"total":0}
                }},{"key":"CNV-2897","fields":{
                  "summary":"Ship the other thing",
                  "issuetype":{"name":"Story"},
                  "status":{"name":"Done"},
                  "resolution":{"name":"Fixed"},
                  "assignee":{"displayName":"Dev Person"},
                  "reporter":{"displayName":"Release Manager"},
                  "fixVersions":[{"name":"2026.1"}],
                  "description":null,
                  "comment":{"comments":[],"total":0}
                }}]}
                """)));
        when(releaseRepository.findTicketKeys(any())).thenReturn(List.of("CNV-2896"));

        List<TicketDetail> tickets = service(wm).search(
                TicketSearchCriteria.byProject("10023", null, EnumSet.of(StatusCategory.DONE)),
                UUID.randomUUID());

        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).key()).isEqualTo("CNV-2897");

        verify(postRequestedFor(urlEqualTo("/rest/api/3/search/jql")).withRequestBody(containing(
                "project = 10023 AND statusCategory IN (\\\"Done\\\") ORDER BY key ASC")));
    }
}
