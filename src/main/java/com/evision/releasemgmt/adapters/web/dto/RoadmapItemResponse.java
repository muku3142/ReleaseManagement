package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RiceCalculation;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemSource;
import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoadmapItemResponse(
        UUID id,
        RoadmapItemSource source,
        String jiraKey,
        String title,
        String jiraStatus,
        Instant jiraSyncedAt,
        boolean jiraUnavailable,
        ProductRef product,
        RoadmapStatus status,
        String targetQuarter,
        String owner,
        String notes,
        Integer reach,
        BigDecimal impact,
        Integer confidence,
        BigDecimal effort,
        String scoreDisplay,
        Integer rank,
        List<RiceField> missing,
        RiceValues calculated,
        Overrides overrides,
        List<RiceField> calculatedFields,
        String jiraPriority,
        Integer readinessSignals,
        Instant createdAt,
        boolean archived) {

    public record ProductRef(UUID id, String name, boolean archived) {
    }

    public record RiceValues(Integer reach, BigDecimal impact, Integer confidence) {
    }

    public record Overrides(String reach, String impact, String confidence) {
    }

    // reach/impact/confidence/effort are the effective values (override if set, else calculated).
    public static RoadmapItemResponse of(RankedItem ranked, Map<UUID, Product> products) {
        RoadmapItem item = ranked.item();
        RiceInputs effective = ranked.effective();
        RiceInputs calculated = ranked.calculated();
        Product product = item.productId() == null ? null : products.get(item.productId());
        List<RiceField> calculatedFields = Arrays.stream(RiceField.values())
                .filter(field -> RiceCalculation.isCalculated(item, field)).toList();
        return new RoadmapItemResponse(item.id(), item.source(), item.jiraKey(), item.title(), item.jiraStatus(),
                item.jiraSyncedAt(), item.jiraUnavailable(),
                product == null ? null : new ProductRef(product.id(), product.name(), product.archived()),
                item.status(), item.targetQuarter(), item.owner(), item.notes(),
                effective.reach(), plain(effective.impact()), effective.confidence(),
                plain(effective.effort()), scoreDisplay(ranked.score()), ranked.rank(), effective.missing(),
                new RiceValues(calculated.reach(), plain(calculated.impact()), calculated.confidence()),
                new Overrides(item.overrides().reach(), item.overrides().impact(), item.overrides().confidence()),
                calculatedFields, item.jiraPriority(), item.jiraReadiness(), item.createdAt(), item.archived());
    }

    // D-17-17: one-decimal HALF_UP is applied here, for display only.
    public static String scoreDisplay(BigDecimal score) {
        return score == null ? null : score.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    // stripTrailingZeros alone turns 10 into 1E+1, which is not a plain JSON number.
    private static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
