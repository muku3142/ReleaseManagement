package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.JiraSprint;
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
    void sprintsAreReadPerScrumBoardAndReturnedNewestFirst(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,"
                        + "\"values\":[{\"id\":7,\"name\":\"CNV board\",\"type\":\"scrum\"}]}")));
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/7/sprint")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":2,\"isLast\":true,\"values\":["
                        + "{\"id\":31,\"name\":\"Sprint 1\",\"state\":\"closed\",\"startDate\":\"2026-01-05T09:00:00.000Z\"},"
                        + "{\"id\":32,\"name\":\"Sprint 2\",\"state\":\"active\",\"startDate\":\"2026-02-02T09:00:00.000Z\"}]}")));

        List<JiraSprint> sprints = adapter(wm).listSprints(context(wm), "10023");

        assertThat(sprints).containsExactly(
                new JiraSprint("32", "Sprint 2", "active"),
                new JiraSprint("31", "Sprint 1", "closed"));
        // Only active and closed are asked for: a future sprint has nothing shippable in it.
        verify(getRequestedFor(urlPathEqualTo("/rest/agile/1.0/board/7/sprint"))
                .withQueryParam("state", equalTo("active,closed")));
    }

    @Test
    void kanbanBoardsAreNeverAskedForSprints(WireMockRuntimeInfo wm) {
        // The Agile API rejects the sprint endpoint on a kanban board, so it must not be called.
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,"
                        + "\"values\":[{\"id\":9,\"name\":\"Support board\",\"type\":\"kanban\"}]}")));

        List<JiraSprint> sprints = adapter(wm).listSprints(context(wm), "10023");

        assertThat(sprints).isEmpty();
        verify(0, getRequestedFor(urlPathEqualTo("/rest/agile/1.0/board/9/sprint")));
    }

    @Test
    void teamManagedSimpleBoardsAreAskedForSprints(WireMockRuntimeInfo wm) {
        // Team-managed (next-gen) projects report their board as type "simple", not "scrum".
        // Filtering to "scrum" alone hid the sprints of every such project.
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,"
                        + "\"values\":[{\"id\":12,\"name\":\"CNV board\",\"type\":\"simple\"}]}")));
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/12/sprint")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,\"values\":["
                        + "{\"id\":44,\"name\":\"CNV Sprint 9\",\"state\":\"active\","
                        + "\"startDate\":\"2026-07-20T09:00:00.000Z\"}]}")));

        List<JiraSprint> sprints = adapter(wm).listSprints(context(wm), "11688");

        assertThat(sprints).containsExactly(new JiraSprint("44", "CNV Sprint 9", "active"));
    }

    @Test
    void aBoardThatDoesNotSupportSprintsIsSkippedRatherThanFailingThePicker(WireMockRuntimeInfo wm) {
        // A "simple" board may be Kanban-style, which answers 400. One such board must not take
        // the whole picker down alongside a sibling board that does have sprints.
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":2,\"isLast\":true,"
                        + "\"values\":[{\"id\":12,\"name\":\"Kanban-style\",\"type\":\"simple\"},"
                        + "{\"id\":13,\"name\":\"Scrum-style\",\"type\":\"simple\"}]}")));
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/12/sprint"))
                .willReturn(aResponse().withStatus(400)));
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/13/sprint")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,\"values\":["
                        + "{\"id\":44,\"name\":\"Sprint 9\",\"state\":\"active\","
                        + "\"startDate\":\"2026-07-20T09:00:00.000Z\"}]}")));

        List<JiraSprint> sprints = adapter(wm).listSprints(context(wm), "11688");

        assertThat(sprints).containsExactly(new JiraSprint("44", "Sprint 9", "active"));
    }

    @Test
    void theSameSprintReachableFromTwoBoardsIsReturnedOnce(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":2,\"isLast\":true,"
                        + "\"values\":[{\"id\":7,\"name\":\"A\",\"type\":\"scrum\"},"
                        + "{\"id\":8,\"name\":\"B\",\"type\":\"scrum\"}]}")));
        String samePage = "{\"startAt\":0,\"maxResults\":50,\"total\":1,\"isLast\":true,\"values\":["
                + "{\"id\":31,\"name\":\"Sprint 1\",\"state\":\"closed\",\"startDate\":\"2026-01-05T09:00:00.000Z\"}]}";
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/7/sprint")).willReturn(okJson(samePage)));
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/8/sprint")).willReturn(okJson(samePage)));

        List<JiraSprint> sprints = adapter(wm).listSprints(context(wm), "10023");

        assertThat(sprints).containsExactly(new JiraSprint("31", "Sprint 1", "closed"));
    }

    @Test
    void projectWithNoBoardsYieldsNoSprints(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/rest/agile/1.0/board")).willReturn(okJson(
                "{\"startAt\":0,\"maxResults\":50,\"total\":0,\"isLast\":true,\"values\":[]}")));

        assertThat(adapter(wm).listSprints(context(wm), "10023")).isEmpty();
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
