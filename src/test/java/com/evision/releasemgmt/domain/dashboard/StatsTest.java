package com.evision.releasemgmt.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatsTest {

    @Test
    void medianOfNothingIsEmpty() {
        assertThat(Stats.median(List.of())).isEmpty();
    }

    @Test
    void medianOfOneIsThatValue() {
        assertThat(Stats.median(List.of(5L)).getAsDouble()).isEqualTo(5.0);
    }

    @Test
    void medianOfEvenCountAveragesMiddlePair() {
        assertThat(Stats.median(List.of(1L, 3L)).getAsDouble()).isEqualTo(2.0);
    }

    @Test
    void medianSortsItsInput() {
        assertThat(Stats.median(List.of(3L, 1L, 2L)).getAsDouble()).isEqualTo(2.0);
    }

    @Test
    void quantileInterpolatesLinearly() {
        List<Long> values = List.of(1L, 2L, 3L, 4L, 100L);
        assertThat(Stats.quantile(values, 0.25).getAsDouble()).isCloseTo(2.0, within(1e-9));
        assertThat(Stats.quantile(values, 0.75).getAsDouble()).isCloseTo(4.0, within(1e-9));
        assertThat(Stats.quantile(List.of(1L, 1L, 1L, 50L), 0.75).getAsDouble()).isCloseTo(13.25, within(1e-9));
    }
}
