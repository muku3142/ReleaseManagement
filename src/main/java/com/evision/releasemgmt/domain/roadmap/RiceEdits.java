package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// The only place that decides what a user edit writes: the next inputs, override reasons and history rows.
public final class RiceEdits {

    private static final List<RiceField> ENTERABLE = List.of(RiceField.IMPACT, RiceField.CONFIDENCE, RiceField.EFFORT);

    private RiceEdits() {}

    public static InputUpdate entered(RoadmapItem current, RiceInputs requested, String actor, Instant at) {
        requireOpen(current);
        RiceInputs next = current.inputs();
        for (RiceField field : ENTERABLE) {
            BigDecimal value = requested.valueOf(field);
            if (RiceCalculation.isCalculated(current, field)) {
                if (value != null) {
                    throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_FIELD_CALCULATED);
                }
                continue;
            }
            next = next.with(field, value);
        }
        List<InputChange> changes = new ArrayList<>();
        for (RiceField field : next.changedFields(current.inputs())) {
            changes.add(new InputChange(current.id(), field, current.inputs().valueOf(field), next.valueOf(field), at,
                    actor, InputChangeKind.ENTERED, null));
        }
        return new InputUpdate(next, current.overrides(), changes);
    }

    public static InputUpdate override(RoadmapItem current, RiceInputs calculated, RiceField field, BigDecimal value,
                                       String reason, String actor, Instant at) {
        requireOpen(current);
        requireCalculated(current, field);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
        RiceInputs next = current.inputs().with(field, value);
        BigDecimal before = current.inputs().valueOf(field);
        if (before != null && before.compareTo(value) == 0 && reason.equals(current.overrides().reasonFor(field))) {
            return unchanged(current);
        }
        BigDecimal effectiveBefore = RiceCalculation.effective(current, calculated).valueOf(field);
        return new InputUpdate(next, current.overrides().with(field, reason), List.of(new InputChange(current.id(),
                field, effectiveBefore, next.valueOf(field), at, actor, InputChangeKind.OVERRIDDEN, reason)));
    }

    public static InputUpdate reset(RoadmapItem current, RiceInputs calculated, RiceField field, String actor,
                                    Instant at) {
        requireOpen(current);
        requireCalculated(current, field);
        if (current.overrides().reasonFor(field) == null) {
            return unchanged(current);
        }
        return new InputUpdate(current.inputs().with(field, null), current.overrides().with(field, null),
                List.of(new InputChange(current.id(), field, current.inputs().valueOf(field),
                        calculated.valueOf(field), at, actor, InputChangeKind.RESET, null)));
    }

    private static InputUpdate unchanged(RoadmapItem current) {
        return new InputUpdate(current.inputs(), current.overrides(), List.of());
    }

    private static void requireOpen(RoadmapItem current) {
        if (current.archived()) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_ITEM_ARCHIVED);
        }
    }

    private static void requireCalculated(RoadmapItem current, RiceField field) {
        if (!RiceCalculation.isCalculated(current, field)) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_FIELD_NOT_CALCULATED);
        }
    }
}
