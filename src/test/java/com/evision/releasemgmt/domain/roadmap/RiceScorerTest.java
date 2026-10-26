package com.evision.releasemgmt.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiceScorerTest {

    private static RiceInputs of(String r, String i, String c, String e) {
        return RiceInputs.validated(new BigDecimal(r), new BigDecimal(i), new BigDecimal(c), new BigDecimal(e));
    }

    @Test
    void computesReachImpactConfidenceOverEffort() {
        assertThat(RiceScorer.score(of("10", "2", "80", "4")))
                .hasValueSatisfying(s -> assertThat(s).isEqualByComparingTo("4"));
    }

    @Test
    void zeroReachIsScoredNotMissing() {
        assertThat(RiceScorer.score(of("0", "3", "100", "1")))
                .hasValueSatisfying(s -> assertThat(s).isEqualByComparingTo("0"));
    }

    @Test
    void nonTerminatingDivisionDoesNotThrow() {
        assertThat(RiceScorer.score(of("1", "1", "100", "3")))
                .hasValueSatisfying(s -> assertThat(s)
                        .isBetween(new BigDecimal("0.3333333333"), new BigDecimal("0.3333333334")));
    }

    @Test
    void missingInputYieldsEmpty() {
        assertThat(RiceScorer.score(RiceInputs.NONE)).isEmpty();
        assertThat(RiceScorer.score(RiceInputs.validated(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100"), null)))
                .isEmpty();
    }
}
