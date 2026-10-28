package com.evision.releasemgmt.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiceEditsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final String ACTOR = "pm@acme.com";

    private static RoadmapItem with(RoadmapItem base, RiceInputs inputs, RiceOverrides overrides, Instant archivedAt) {
        return new RoadmapItem(base.id(), base.source(), base.jiraKey(), base.jiraIssueId(), base.title(),
                base.jiraStatus(), base.jiraUpdatedAt(), base.jiraSyncedAt(), null, base.productId(), base.status(),
                null, null, null, inputs, overrides, base.jiraPriority(), base.jiraReadiness(), base.createdAt(),
                base.createdBy(), archivedAt);
    }

    private static RoadmapItem jira() {
        return RoadmapItem.fromJira(
                new JiraSnapshot("K-1", "1", "t", "Open", T0, T0, "High", 2), UUID.randomUUID(), "a", T0);
    }

    private static RoadmapItem manual() {
        return RoadmapItem.manual("t", UUID.randomUUID(), "a", T0);
    }

    private static RejectionAssert rejects(RoadmapRejection reason) {
        return new RejectionAssert(reason);
    }

    private record RejectionAssert(RoadmapRejection reason) {
        void when(Runnable call) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(RoadmapRejectedException.class,
                    e -> assertThat(e.reason()).isEqualTo(reason));
        }
    }

    @Test
    void overrideReachRecordsEffectiveBeforeAndTheReason() {
        RoadmapItem item = jira();
        RiceInputs calculated = RiceCalculation.calculated(item, 7);

        InputUpdate update = RiceEdits.override(item, calculated, RiceField.REACH, BigDecimal.valueOf(20),
                "Big client onboarding", ACTOR, NOW);

        assertThat(update.inputs().reach()).isEqualTo(20);
        assertThat(update.overrides().reach()).isEqualTo("Big client onboarding");
        assertThat(update.changes()).containsExactly(new InputChange(item.id(), RiceField.REACH,
                BigDecimal.valueOf(7), BigDecimal.valueOf(20), NOW, ACTOR, InputChangeKind.OVERRIDDEN,
                "Big client onboarding"));
    }

    @Test
    void overrideRejectsFieldsThatAreNotCalculatedArchivedItemsAndInvalidValues() {
        RoadmapItem manual = manual();
        RiceInputs calc = RiceCalculation.calculated(manual, 0);
        rejects(RoadmapRejection.ROADMAP_FIELD_NOT_CALCULATED).when(() -> RiceEdits.override(manual, calc,
                RiceField.IMPACT, BigDecimal.ONE, "r", ACTOR, NOW));
        rejects(RoadmapRejection.ROADMAP_FIELD_NOT_CALCULATED).when(() -> RiceEdits.override(manual, calc,
                RiceField.CONFIDENCE, BigDecimal.valueOf(80), "r", ACTOR, NOW));
        RoadmapItem jira = jira();
        RiceInputs jcalc = RiceCalculation.calculated(jira, 0);
        rejects(RoadmapRejection.ROADMAP_FIELD_NOT_CALCULATED).when(() -> RiceEdits.override(jira, jcalc,
                RiceField.EFFORT, BigDecimal.ONE, "r", ACTOR, NOW));
        RoadmapItem archived = with(jira, RiceInputs.NONE, RiceOverrides.NONE, T0);
        rejects(RoadmapRejection.ROADMAP_ITEM_ARCHIVED).when(() -> RiceEdits.override(archived, jcalc,
                RiceField.REACH, BigDecimal.ONE, "r", ACTOR, NOW));
        rejects(RoadmapRejection.ROADMAP_REACH_INVALID).when(() -> RiceEdits.override(jira, jcalc,
                RiceField.REACH, BigDecimal.valueOf(-1), "r", ACTOR, NOW));
        rejects(RoadmapRejection.ROADMAP_IMPACT_INVALID).when(() -> RiceEdits.override(jira, jcalc,
                RiceField.IMPACT, new BigDecimal("0.7"), "r", ACTOR, NOW));
        rejects(RoadmapRejection.ROADMAP_CONFIDENCE_INVALID).when(() -> RiceEdits.override(jira, jcalc,
                RiceField.CONFIDENCE, BigDecimal.valueOf(70), "r", ACTOR, NOW));
    }

    @Test
    void overrideWithTheSameValueAndReasonChangesNothing() {
        RoadmapItem item = with(jira(), new RiceInputs(20, null, null, null), new RiceOverrides("why", null, null),
                null);

        InputUpdate update = RiceEdits.override(item, RiceCalculation.calculated(item, 7), RiceField.REACH,
                new BigDecimal("20.0"), "why", ACTOR, NOW);

        assertThat(update.noChange()).isTrue();
    }

    @Test
    void overrideWithANewReasonOnlyStillRecordsARow() {
        RoadmapItem item = with(jira(), new RiceInputs(20, null, null, null), new RiceOverrides("why", null, null),
                null);

        InputUpdate update = RiceEdits.override(item, RiceCalculation.calculated(item, 7), RiceField.REACH,
                BigDecimal.valueOf(20), "new reason", ACTOR, NOW);

        assertThat(update.changes()).singleElement().satisfies(c -> {
            assertThat(c.oldValue()).isEqualByComparingTo("20");
            assertThat(c.reason()).isEqualTo("new reason");
        });
    }

    @Test
    void resetClearsTheOverrideAndRecordsTheCalculatedValue() {
        RoadmapItem item = with(jira(), new RiceInputs(20, null, null, BigDecimal.ONE),
                new RiceOverrides("why", null, null), null);

        InputUpdate update = RiceEdits.reset(item, RiceCalculation.calculated(item, 7), RiceField.REACH, ACTOR, NOW);

        assertThat(update.inputs()).isEqualTo(new RiceInputs(null, null, null, BigDecimal.ONE));
        assertThat(update.overrides()).isEqualTo(RiceOverrides.NONE);
        assertThat(update.changes()).containsExactly(new InputChange(item.id(), RiceField.REACH,
                BigDecimal.valueOf(20), BigDecimal.valueOf(7), NOW, ACTOR, InputChangeKind.RESET, null));
    }

    @Test
    void resetWithoutAnOverrideChangesNothing() {
        RoadmapItem item = jira();

        assertThat(RiceEdits.reset(item, RiceCalculation.calculated(item, 7), RiceField.IMPACT, ACTOR, NOW)
                .noChange()).isTrue();
        RoadmapItem manual = manual();
        rejects(RoadmapRejection.ROADMAP_FIELD_NOT_CALCULATED).when(() -> RiceEdits.reset(manual,
                RiceCalculation.calculated(manual, 0), RiceField.IMPACT, ACTOR, NOW));
    }

    @Test
    void enteredOnAJiraItemRejectsCalculatedFieldsAndWritesEffortOnly() {
        RoadmapItem item = with(jira(), new RiceInputs(20, null, null, null), new RiceOverrides("why", null, null),
                null);

        rejects(RoadmapRejection.ROADMAP_FIELD_CALCULATED).when(() -> RiceEdits.entered(item,
                new RiceInputs(null, BigDecimal.TWO, null, null), ACTOR, NOW));
        rejects(RoadmapRejection.ROADMAP_FIELD_CALCULATED).when(() -> RiceEdits.entered(item,
                new RiceInputs(null, null, 80, null), ACTOR, NOW));

        InputUpdate update = RiceEdits.entered(item, new RiceInputs(5, null, null, new BigDecimal("1.5")), ACTOR, NOW);

        assertThat(update.inputs()).isEqualTo(new RiceInputs(20, null, null, new BigDecimal("1.5")));
        assertThat(update.overrides()).isEqualTo(new RiceOverrides("why", null, null));
        assertThat(update.changes()).containsExactly(new InputChange(item.id(), RiceField.EFFORT, null,
                new BigDecimal("1.5"), NOW, ACTOR, InputChangeKind.ENTERED, null));
    }

    @Test
    void enteredOnAManualItemWritesImpactConfidenceAndEffort() {
        RoadmapItem item = with(manual(), new RiceInputs(3, BigDecimal.ONE, 50, null), RiceOverrides.NONE, null);

        InputUpdate update = RiceEdits.entered(item,
                new RiceInputs(99, BigDecimal.TWO, 50, BigDecimal.ONE), ACTOR, NOW);

        assertThat(update.inputs()).isEqualTo(new RiceInputs(3, BigDecimal.TWO, 50, BigDecimal.ONE));
        assertThat(update.changes()).extracting(InputChange::field)
                .containsExactly(RiceField.IMPACT, RiceField.EFFORT);
        assertThat(update.changes()).allSatisfy(c -> assertThat(c.kind()).isEqualTo(InputChangeKind.ENTERED));
    }

    @Test
    void enteredRejectsArchivedItems() {
        RoadmapItem archived = with(manual(), RiceInputs.NONE, RiceOverrides.NONE, T0);
        rejects(RoadmapRejection.ROADMAP_ITEM_ARCHIVED).when(() -> RiceEdits.entered(archived, RiceInputs.NONE,
                ACTOR, NOW));
    }
}
