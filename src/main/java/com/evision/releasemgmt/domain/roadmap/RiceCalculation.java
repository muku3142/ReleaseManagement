package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public final class RiceCalculation {

    private static final Map<String, BigDecimal> IMPACT_BY_PRIORITY = Map.of(
            "highest", new BigDecimal("3"),
            "blocker", new BigDecimal("3"),
            "high", BigDecimal.TWO,
            "medium", BigDecimal.ONE,
            "low", new BigDecimal("0.5"),
            "lowest", new BigDecimal("0.25"));

    private RiceCalculation() {}

    public static BigDecimal impactForPriority(String priority) {
        return priority == null ? null : IMPACT_BY_PRIORITY.get(priority.strip().toLowerCase(Locale.ROOT));
    }

    public static Integer confidenceForSignals(Integer readinessSignals) {
        if (readinessSignals == null) {
            return null;
        }
        return switch (readinessSignals) {
            case 3 -> 100;
            case 2 -> 80;
            default -> 50;
        };
    }

    public static RiceInputs calculated(RoadmapItem item, int runningClients) {
        return new RiceInputs(runningClients,
                item.isJira() ? impactForPriority(item.jiraPriority()) : null,
                item.isJira() ? confidenceForSignals(item.jiraReadiness()) : null,
                null);
    }

    public static RiceInputs effective(RoadmapItem item, RiceInputs calculated) {
        return item.inputs().orElse(calculated);
    }

    public static boolean isCalculated(RoadmapItem item, RiceField field) {
        return switch (field) {
            case REACH -> true;
            case IMPACT, CONFIDENCE -> item.isJira();
            case EFFORT -> false;
        };
    }
}
