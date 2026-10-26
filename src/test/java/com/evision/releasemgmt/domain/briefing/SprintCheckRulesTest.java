package com.evision.releasemgmt.domain.briefing;

import com.evision.releasemgmt.domain.briefing.SprintCheckRules.Readiness;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SprintCheckRulesTest {

    private static final BriefingFieldIds FIELDS = new BriefingFieldIds("cf1", "cf2", "cf3", "cf4");
    private static final BriefingRunSettings SETTINGS = new BriefingRunSettings(
            new BriefingThresholds(3, 5, 2), new WorkCalendar(WorkCalendar.CAIRO, WorkCalendar.SUN_TO_THU),
            BriefingRunSettings.DEFAULT_KEEP_RUNS);
    private static final String GOOD = "As a user I want to pay so that I save time. Invalid input is rejected.";

    private static BriefingIssue issue(String key, String summary, String description, boolean ac,
            BigDecimal points, String parent, int subtasks, List<BriefingIssue.Sprint> sprints) {
        return new BriefingIssue(key, summary, "Story", "To Do", StatusCategory.TO_DO, "Medium", false, "Sara",
                null, null, null, null, points, parent, subtasks, ac, description, sprints);
    }

    private static BriefingIssue ready(String key) {
        return issue(key, key + " summary", GOOD, true, BigDecimal.ONE, "CNV-1", 1, List.of());
    }

    private static BriefingContent run(List<BriefingIssue> next, List<BriefingIssue> backlog, BriefingFieldIds f) {
        return SprintCheckRules.compute(new SprintCheckInput("CNV", next, backlog, SETTINGS, f));
    }

    private static List<BriefingContent.Item> items(BriefingContent c) {
        assertThat(c.sections()).extracting(BriefingContent.Section::title)
                .containsExactly("Sprint or backlog items checked");
        return c.sections().get(0).items();
    }

    @Test
    void fullyReadyItemIsYes() {
        assertThat(SprintCheckRules.rate(ready("CNV-2"), FIELDS)).isEqualTo(Readiness.YES);
    }

    @Test
    void oneAdvisoryMissingIsStillYes() {
        BriefingIssue i = issue("CNV-2", "s", GOOD, true, BigDecimal.ONE, "CNV-1", 0, List.of());
        assertThat(SprintCheckRules.rate(i, FIELDS)).isEqualTo(Readiness.YES);
    }

    @Test
    void twoAdvisoriesMissingIsPartly() {
        BriefingIssue i = issue("CNV-2", "s", "As a user I want x so that y", true, BigDecimal.ONE, "CNV-1", 0,
                List.of());
        assertThat(SprintCheckRules.rate(i, FIELDS)).isEqualTo(Readiness.PARTLY);
    }

    @Test
    void oneEssentialMissingIsPartly() {
        BriefingIssue i = issue("CNV-2", "s", GOOD, true, null, "CNV-1", 1, List.of());
        assertThat(SprintCheckRules.rate(i, FIELDS)).isEqualTo(Readiness.PARTLY);
    }

    @Test
    void twoEssentialsMissingIsNo() {
        BriefingIssue i = issue("CNV-2", "s", GOOD, false, null, "CNV-1", 1, List.of());
        assertThat(SprintCheckRules.rate(i, FIELDS)).isEqualTo(Readiness.NO);
    }

    @Test
    void unavailableEstimateFieldIsNotCheckedAndNotMissing() {
        BriefingFieldIds noPoints = new BriefingFieldIds(null, "cf2", "cf3", "cf4");
        BriefingIssue i = issue("CNV-2", "Pay", GOOD, true, null, "CNV-1", 1, List.of());
        assertThat(SprintCheckRules.rate(i, noPoints)).isEqualTo(Readiness.YES);
        BriefingContent c = run(List.of(), List.of(i), noPoints);
        assertThat(items(c).get(1).text()).isEqualTo("Pay: Yes. Missing: nothing; not checked: estimate");
    }

    @Test
    void openQuestionMarkersCountAsAdvisoryMisses() {
        for (String d : List.of(GOOD + "\nWho approves?", GOOD + " TBD", GOOD + " tbc", GOOD + " Open question here")) {
            BriefingIssue i = issue("CNV-2", "s", d, true, BigDecimal.ONE, "CNV-1", 0, List.of());
            assertThat(SprintCheckRules.rate(i, FIELDS)).isEqualTo(Readiness.PARTLY);
        }
    }

    @Test
    void nextSprintWithEarliestStartIsChosen() {
        BriefingIssue.Sprint later = new BriefingIssue.Sprint(2, "Sprint 9", "future",
                Instant.parse("2026-11-08T00:00:00Z"), null);
        BriefingIssue.Sprint sooner = new BriefingIssue.Sprint(3, "Sprint 8", "future",
                Instant.parse("2026-11-01T00:00:00Z"), null);
        BriefingIssue a = issue("CNV-2", "A", GOOD, true, BigDecimal.ONE, "CNV-1", 1, List.of(later));
        BriefingIssue b = issue("CNV-3", "B", GOOD, true, BigDecimal.ONE, "CNV-1", 1, List.of(sooner));
        BriefingContent c = run(List.of(a, b), List.of(ready("CNV-9")), FIELDS);
        assertThat(items(c)).extracting(BriefingContent.Item::text)
                .containsExactly("Checked: next sprint Sprint 8", "B: Yes. Missing: nothing");
        assertThat(c.headline()).isEqualTo("All 1 items ready for Sprint 8 in CNV");
    }

    @Test
    void nullStartFallsBackToLowestSprintId() {
        BriefingIssue.Sprint s5 = new BriefingIssue.Sprint(5, "Five", "future", null, null);
        BriefingIssue.Sprint s4 = new BriefingIssue.Sprint(4, "Four", "future", null, null);
        BriefingIssue a = issue("CNV-2", "A", GOOD, true, BigDecimal.ONE, "CNV-1", 1, List.of(s5, s4));
        assertThat(items(run(List.of(a), List.of(), FIELDS)).get(0).text()).isEqualTo("Checked: next sprint Four");
    }

    @Test
    void backlogIsUsedWhenThereIsNoNextSprintAndLimitedToTheThreshold() {
        BriefingContent c = run(List.of(), List.of(ready("CNV-2"), ready("CNV-3"), ready("CNV-4")), FIELDS);
        assertThat(items(c)).hasSize(3);
        assertThat(items(c).get(0).text()).isEqualTo("Checked: top 2 backlog items (no future sprint)");
        assertThat(items(c).get(0).issueKey()).isNull();
    }

    @Test
    void itemsAreSortedNoPartlyYesAndAttentionCountsPartlyAndNo() {
        BriefingIssue yes = ready("CNV-2");
        BriefingIssue partly = issue("CNV-3", "P", GOOD, true, null, "CNV-1", 1, List.of());
        BriefingIssue no = issue("CNV-4", "N", GOOD, false, null, "CNV-1", 1, List.of());
        SprintCheckInput in = new SprintCheckInput("CNV", List.of(), List.of(yes, partly, no),
                new BriefingRunSettings(new BriefingThresholds(3, 5, 3), SETTINGS.calendar(), 60), FIELDS);
        BriefingContent c = SprintCheckRules.compute(in);
        assertThat(items(c)).extracting(BriefingContent.Item::issueKey).containsExactly(null, "CNV-4", "CNV-3", "CNV-2");
        assertThat(items(c).get(1).text()).isEqualTo("N: No. Missing: acceptance criteria, estimate");
        assertThat(items(c).get(2).text()).isEqualTo("P: Partly. Missing: estimate");
        assertThat(c.attention()).isEqualTo(2);
        assertThat(c.status()).isEqualTo(BriefingStatus.ATTENTION);
        assertThat(c.headline()).isEqualTo("2 of 3 items not ready for the backlog in CNV");
        assertThat(c.summary()).isEqualTo("1 Yes, 1 Partly, 1 No\nFix first: CNV-4, CNV-3");
    }

    @Test
    void paymentChecksAreAdvisoryNotMentionedAndNeverChangeTheRating() {
        BriefingIssue pay = issue("CNV-2", "Payment transfer", GOOD, true, BigDecimal.ONE, "CNV-1", 1, List.of());
        assertThat(SprintCheckRules.rate(pay, FIELDS)).isEqualTo(Readiness.YES);
        String text = items(run(List.of(), List.of(pay), FIELDS)).get(1).text();
        assertThat(text).isEqualTo("Payment transfer: Yes. Missing: nothing; not mentioned: idempotency, audit, "
                + "maker-checker, return path, ISO 20022");
        assertThat(items(run(List.of(), List.of(ready("CNV-3")), FIELDS)).get(1).text()).doesNotContain("not mentioned");
    }

    @Test
    void mentionedPaymentTopicsAreNotListed() {
        BriefingIssue pay = issue("CNV-2", "Transfer",
                GOOD + " Idempotency key, audit log, maker-checker, reversal, ISO 20022.", true, BigDecimal.ONE,
                "CNV-1", 1, List.of());
        assertThat(items(run(List.of(), List.of(pay), FIELDS)).get(1).text()).doesNotContain("not mentioned");
    }

    @Test
    void nothingToCheckIsQuiet() {
        BriefingContent c = run(List.of(), List.of(), FIELDS);
        assertThat(c.status()).isEqualTo(BriefingStatus.QUIET);
        assertThat(c.attention()).isZero();
        assertThat(c.headline()).isEqualTo("No sprint or backlog items to check in CNV");
        assertThat(c.summary()).doesNotContain("Fix first");
    }

    @Test
    void allReadyIsOk() {
        BriefingContent c = run(List.of(), List.of(ready("CNV-2")), FIELDS);
        assertThat(c.status()).isEqualTo(BriefingStatus.OK);
        assertThat(c.headline()).isEqualTo("All 1 items ready for the backlog in CNV");
    }
}
