package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.jira.StatusCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DigestRulesTest {

    private static final Instant NOW = cairo(2026, 10, 26, 9, 0);
    private static final Instant SINCE = cairo(2026, 10, 25, 9, 0);
    private static final BriefingFieldIds FIELDS = new BriefingFieldIds("cf1", "cf2", "cf3", "cf4");
    private static final BriefingRunSettings SETTINGS = new BriefingRunSettings(BriefingThresholds.DEFAULTS,
            new WorkCalendar(WorkCalendar.CAIRO, WorkCalendar.SUN_TO_THU), BriefingRunSettings.DEFAULT_KEEP_RUNS);

    private static Instant cairo(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(WorkCalendar.CAIRO).toInstant();
    }

    private static BriefingIssue issue(String key, String type, String priority, boolean flagged, String status,
            StatusCategory category, Instant updated, LocalDate due, BigDecimal points, String parent, boolean ac) {
        return new BriefingIssue(key, key + " summary", type, status, category, priority, flagged, "Sara", updated,
                updated, null, due, points, parent, 0, ac, "", List.of());
    }

    private static BriefingIssue plain(String key) {
        return issue(key, "Task", "Medium", false, "In Progress", StatusCategory.IN_PROGRESS, NOW, null,
                BigDecimal.ONE, "CNV-1", true);
    }

    private static BriefingIssue story(String key, BigDecimal points, String parent, boolean ac) {
        return issue(key, "Story", "Medium", false, "To Do", StatusCategory.TO_DO, NOW, null, points, parent, ac);
    }

    private static BriefingContent compute(BriefingFieldIds fields, List<BriefingIssue> sprint,
            List<BriefingIssue> stale, List<BriefingIssue> next, List<BriefingIssue> created,
            List<BriefingIssue> done, List<BriefingIssue> reopened, List<BriefingIssue> due) {
        return DigestRules.compute(new DigestInput("CNV", sprint, stale, next, created, done, reopened, due, SINCE,
                NOW, SETTINGS, fields));
    }

    private static BriefingContent sprint(BriefingIssue... issues) {
        return compute(FIELDS, List.of(issues), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static BriefingContent.Section section(BriefingContent c, String title) {
        return c.sections().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void sectionsAreInOrderWithTheExpectedTitles() {
        assertThat(sprint().sections()).extracting(BriefingContent.Section::title).containsExactly("Blockers",
                "Stale in progress", "Not ready", "Changes since the last run", "Due soon or overdue");
    }

    @Test
    void blockersAreFlaggedHighestBlockerPriorityOrBlockedStatus() {
        BriefingIssue flagged = issue("CNV-2", "Task", "Medium", true, "In Progress", StatusCategory.IN_PROGRESS,
                NOW, null, BigDecimal.ONE, "CNV-1", true);
        BriefingIssue highest = issue("CNV-3", "Task", "Highest", false, "To Do", StatusCategory.TO_DO, NOW, null,
                BigDecimal.ONE, "CNV-1", true);
        BriefingIssue blocker = issue("CNV-4", "Task", "blocker", false, "To Do", StatusCategory.TO_DO, NOW, null,
                BigDecimal.ONE, "CNV-1", true);
        BriefingIssue blocked = issue("CNV-5", "Task", "Low", false, "Blocked", StatusCategory.IN_PROGRESS, NOW,
                null, BigDecimal.ONE, "CNV-1", true);
        BriefingContent c = sprint(flagged, highest, blocker, blocked, plain("CNV-6"));

        List<BriefingContent.Item> items = section(c, "Blockers").items();
        assertThat(items).extracting(BriefingContent.Item::issueKey)
                .containsExactly("CNV-2", "CNV-3", "CNV-4", "CNV-5");
        assertThat(items.get(0).text()).isEqualTo("CNV-2 summary (flagged)");
        assertThat(items.get(1).text()).isEqualTo("CNV-3 summary (priority Highest)");
        assertThat(items.get(3).text()).isEqualTo("CNV-5 summary (status Blocked)");
    }

    @Test
    void staleNeedsThreeWorkingDaysEvenIfThePrefilterReturnedIt() {
        BriefingIssue stale = issue("CNV-7", "Task", "Medium", false, "In Progress", StatusCategory.IN_PROGRESS,
                cairo(2026, 10, 21, 10, 0), null, BigDecimal.ONE, "CNV-1", true);
        BriefingIssue twoDays = issue("CNV-8", "Task", "Medium", false, "In Progress", StatusCategory.IN_PROGRESS,
                cairo(2026, 10, 22, 10, 0), null, BigDecimal.ONE, "CNV-1", true);
        BriefingContent c = compute(FIELDS, List.of(), List.of(stale, twoDays), List.of(), List.of(), List.of(),
                List.of(), List.of());

        assertThat(section(c, "Stale in progress").items()).singleElement().satisfies(i -> {
            assertThat(i.issueKey()).isEqualTo("CNV-7");
            assertThat(i.text()).isEqualTo("CNV-7 summary: no update for 3 working days (Sara)");
        });
    }

    @Test
    void staleShowsUnassignedWhenThereIsNoAssignee() {
        BriefingIssue s = new BriefingIssue("CNV-9", "x", "Task", "In Progress", StatusCategory.IN_PROGRESS, "Low",
                false, null, NOW, cairo(2026, 10, 18, 10, 0), null, null, null, null, 0, true, "", List.of());
        BriefingContent c = compute(FIELDS, List.of(), List.of(s), List.of(), List.of(), List.of(), List.of(),
                List.of());
        assertThat(section(c, "Stale in progress").items().get(0).text()).endsWith("(Unassigned)");
    }

    @Test
    void notReadyListsOnlyWhatIsMissingForStoriesAcrossBothSprintsOnce() {
        BriefingIssue missingAll = story("CNV-10", null, null, false);
        BriefingIssue missingEpic = story("CNV-11", BigDecimal.TWO, null, true);
        BriefingIssue ready = story("CNV-12", BigDecimal.TWO, "CNV-1", true);
        BriefingIssue task = issue("CNV-13", "Task", "Low", false, "To Do", StatusCategory.TO_DO, NOW, null, null,
                null, false);
        BriefingContent c = compute(FIELDS, List.of(missingAll, ready, task), List.of(), List.of(missingAll,
                missingEpic), List.of(), List.of(), List.of(), List.of());

        List<BriefingContent.Item> items = section(c, "Not ready").items();
        assertThat(items).extracting(BriefingContent.Item::text).containsExactly(
                "CNV-10 summary: missing acceptance criteria, estimate, epic", "CNV-11 summary: missing epic");
    }

    @Test
    void aSiteWithoutTheStoryPointsFieldNeverReportsAMissingEstimate() {
        BriefingContent c = compute(new BriefingFieldIds(null, "cf2", "cf3", "cf4"),
                List.of(story("CNV-14", null, "CNV-1", true)), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        assertThat(section(c, "Not ready").items()).isEmpty();
    }

    @Test
    void changesSinceTheLastRunExcludeOlderItems() {
        BriefingIssue newIssue = withTimes("CNV-20", cairo(2026, 10, 25, 12, 0), null);
        BriefingIssue oldCreated = withTimes("CNV-21", cairo(2026, 10, 20, 12, 0), null);
        BriefingIssue closed = withTimes("CNV-22", cairo(2026, 10, 1, 12, 0), cairo(2026, 10, 25, 13, 0));
        BriefingIssue oldDone = withTimes("CNV-23", cairo(2026, 10, 1, 12, 0), cairo(2026, 10, 20, 13, 0));
        BriefingIssue reopened = issue("CNV-24", "Task", "Low", false, "To Do", StatusCategory.TO_DO,
                cairo(2026, 10, 25, 14, 0), null, BigDecimal.ONE, "CNV-1", true);
        BriefingIssue oldReopen = issue("CNV-25", "Task", "Low", false, "To Do", StatusCategory.TO_DO,
                cairo(2026, 10, 20, 14, 0), null, BigDecimal.ONE, "CNV-1", true);
        BriefingContent c = compute(FIELDS, List.of(), List.of(), List.of(), List.of(newIssue, oldCreated),
                List.of(closed, oldDone), List.of(reopened, oldReopen), List.of());

        assertThat(section(c, "Changes since the last run").items()).extracting(BriefingContent.Item::text)
                .containsExactly("CNV-20 summary: created", "CNV-22 summary: moved to Done",
                        "CNV-24 summary: reopened");
        assertThat(c.attention()).isZero();
        assertThat(c.status()).isEqualTo(BriefingStatus.OK);
    }

    private static BriefingIssue withTimes(String key, Instant created, Instant resolved) {
        return new BriefingIssue(key, key + " summary", "Task", "Done", StatusCategory.DONE, "Low", false, "Sara",
                created, created, resolved, null, null, null, 0, true, "", List.of());
    }

    @Test
    void dueSoonAndOverdueUseTheCairoDate() {
        BriefingIssue overdue = dueIssue("CNV-30", LocalDate.of(2026, 10, 20), StatusCategory.TO_DO);
        BriefingIssue today = dueIssue("CNV-31", LocalDate.of(2026, 10, 26), StatusCategory.TO_DO);
        BriefingIssue edge = dueIssue("CNV-32", LocalDate.of(2026, 10, 31), StatusCategory.IN_PROGRESS);
        BriefingIssue later = dueIssue("CNV-33", LocalDate.of(2026, 11, 1), StatusCategory.TO_DO);
        BriefingIssue done = dueIssue("CNV-34", LocalDate.of(2026, 10, 20), StatusCategory.DONE);
        BriefingContent c = compute(FIELDS, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(overdue, today, edge, later, done));

        assertThat(section(c, "Due soon or overdue").items()).extracting(BriefingContent.Item::text).containsExactly(
                "CNV-30 summary: overdue since 2026-10-20", "CNV-31 summary: due 2026-10-26",
                "CNV-32 summary: due 2026-10-31");
    }

    private static BriefingIssue dueIssue(String key, LocalDate due, StatusCategory category) {
        return issue(key, "Task", "Low", false, "x", category, NOW, due, BigDecimal.ONE, "CNV-1", true);
    }

    @Test
    void attentionCountsFourSectionsAndBuildsTheHeadlineAndSummary() {
        BriefingIssue blocker = issue("CNV-40", "Task", "Highest", false, "To Do", StatusCategory.TO_DO, NOW, null,
                BigDecimal.ONE, "CNV-1", true);
        BriefingIssue overdue = dueIssue("CNV-41", LocalDate.of(2026, 10, 20), StatusCategory.TO_DO);
        BriefingContent c = compute(FIELDS, List.of(blocker), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(overdue));

        assertThat(c.attention()).isEqualTo(2);
        assertThat(c.status()).isEqualTo(BriefingStatus.ATTENTION);
        assertThat(c.headline()).isEqualTo("1 blockers, 0 stale, 0 not ready, 1 due soon or overdue in CNV");
        assertThat(c.summary()).isEqualTo("1 blockers, 0 stale, 0 not ready, 1 due soon or overdue, "
                + "0 changes since the last run\nMost important: CNV-40 CNV-40 summary");
    }

    @Test
    void quietWhenEverythingIsEmptyAndTheSummaryHasNoMostImportantLine() {
        BriefingContent c = sprint();
        assertThat(c.status()).isEqualTo(BriefingStatus.QUIET);
        assertThat(c.headline()).isEqualTo("Nothing needs attention in CNV");
        assertThat(c.summary()).doesNotContain("Most important");
    }

    @Test
    void itemTextIsClippedTo300CodePoints() {
        BriefingIssue flagged = new BriefingIssue("CNV-50", "x".repeat(500), "Task", "To Do", StatusCategory.TO_DO,
                "Low", true, "Sara", NOW, NOW, null, null, null, null, 0, true, "", List.of());
        String text = section(sprint(flagged), "Blockers").items().get(0).text();
        assertThat(text.codePointCount(0, text.length())).isEqualTo(301);
        assertThat(text).endsWith("…");
    }
}
