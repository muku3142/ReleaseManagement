package com.evision.releasemgmt.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiceInputsTest {

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static RiceInputs of(String r, String i, String c, String e) {
        return RiceInputs.validated(bd(r), bd(i), bd(c), bd(e));
    }

    private static void rejects(String r, String i, String c, String e, RoadmapRejection reason) {
        assertThatThrownBy(() -> of(r, i, c, e))
                .isInstanceOfSatisfying(RoadmapRejectedException.class, x -> assertThat(x.reason()).isEqualTo(reason));
    }

    @Test
    void rejectsInvalidReach() {
        rejects("-1", null, null, null, RoadmapRejection.ROADMAP_REACH_INVALID);
        rejects("2.5", null, null, null, RoadmapRejection.ROADMAP_REACH_INVALID);
        rejects("1000001", null, null, null, RoadmapRejection.ROADMAP_REACH_INVALID);
    }

    @Test
    void acceptsValidReach() {
        assertThat(of("0", null, null, null).reach()).isEqualTo(0);
        assertThat(of("10.00", null, null, null).reach()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidImpactAndCanonicalisesValid() {
        rejects(null, "0.75", null, null, RoadmapRejection.ROADMAP_IMPACT_INVALID);
        assertThat(of(null, "0.50", null, null).impact()).isEqualByComparingTo("0.5");
        assertThat(of(null, "0.50", null, null).impact()).isSameAs(RiceInputs.IMPACTS.get(1));
    }

    @Test
    void rejectsInvalidConfidence() {
        rejects(null, null, "70", null, RoadmapRejection.ROADMAP_CONFIDENCE_INVALID);
        rejects(null, null, "80.5", null, RoadmapRejection.ROADMAP_CONFIDENCE_INVALID);
        for (String c : List.of("50", "80", "100")) {
            assertThat(of(null, null, c, null).confidence()).isEqualTo(Integer.valueOf(c));
        }
    }

    @Test
    void rejectsInvalidEffort() {
        rejects(null, null, null, "0", RoadmapRejection.ROADMAP_EFFORT_INVALID);
        rejects(null, null, null, "-1", RoadmapRejection.ROADMAP_EFFORT_INVALID);
        rejects(null, null, null, "0.125", RoadmapRejection.ROADMAP_EFFORT_INVALID);
        rejects(null, null, null, "1000000", RoadmapRejection.ROADMAP_EFFORT_INVALID);
        assertThat(of(null, null, null, "0.5").effort()).isEqualByComparingTo("0.5");
    }

    @Test
    void allNullIsValidButIncomplete() {
        RiceInputs none = of(null, null, null, null);
        assertThat(none.complete()).isFalse();
        assertThat(none.missing()).containsExactly(RiceField.REACH, RiceField.IMPACT, RiceField.CONFIDENCE, RiceField.EFFORT);
        assertThat(of("1", "1", "100", "1").complete()).isTrue();
    }

    @Test
    void changedFieldsUsesNumericComparisonInFieldOrder() {
        RiceInputs previous = of("10", "2.0", "80", null);
        assertThat(of("10", "2.00", "80", null).changedFields(previous)).isEmpty();
        assertThat(of("11", "2", "50", "4").changedFields(previous))
                .containsExactly(RiceField.REACH, RiceField.CONFIDENCE, RiceField.EFFORT);
        assertThat(of(null, "2", "80", null).changedFields(previous)).containsExactly(RiceField.REACH);
    }
}
