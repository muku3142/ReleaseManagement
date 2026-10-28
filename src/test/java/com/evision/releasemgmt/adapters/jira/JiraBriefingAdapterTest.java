package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.briefing.BriefingQueries;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraBriefingAdapterTest {

    private static final String SEARCH_URL = "/rest/api/3/search/jql";
    private static final String JQL = "project = CNV";
    private static final BriefingFieldIds ALL_FIELDS =
            new BriefingFieldIds("customfield_10016", "customfield_10021", "customfield_10020", "customfield_10030");

    private final JiraBriefingAdapter adapter = adapter();

    private static JiraBriefingAdapter adapter() {
        // HTTP/1.1 pinned for the same WireMock h2c reason as JiraRestTicketAdapterTest.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        return new JiraBriefingAdapter(RestClient.builder().requestFactory(requestFactory).build(), 100, 10);
    }

    private static JiraAccessContext context(WireMockRuntimeInfo wm) {
        return new JiraAccessContext(wm.getHttpBaseUrl(), "acct-1", "rm@acme.com", "tok-123");
    }

    private static String fixture(String name) throws IOException {
        return new String(JiraBriefingAdapterTest.class.getResourceAsStream("/fixtures/" + name).readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private void stubTwoPages() throws IOException {
        stubFor(post(urlEqualTo(SEARCH_URL)).withRequestBody(notMatching("(?s).*nextPageToken.*"))
                .willReturn(okJson(fixture("jira-briefing-search-page1.json"))));
        stubFor(post(urlEqualTo(SEARCH_URL)).withRequestBody(containing("\"nextPageToken\":\"token-page-2\""))
                .willReturn(okJson(fixture("jira-briefing-search-page2.json"))));
    }

    @Test
    void mapsEveryBriefingFieldAcrossTwoPages(WireMockRuntimeInfo wm) throws IOException {
        stubTwoPages();

        List<BriefingIssue> issues = adapter.search(context(wm), JQL, ALL_FIELDS, BriefingQueries.UNCAPPED);

        assertThat(issues).extracting(BriefingIssue::key)
                .containsExactly("CNV-1", "CNV-2", "CNV-3", "CNV-4", "CNV-5", "CNV-6");
        BriefingIssue first = issues.get(0);
        assertThat(first.summary()).isEqualTo("Export the quarterly report");
        assertThat(first.issueType()).isEqualTo("Story");
        assertThat(first.status()).isEqualTo("In Progress");
        assertThat(first.statusCategory()).isEqualTo(StatusCategory.IN_PROGRESS);
        assertThat(first.priority()).isEqualTo("Highest");
        assertThat(first.assignee()).isEqualTo("Sample Person");
        assertThat(first.created()).isNotNull();
        assertThat(first.updated()).isNotNull();
        assertThat(first.resolved()).isNull();
        assertThat(first.dueDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(first.parentKey()).isEqualTo("CNV-100");
        assertThat(first.subtaskCount()).isEqualTo(2);
        assertThat(first.storyPoints()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(first.flagged()).isTrue();
        assertThat(first.description()).contains("Users export the report as CSV.");
        assertThat(first.acceptanceCriteriaPresent()).isTrue();
        assertThat(first.sprints()).singleElement().satisfies(sprint -> {
            assertThat(sprint.id()).isEqualTo(77);
            assertThat(sprint.name()).isEqualTo("Sprint 41");
            assertThat(sprint.state()).isEqualTo("active");
            assertThat(sprint.startDate()).isNotNull();
            assertThat(sprint.endDate()).isNotNull();
        });

        BriefingIssue open = issues.get(1);
        assertThat(open.statusCategory()).isEqualTo(StatusCategory.TO_DO);
        assertThat(open.flagged()).isFalse();
        assertThat(open.storyPoints()).isNull();
        assertThat(open.assignee()).isNull();
        assertThat(open.sprints()).isEmpty();
        assertThat(open.acceptanceCriteriaPresent()).isFalse();

        BriefingIssue done = issues.get(2);
        assertThat(done.statusCategory()).isEqualTo(StatusCategory.DONE);
        assertThat(done.resolved()).isNotNull();
        assertThat(done.description()).isEqualTo("plain text, not a document");
        assertThat(open.description()).isEmpty();
        assertThat(done.acceptanceCriteriaPresent()).isTrue();
    }

    @Test
    void malformedOptionalFieldsDegradeInsteadOfFailing(WireMockRuntimeInfo wm) throws IOException {
        stubTwoPages();

        List<BriefingIssue> issues = adapter.search(context(wm), JQL, ALL_FIELDS, BriefingQueries.UNCAPPED);

        assertThat(issues.get(4).created()).isNull();
        assertThat(issues.get(5).storyPoints()).isNull();
        assertThat(issues.get(3).priority()).isNull();
        assertThat(issues.get(3).subtaskCount()).isZero();
    }

    @Test
    void absentFieldIdsRequestOnlySystemFieldsAndNeverFail(WireMockRuntimeInfo wm) throws IOException {
        stubTwoPages();

        List<BriefingIssue> issues = adapter.search(context(wm), JQL, new BriefingFieldIds(null, null, null, null),
                BriefingQueries.UNCAPPED);

        verify(postRequestedFor(urlEqualTo(SEARCH_URL)).withRequestBody(notMatching("(?s).*customfield.*")));
        BriefingIssue first = issues.get(0);
        assertThat(first.flagged()).isFalse();
        assertThat(first.storyPoints()).isNull();
        assertThat(first.sprints()).isEmpty();
        // No AC field on the site: the description is the fallback, and CNV-1's has no such wording.
        assertThat(first.acceptanceCriteriaPresent()).isFalse();
        assertThat(issues.get(2).acceptanceCriteriaPresent()).isFalse();
    }

    @Test
    void descriptionWithGivenWhenThenCountsAsAcceptanceCriteriaWhenTheSiteHasNoField(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson("{\"issues\":[{\"key\":\"CNV-9\",\"fields\":{"
                + "\"description\":\"Given a user, when they log in, then they see the home page\"}}]}")));

        List<BriefingIssue> issues = adapter.search(context(wm), JQL, new BriefingFieldIds(null, null, null, null),
                BriefingQueries.UNCAPPED);

        assertThat(issues.get(0).acceptanceCriteriaPresent()).isTrue();
    }

    @Test
    void clipsLongDescriptions(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson("{\"issues\":[{\"key\":\"CNV-9\",\"fields\":{"
                + "\"description\":\"" + "x".repeat(6000) + "\"}}]}")));

        List<BriefingIssue> issues = adapter.search(context(wm), JQL, ALL_FIELDS, BriefingQueries.UNCAPPED);

        assertThat(issues.get(0).description()).hasSizeLessThanOrEqualTo(4001);
    }

    @Test
    void stopsAtMaxIssuesWithoutRequestingTheNextPage(WireMockRuntimeInfo wm) {
        StringBuilder five = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            five.append(i > 1 ? "," : "").append("{\"key\":\"CNV-").append(i).append("\",\"fields\":{}}");
        }
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(okJson(
                "{\"issues\":[" + five + "],\"nextPageToken\":\"more\"}")));

        List<BriefingIssue> issues = adapter.search(context(wm), JQL, ALL_FIELDS, 3);

        assertThat(issues).hasSize(3);
        verify(1, postRequestedFor(urlEqualTo(SEARCH_URL)));
        verify(postRequestedFor(urlEqualTo(SEARCH_URL)).withRequestBody(containing("\"maxResults\":3")));
    }

    @Test
    void mapsHttpFailuresToReasonsWithoutRetrying(WireMockRuntimeInfo wm) {
        assertReason(wm, 401, JiraAuthFailureReason.INVALID_CREDENTIALS);
        assertReason(wm, 403, JiraAuthFailureReason.JIRA_FORBIDDEN);
        assertReason(wm, 429, JiraAuthFailureReason.JIRA_RATE_LIMITED);
        assertReason(wm, 500, JiraAuthFailureReason.JIRA_ERROR);
    }

    private void assertReason(WireMockRuntimeInfo wm, int status, JiraAuthFailureReason reason) {
        com.github.tomakehurst.wiremock.client.WireMock.reset();
        stubFor(post(urlEqualTo(SEARCH_URL)).willReturn(aResponse().withStatus(status)));

        assertThatThrownBy(() -> adapter.search(context(wm), JQL, ALL_FIELDS, BriefingQueries.UNCAPPED))
                .isInstanceOfSatisfying(JiraTicketReadException.class, e -> assertThat(e.reason()).isEqualTo(reason));
        verify(1, postRequestedFor(urlEqualTo(SEARCH_URL)));
    }

    @Test
    void onlyEverPostsTheJqlSearch(WireMockRuntimeInfo wm) throws IOException {
        stubTwoPages();

        adapter.search(context(wm), JQL, ALL_FIELDS, BriefingQueries.UNCAPPED);

        verify(2, postRequestedFor(urlPathEqualTo(SEARCH_URL)).withRequestBody(containing("\"jql\":\"project = CNV\""))
                .withHeader("Authorization", containing("Basic ")));
        verify(2, anyRequestedFor(anyUrl()));
    }
}
