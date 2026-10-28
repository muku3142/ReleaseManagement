package com.evision.releasemgmt.domain.roadmap;

import static com.evision.releasemgmt.domain.roadmap.RoadmapRejection.ROADMAP_CONFIDENCE_INVALID;
import static com.evision.releasemgmt.domain.roadmap.RoadmapRejection.ROADMAP_EFFORT_INVALID;
import static com.evision.releasemgmt.domain.roadmap.RoadmapRejection.ROADMAP_IMPACT_INVALID;
import static com.evision.releasemgmt.domain.roadmap.RoadmapRejection.ROADMAP_REACH_INVALID;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record RiceInputs(Integer reach, BigDecimal impact, Integer confidence, BigDecimal effort) {

    public static final RiceInputs NONE = new RiceInputs(null, null, null, null);
    public static final int MAX_REACH = 1_000_000;
    public static final BigDecimal MAX_EFFORT = new BigDecimal("999999.99");
    public static final List<BigDecimal> IMPACTS =
            List.of(new BigDecimal("0.25"), new BigDecimal("0.5"), BigDecimal.ONE, BigDecimal.TWO, new BigDecimal("3"));
    public static final Set<Integer> CONFIDENCES = Set.of(50, 80, 100);

    public static RiceInputs validated(BigDecimal reach, BigDecimal impact, BigDecimal confidence, BigDecimal effort) {
        return new RiceInputs(
                validReach(reach), validImpact(impact), validConfidence(confidence), validEffort(effort));
    }

    private static Integer validReach(BigDecimal reach) {
        if (reach == null) {
            return null;
        }
        if (!isIntegral(reach) || reach.signum() < 0 || reach.compareTo(BigDecimal.valueOf(MAX_REACH)) > 0) {
            throw new RoadmapRejectedException(ROADMAP_REACH_INVALID);
        }
        return reach.intValueExact();
    }

    private static BigDecimal validImpact(BigDecimal impact) {
        if (impact == null) {
            return null;
        }
        return IMPACTS.stream()
                .filter(allowed -> allowed.compareTo(impact) == 0)
                .findFirst()
                .orElseThrow(() -> new RoadmapRejectedException(ROADMAP_IMPACT_INVALID));
    }

    private static Integer validConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return null;
        }
        if (!isIntegral(confidence)
                || confidence.compareTo(BigDecimal.valueOf(100)) > 0
                || confidence.signum() < 0
                || !CONFIDENCES.contains(confidence.intValueExact())) {
            throw new RoadmapRejectedException(ROADMAP_CONFIDENCE_INVALID);
        }
        return confidence.intValueExact();
    }

    private static BigDecimal validEffort(BigDecimal effort) {
        if (effort == null) {
            return null;
        }
        if (effort.signum() <= 0 || effort.stripTrailingZeros().scale() > 2 || effort.compareTo(MAX_EFFORT) > 0) {
            throw new RoadmapRejectedException(ROADMAP_EFFORT_INVALID);
        }
        return effort;
    }

    private static boolean isIntegral(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    public RiceInputs orElse(RiceInputs fallback) {
        return new RiceInputs(
                reach != null ? reach : fallback.reach(),
                impact != null ? impact : fallback.impact(),
                confidence != null ? confidence : fallback.confidence(),
                effort != null ? effort : fallback.effort());
    }

    public RiceInputs with(RiceField field, BigDecimal value) {
        return switch (field) {
            case REACH -> new RiceInputs(validReach(value), impact, confidence, effort);
            case IMPACT -> new RiceInputs(reach, validImpact(value), confidence, effort);
            case CONFIDENCE -> new RiceInputs(reach, impact, validConfidence(value), effort);
            case EFFORT -> new RiceInputs(reach, impact, confidence, validEffort(value));
        };
    }

    public boolean complete() {
        return missing().isEmpty();
    }

    public List<RiceField> missing() {
        List<RiceField> missing = new ArrayList<>();
        for (RiceField field : RiceField.values()) {
            if (valueOf(field) == null) {
                missing.add(field);
            }
        }
        return missing;
    }

    public BigDecimal valueOf(RiceField field) {
        return switch (field) {
            case REACH -> reach == null ? null : BigDecimal.valueOf(reach);
            case IMPACT -> impact;
            case CONFIDENCE -> confidence == null ? null : BigDecimal.valueOf(confidence);
            case EFFORT -> effort;
        };
    }

    public List<RiceField> changedFields(RiceInputs previous) {
        List<RiceField> changed = new ArrayList<>();
        for (RiceField field : RiceField.values()) {
            BigDecimal now = valueOf(field);
            BigDecimal before = previous.valueOf(field);
            boolean same = now == null ? before == null : before != null && now.compareTo(before) == 0;
            if (!same) {
                changed.add(field);
            }
        }
        return changed;
    }
}
