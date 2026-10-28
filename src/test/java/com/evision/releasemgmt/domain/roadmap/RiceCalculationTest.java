package com.evision.releasemgmt.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class RiceCalculationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    static RoadmapItem jira(String priority, Integer readiness, RiceInputs entered) {
        RoadmapItem base = RoadmapItem.fromJira(
                new JiraSnapshot("K-1", "1", "t", "Open", T0, T0, priority, readiness), UUID.randomUUID(), "a", T0);
        return new RoadmapItem(base.id(), base.source(), base.jiraKey(), base.jiraIssueId(), base.title(),
                base.jiraStatus(), T0, T0, null, base.productId(), RoadmapStatus.IDEA, null, null, null, entered,
                RiceOverrides.NONE, priority, readiness, T0, "a", null);
    }

    @ParameterizedTest
    @CsvSource({"Highest,3", "Blocker,3", "High,2", "Medium,1", "Low,0.5", "Lowest,0.25", "' high ',2", "HIGHEST,3"})
    void impactFollowsJiraPriority(String priority, String impact) {
        assertThat(RiceCalculation.impactForPriority(priority)).isEqualByComparingTo(impact);
    }

    @Test
    void unknownOrMissingPriorityHasNoImpact() {
        assertThat(RiceCalculation.impactForPriority("Critical")).isNull();
        assertThat(RiceCalculation.impactForPriority("")).isNull();
        assertThat(RiceCalculation.impactForPriority(null)).isNull();
    }

    @Test
    void confidenceFollowsReadinessSignals() {
        assertThat(RiceCalculation.confidenceForSignals(3)).isEqualTo(100);
        assertThat(RiceCalculation.confidenceForSignals(2)).isEqualTo(80);
        assertThat(RiceCalculation.confidenceForSignals(1)).isEqualTo(50);
        assertThat(RiceCalculation.confidenceForSignals(0)).isEqualTo(50);
        assertThat(RiceCalculation.confidenceForSignals(null)).isNull();
    }

    @Test
    void jiraItemCalculatesReachImpactAndConfidence() {
        assertThat(RiceCalculation.calculated(jira("High", 2, RiceInputs.NONE), 7))
                .isEqualTo(new RiceInputs(7, BigDecimal.TWO, 80, null));
    }

    @Test
    void manualItemCalculatesOnlyReach() {
        RoadmapItem manual = RoadmapItem.manual("t", UUID.randomUUID(), "a", T0);
        assertThat(RiceCalculation.calculated(manual, 4)).isEqualTo(new RiceInputs(4, null, null, null));
        assertThat(RiceCalculation.isCalculated(manual, RiceField.REACH)).isTrue();
        assertThat(RiceCalculation.isCalculated(manual, RiceField.IMPACT)).isFalse();
        assertThat(RiceCalculation.isCalculated(manual, RiceField.CONFIDENCE)).isFalse();
        assertThat(RiceCalculation.isCalculated(manual, RiceField.EFFORT)).isFalse();
    }

    @Test
    void effectiveIsEnteredElseCalculatedPerField() {
        RoadmapItem item = jira("High", 3, new RiceInputs(20, null, null, BigDecimal.ONE));
        RiceInputs calculated = RiceCalculation.calculated(item, 7);

        RiceInputs effective = RiceCalculation.effective(item, calculated);

        assertThat(effective).isEqualTo(new RiceInputs(20, BigDecimal.TWO, 100, BigDecimal.ONE));
        assertThat(RiceCalculation.isCalculated(item, RiceField.IMPACT)).isTrue();
        assertThat(RiceCalculation.isCalculated(item, RiceField.EFFORT)).isFalse();
    }
}
