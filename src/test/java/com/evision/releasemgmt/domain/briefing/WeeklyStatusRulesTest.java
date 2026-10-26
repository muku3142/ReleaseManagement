package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.briefing.WeeklyStatusRules.Overall;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyStatusRulesTest {

    private static final Instant NOW = cairo(2026, 10, 29, 13, 41);
    private static final Instant WEEK_START = cairo(2026, 10, 25, 0, 0);
    private static final BriefingRunSettings SETTINGS = new BriefingRunSettings(BriefingThresholds.DEFAULTS,
            new WorkCalendar(WorkCalendar.CAIRO, WorkCalendar.SUN_TO_THU), BriefingRunSettings.DEFAULT_KEEP_RUNS);
    private static final BriefingIssue.Sprint SPRINT_ENDING_LATER = new BriefingIssue.Sprint(1, "Sprint 7",
            "active", cairo(2026, 10, 18, 0, 0), cairo(2026, 11, 12, 0, 0));
    private static final BriefingIssue.Sprint SPRINT_ENDING_SOON = new BriefingIssue.Sprint(1, "Sprint 7",
            "active", cairo(2026, 10, 18, 0, 0), cairo(2026, 11, 1, 0, 0));

    private static Instant cairo(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(WorkCalendar.CAIRO).toInstant();
    }

    private static BriefingIssue issue(String key, StatusCategory category, boolean flagged, String assignee,
            Instant created, Instant resolved, LocalDate due, BigDecimal points, BriefingIssue.Sprint sprint) {
        return new BriefingIssue(key, key + " summary", "Task", category == StatusCategory.DONE ? "Done" : "In Progress",
                category, "Medium", flagged, assignee, created, NOW, resolved, due, points, "CNV-1", 0, true, "",
                sprint == null ? List.of() : List.of(sprint));
    }

    private static BriefingIssue open(String key) {
        return issue(key, StatusCategory.IN_PROGRESS, false, "Sara", cairo(2026, 10, 1, 9, 0), null, null,
                BigDecimal.ONE, SPRINT_ENDING_LATER);
    }

    private static BriefingIssue blocked(String key, String assignee) {
        return issue(key, StatusCategory.IN_PROGRESS, true, assignee, cairo(2026, 10, 1, 9, 0), null, null,
                BigDecimal.ONE, SPRINT_ENDING_LATER);
    }

    private static BriefingIssue done(String key, Instant resolved) {
        return issue(key, StatusCategory.DONE, false, "Sara", cairo(2026, 10, 1, 9, 0), resolved, null,
                BigDecimal.TWO, SPRINT_ENDING_LATER);
    }

    private static BriefingIssue overdue(String key) {
        return issue(key, StatusCategory.TO_DO, false, "Sara", cairo(2026, 10, 1, 9, 0), null,
                LocalDate.of(2026, 10, 27), BigDecimal.ONE, null);
    }

    private static WeeklyInput input(List<BriefingIssue> completed, List<BriefingIssue> inProgress,
            List<BriefingIssue> sprint, List<BriefingIssue> added, List<BriefingIssue> due) {
        return new WeeklyInput("CNV", completed, inProgress, sprint, added, due, NOW, SETTINGS);
    }

    private static BriefingContent.Section section(BriefingContent c, String title) {
        return c.sections().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow();
    }

    private static List<String> texts(BriefingContent c, String title) {
        return section(c, title).items().stream().map(BriefingContent.Item::text).toList();
    }

    @Test
    void sectionsAreInOrder() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThat(c.sections()).extracting(BriefingContent.Section::title).containsExactly("Completed",
                "In progress", "Blocked", "Newly added", "Sprint committed vs done", "Overdue", "Executive draft",
                "Engineering draft");
    }

    @Test
    void completedKeepsOnlyDoneItemsResolvedThisWeek() {
        BriefingContent c = WeeklyStatusRules.compute(input(
                List.of(done("CNV-2", WEEK_START.plusSeconds(60)), done("CNV-3", WEEK_START.minusSeconds(60)),
                        open("CNV-4")), List.of(), List.of(), List.of(), List.of()));
        assertThat(section(c, "Completed").items()).extracting(BriefingContent.Item::issueKey)
                .containsExactly("CNV-2");
    }

    @Test
    void inProgressKeepsOnlyInProgressCategory() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(),
                List.of(open("CNV-2"), overdue("CNV-3")), List.of(), List.of(), List.of()));
        assertThat(section(c, "In progress").items()).extracting(BriefingContent.Item::issueKey)
                .containsExactly("CNV-2");
    }

    @Test
    void blockedIsDeduplicatedAcrossInProgressAndTheOpenSprintAndSkipsDone() {
        BriefingIssue b = blocked("CNV-2", "Sara");
        BriefingIssue doneFlagged = issue("CNV-3", StatusCategory.DONE, true, "Sara", NOW, NOW, null, null, null);
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(b),
                List.of(b, blocked("CNV-4", "Sara"), doneFlagged), List.of(), List.of()));
        assertThat(texts(c, "Blocked")).containsExactly("CNV-2 summary (flagged)", "CNV-4 summary (flagged)");
    }

    @Test
    void newlyAddedKeepsItemsCreatedThisWeek() {
        BriefingIssue fresh = issue("CNV-2", StatusCategory.TO_DO, false, null, WEEK_START.plusSeconds(1), null,
                null, null, null);
        BriefingIssue old = issue("CNV-3", StatusCategory.TO_DO, false, null, WEEK_START.minusSeconds(1), null,
                null, null, null);
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(fresh, old),
                List.of()));
        assertThat(section(c, "Newly added").items()).extracting(BriefingContent.Item::issueKey)
                .containsExactly("CNV-2");
    }

    @Test
    void overdueIsNotDoneWithDueDateBeforeTodayInCairo() {
        BriefingIssue today = issue("CNV-3", StatusCategory.TO_DO, false, "Sara", NOW, null,
                LocalDate.of(2026, 10, 29), null, null);
        BriefingIssue doneLate = issue("CNV-4", StatusCategory.DONE, false, "Sara", NOW, NOW,
                LocalDate.of(2026, 10, 1), null, null);
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(),
                List.of(overdue("CNV-2"), today, doneLate)));
        assertThat(section(c, "Overdue").items()).extracting(BriefingContent.Item::issueKey).containsExactly("CNV-2");
    }

    @Test
    void sprintScopeWithoutActiveSprintIsTbc() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThat(texts(c, "Sprint committed vs done")).containsExactly("TBC: no active sprint found");
    }

    @Test
    void sprintScopeCountsIssuesAndPoints() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(),
                List.of(open("CNV-2"), done("CNV-3", NOW)), List.of(), List.of()));
        assertThat(texts(c, "Sprint committed vs done"))
                .containsExactly("Current sprint scope: 2 issues (3 points); done 1 (2 points)");
    }

    @Test
    void sprintPointsReadTbcWhenAnEstimateIsMissing() {
        BriefingIssue noPoints = issue("CNV-2", StatusCategory.IN_PROGRESS, false, "Sara", NOW, null, null, null,
                SPRINT_ENDING_LATER);
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(),
                List.of(noPoints, done("CNV-3", NOW)), List.of(), List.of()));
        assertThat(texts(c, "Sprint committed vs done"))
                .containsExactly("Current sprint scope: 2 issues (TBC points); done 1 (2 points)");
    }

    @Test
    void overallIsTbcWithoutAnActiveSprintAndNoRisks() {
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(), List.of(), List.of(), List.of())))
                .isEqualTo(Overall.TBC);
    }

    @Test
    void overallIsAtRiskWithoutAnActiveSprintButWithAnOverdueItem() {
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(), List.of(), List.of(),
                List.of(overdue("CNV-2"))))).isEqualTo(Overall.AT_RISK);
    }

    @Test
    void overallIsOnTrackWithNoRisks() {
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(open("CNV-2")),
                List.of(open("CNV-2"), done("CNV-3", NOW)), List.of(), List.of()))).isEqualTo(Overall.ON_TRACK);
    }

    @Test
    void overallIsAtRiskWithOneBlockedOrOverdue() {
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(blocked("CNV-2", "Sara")),
                List.of(done("CNV-3", NOW)), List.of(), List.of()))).isEqualTo(Overall.AT_RISK);
    }

    @Test
    void overallIsOffTrackWithThreeRisks() {
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(blocked("CNV-2", "Sara")),
                List.of(done("CNV-3", NOW), done("CNV-4", NOW)), List.of(),
                List.of(overdue("CNV-5"), overdue("CNV-6"))))).isEqualTo(Overall.OFF_TRACK);
    }

    @Test
    void overallIsOffTrackWhenLessThanHalfIsDoneAndTheSprintEndsWithinTwoWorkingDays() {
        BriefingIssue lateA = issue("CNV-2", StatusCategory.IN_PROGRESS, false, "Sara", NOW, null, null, null,
                SPRINT_ENDING_SOON);
        BriefingIssue lateB = issue("CNV-3", StatusCategory.TO_DO, false, "Sara", NOW, null, null, null,
                SPRINT_ENDING_SOON);
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(), List.of(lateA, lateB), List.of(),
                List.of()))).isEqualTo(Overall.OFF_TRACK);
    }

    @Test
    void lowDoneRatioWithTheSprintFarFromTheEndIsNotOffTrack() {
        assertThat(WeeklyStatusRules.overall(input(List.of(), List.of(), List.of(open("CNV-2"), open("CNV-3")),
                List.of(), List.of()))).isEqualTo(Overall.ON_TRACK);
    }

    @Test
    void attentionIsRisksPlusUnassignedBlockedDecisions() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(),
                List.of(blocked("CNV-2", null), blocked("CNV-3", "Sara")),
                List.of(done("CNV-4", NOW)), List.of(), List.of(overdue("CNV-5"))));
        assertThat(c.attention()).isEqualTo(4);
        assertThat(c.status()).isEqualTo(BriefingStatus.ATTENTION);
        assertThat(texts(c, "Executive draft")).contains("Decisions needed: CNV-2 (owner TBC, date TBC)",
                "Top risks: 2 blocked, 1 overdue");
    }

    @Test
    void atRiskIsAttentionAndAnEmptyWeekIsQuiet() {
        assertThat(WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(), List.of())).status())
                .isEqualTo(BriefingStatus.QUIET);
        assertThat(WeeklyStatusRules.compute(input(List.of(done("CNV-2", NOW)), List.of(), List.of(done("CNV-2", NOW)),
                List.of(), List.of())).status()).isEqualTo(BriefingStatus.OK);
    }

    @Test
    void executiveDraftWithNoRisksReadsNoneAndNamesTheMilestone() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(done("CNV-2", NOW)), List.of(open("CNV-3")),
                List.of(open("CNV-3"), done("CNV-2", NOW)), List.of(), List.of()));
        assertThat(texts(c, "Executive draft")).containsExactly(
                "Overall status: On track — no blocked or overdue items",
                "Outcomes delivered: 1 issues completed",
                "Top risks: none known",
                "Decisions needed: none",
                "Next milestone: Sprint 7 ends 2026-11-12");
        assertThat(section(c, "Executive draft").items()).allMatch(i -> i.issueKey() == null);
    }

    @Test
    void executiveDraftMilestoneIsTbcWithoutASprint() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThat(texts(c, "Executive draft")).contains("Next milestone: TBC");
    }

    @Test
    void engineeringDraftListsKeysAndLeavesUnknownsTbc() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(done("CNV-2", NOW), done("CNV-3", NOW)),
                List.of(blocked("CNV-4", "Sara")), List.of(), List.of(), List.of()));
        assertThat(texts(c, "Engineering draft")).containsExactly("Completed: CNV-2, CNV-3",
                "In progress: CNV-4", "Blocked: CNV-4 (flagged)", "Next week's focus: TBC", "Dependencies: TBC");
        BriefingContent empty = WeeklyStatusRules.compute(input(List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThat(texts(empty, "Engineering draft")).startsWith("Completed: none", "In progress: none",
                "Blocked: none");
    }

    @Test
    void headlineStartsWithTheOverallLabel() {
        BriefingContent c = WeeklyStatusRules.compute(input(List.of(done("CNV-2", NOW)),
                List.of(blocked("CNV-4", "Sara")), List.of(done("CNV-2", NOW)), List.of(), List.of(overdue("CNV-5"))));
        assertThat(c.headline()).isEqualTo("At risk: 1 completed, 1 blocked, 1 overdue in CNV");
    }
}
