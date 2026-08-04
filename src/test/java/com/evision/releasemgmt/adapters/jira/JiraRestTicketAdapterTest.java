package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraRestTicketAdapterTest {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final String SEARCH_URL = "/rest/api/3/search/jql";

    private JiraRestTicketAdapter adapter(int maxPages) {
        // Pinned to HTTP/1.1: the JDK client defaults to HTTP_2 and attempts an h2c cleartext
        // upgrade, which WireMock's Jetty answers by closing the connection on a POST with a
        // body — surfacing as EOFException -> JIRA_UNREACHABLE on every test. Production talks
        // to Jira Cloud over HTTPS, where HTTP/2 is negotiated by ALPN and never upgraded.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new JiraRestTicketAdapter(restClient, 100, maxPages);
    }

    private JiraAccessContext context(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "acct-1", "rm@acme.com", "tok-123");
    }

    private Map<TicketField, String> fieldIds() {
        Map<TicketField, String> ids = new LinkedHashMap<>();
        ids.put(TicketField.SUMMARY, "summary");
        ids.put(TicketField.ISSUE_TYPE, "issuetype");
        ids.put(TicketField.STATUS, "status");
        ids.put(TicketField.RESOLUTION, "resolution");
        ids.put(TicketField.ASSIGNEE, "assignee");
        ids.put(TicketField.REPORTER, "reporter");
        ids.put(TicketField.DESCRIPTION, "description");
        ids.put(TicketField.COMMENT, "comment");
        ids.put(TicketField.FIX_VERSIONS, "fixVersions");
        return ids;
    }

    private String issueJson(String key) {
        return "{\"key\":\"" + key + "\",\"fields\":{"
                + "\"summary\":\"Summary for " + key + "\","
                + "\"issuetype\":{\"name\":\"Bug\"},"
                + "\"status\":{\"name\":\"Open\"},"
                + "\"resolution\":null,"
                + "\"assignee\":null,"
                + "\"reporter\":{\"displayName\":\"Reporter Person\"},"
                + "\"fixVersions\":[],"
                + "\"description\":null,"
                + "\"comment\":{\"comments\":[],\"total\":0}"
                + "}}";
    }

    private String pageJson(String issueKey, String nextToken) {
        String tokenField = nextToken == null ? "" : ",\"nextPageToken\":\"" + nextToken + "\"";
        return "{\"issues\":[" + issueJson(issueKey) + "]" + tokenField + "}";
    }

    @Test
    void pagesToExhaustion(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).inScenario("paging")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(pageJson("CNV-1", "tok-page-2")))
                .willSetStateTo("page2"));
        stubFor(post(urlEqualTo(SEARCH_URL)).inScenario("paging")
                .whenScenarioStateIs("page2")
                .willReturn(okJson(pageJson("CNV-2", null))));

        List<TicketDetail> tickets = adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        assertThat(tickets).extracting(TicketDetail::key).containsExactly("CNV-1", "CNV-2");
        verify(2, postRequestedFor(urlEqualTo(SEARCH_URL)));
    }

    @Test
    void secondRequestCarriesTokenFromFirst(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).inScenario("paging2")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(pageJson("CNV-1", "tok-page-2")))
                .willSetStateTo("page2"));
        stubFor(post(urlEqualTo(SEARCH_URL)).inScenario("paging2")
                .whenScenarioStateIs("page2")
                .willReturn(okJson(pageJson("CNV-2", null))));

        adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        verify(postRequestedFor(urlEqualTo(SEARCH_URL))
                .withRequestBody(containing("\"nextPageToken\":\"tok-page-2\"")));
    }

    @Test
    void firstRequestOmitsNextPageTokenEntirely(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(pageJson("CNV-1", null))));

        adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        verify(postRequestedFor(urlEqualTo(SEARCH_URL))
                .withRequestBody(notMatching("(?s).*nextPageToken.*")));
    }

    @Test
    void isLastTrueWithLiveTokenDoesNotStopTheLoop(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).inScenario("isLastTrap")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson("{\"isLast\":true,\"nextPageToken\":\"tok-2\",\"issues\":["
                        + issueJson("CNV-1") + "]}"))
                .willSetStateTo("page2"));
        stubFor(post(urlEqualTo(SEARCH_URL)).inScenario("isLastTrap")
                .whenScenarioStateIs("page2")
                .willReturn(okJson(pageJson("CNV-2", null))));

        List<TicketDetail> tickets = adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        assertThat(tickets).extracting(TicketDetail::key).containsExactly("CNV-1", "CNV-2");
    }

    @Test
    void absentNextPageTokenTerminates(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(pageJson("CNV-1", null))));

        List<TicketDetail> tickets = adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        assertThat(tickets).hasSize(1);
        verify(1, postRequestedFor(urlEqualTo(SEARCH_URL)));
    }

    @Test
    void blankNextPageTokenTerminates(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(
                "{\"issues\":[" + issueJson("CNV-1") + "],\"nextPageToken\":\"\"}")));

        List<TicketDetail> tickets = adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        assertThat(tickets).hasSize(1);
        verify(1, postRequestedFor(urlEqualTo(SEARCH_URL)));
    }

    @Test
    void safetyCeilingThrowsResultSetTooLarge(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(pageJson("CNV-1", "tok-always"))));

        assertThatThrownBy(() -> adapter(3).searchAll(context(wm), "project = CNV", fieldIds()))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.RESULT_SET_TOO_LARGE));
        verify(3, postRequestedFor(urlEqualTo(SEARCH_URL)));
    }

    @Test
    void mapsAllRequestedFields(WireMockRuntimeInfo wm) {
        String issue = "{\"key\":\"CNV-100\",\"fields\":{"
                + "\"summary\":\"Ship the release\","
                + "\"issuetype\":{\"name\":\"Story\"},"
                + "\"status\":{\"name\":\"In Progress\"},"
                + "\"resolution\":{\"name\":\"Done\"},"
                + "\"assignee\":{\"displayName\":\"Jane Doe\"},"
                + "\"reporter\":{\"displayName\":\"John Roe\"},"
                + "\"fixVersions\":[{\"name\":\"1.2.0\"}],"
                + "\"description\":null,"
                + "\"comment\":{\"comments\":[],\"total\":0}"
                + "}}";
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson("{\"issues\":[" + issue + "]}")));

        TicketDetail ticket = adapter(50).searchAll(context(wm), "project = CNV", fieldIds()).get(0);

        assertThat(ticket.key()).isEqualTo("CNV-100");
        assertThat(ticket.summary()).isEqualTo("Ship the release");
        assertThat(ticket.issueType()).isEqualTo("Story");
        assertThat(ticket.status()).isEqualTo("In Progress");
        assertThat(ticket.resolution()).isEqualTo("Done");
        assertThat(ticket.assignee()).isEqualTo("Jane Doe");
        assertThat(ticket.reporter()).isEqualTo("John Roe");
        assertThat(ticket.fixVersions()).containsExactly("1.2.0");
    }

    @Test
    void unassignedAndUnresolvedAreNullNotAnException(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(pageJson("CNV-1", null))));

        TicketDetail ticket = adapter(50).searchAll(context(wm), "project = CNV", fieldIds()).get(0);

        assertThat(ticket.assignee()).isNull();
        assertThat(ticket.resolution()).isNull();
    }

    @Test
    void adfDescriptionIsFlattened(WireMockRuntimeInfo wm) {
        String issue = "{\"key\":\"CNV-1\",\"fields\":{"
                + "\"summary\":\"S\","
                + "\"issuetype\":{\"name\":\"Bug\"},"
                + "\"status\":{\"name\":\"Open\"},"
                + "\"resolution\":null,"
                + "\"assignee\":null,"
                + "\"reporter\":null,"
                + "\"fixVersions\":[],"
                + "\"description\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"Ship it\",\"marks\":[{\"type\":\"strong\"}]}]}]},"
                + "\"comment\":{\"comments\":[],\"total\":0}"
                + "}}";
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson("{\"issues\":[" + issue + "]}")));

        TicketDetail ticket = adapter(50).searchAll(context(wm), "project = CNV", fieldIds()).get(0);

        assertThat(ticket.description()).contains("**Ship it**");
        assertThat(ticket.description()).doesNotContain("\"type\"");
    }

    @Test
    void commentsAreFlattenedWithTotal(WireMockRuntimeInfo wm) {
        String adfComment = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"Looks good\"}]}]}";
        String issue = "{\"key\":\"CNV-1\",\"fields\":{"
                + "\"summary\":\"S\","
                + "\"issuetype\":{\"name\":\"Bug\"},"
                + "\"status\":{\"name\":\"Open\"},"
                + "\"resolution\":null,"
                + "\"assignee\":null,"
                + "\"reporter\":null,"
                + "\"fixVersions\":[],"
                + "\"description\":null,"
                + "\"comment\":{\"comments\":[{\"body\":" + adfComment + "},{\"body\":" + adfComment + "}],\"total\":7}"
                + "}}";
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson("{\"issues\":[" + issue + "]}")));

        TicketDetail ticket = adapter(50).searchAll(context(wm), "project = CNV", fieldIds()).get(0);

        assertThat(ticket.comments()).hasSize(2);
        assertThat(ticket.comments().get(0)).contains("Looks good");
        assertThat(ticket.commentTotal()).isEqualTo(7);
    }

    @Test
    void sendsBasicAuthHeader(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(pageJson("CNV-1", null))));

        adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        String expectedBase64 = Base64.getEncoder().encodeToString("rm@acme.com:tok-123".getBytes(StandardCharsets.UTF_8));
        verify(postRequestedFor(urlEqualTo(SEARCH_URL))
                .withHeader("Authorization", equalTo("Basic " + expectedBase64)));
    }

    @Test
    void requestedFieldsAreTheResolvedIds(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(pageJson("CNV-1", null))));

        adapter(50).searchAll(context(wm), "project = CNV", fieldIds());

        verify(postRequestedFor(urlEqualTo(SEARCH_URL))
                .withRequestBody(containing("\"fixVersions\""))
                .withRequestBody(containing("\"issuetype\"")));
    }

    @Test
    void mapsUnauthorizedToInvalidCredentials(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(unauthorized()));

        assertThatThrownBy(() -> adapter(50).searchAll(context(wm), "project = CNV", fieldIds()))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.INVALID_CREDENTIALS));
    }

    @Test
    void mapsForbiddenToJiraForbidden(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(forbidden()));

        assertThatThrownBy(() -> adapter(50).searchAll(context(wm), "project = CNV", fieldIds()))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_FORBIDDEN));
    }

    @Test
    void mapsTooManyRequestsToRateLimitedWithNoRetry(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(aResponse().withStatus(TOO_MANY_REQUESTS)));

        assertThatThrownBy(() -> adapter(50).searchAll(context(wm), "project = CNV", fieldIds()))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_RATE_LIMITED));
        verify(1, postRequestedFor(urlEqualTo(SEARCH_URL)));
    }

    @Test
    void mapsServerErrorToJiraError(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(serverError()));

        assertThatThrownBy(() -> adapter(50).searchAll(context(wm), "project = CNV", fieldIds()))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_ERROR));
    }

    @Test
    void mapsTimeoutToJiraUnreachable(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(aResponse().withFixedDelay(3000)));

        assertThatThrownBy(() -> adapter(50).searchAll(context(wm), "project = CNV", fieldIds()))
                .isInstanceOf(JiraTicketReadException.class)
                .satisfies(ex -> assertThat(((JiraTicketReadException) ex).reason())
                        .isEqualTo(JiraAuthFailureReason.JIRA_UNREACHABLE));
    }
}
