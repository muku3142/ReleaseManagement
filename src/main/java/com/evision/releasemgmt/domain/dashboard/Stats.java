package com.evision.releasemgmt.domain.dashboard;

import java.util.List;
import java.util.OptionalDouble;

public final class Stats {

    private Stats() {
    }

    public static OptionalDouble median(List<Long> values) {
        return quantile(values, 0.5);
    }

    // Linear interpolation between closest ranks (the same definition as PostgreSQL percentile_cont).
    public static OptionalDouble quantile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        List<Long> sorted = values.stream().sorted().toList();
        double position = p * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(lower + 1, sorted.size() - 1);
        return OptionalDouble.of(sorted.get(lower) + (position - lower) * (sorted.get(upper) - sorted.get(lower)));
    }
}
