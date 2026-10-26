package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceScorer;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemSource;
import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
        Instant createdAt,
        boolean archived) {

    public record ProductRef(UUID id, String name, boolean archived) {
    }

    public static RoadmapItemResponse of(RoadmapItem item, BigDecimal score, Integer rank,
                                         Map<UUID, Product> products) {
        Product product = item.productId() == null ? null : products.get(item.productId());
        return new RoadmapItemResponse(item.id(), item.source(), item.jiraKey(), item.title(), item.jiraStatus(),
                item.jiraSyncedAt(), item.jiraUnavailable(),
                product == null ? null : new ProductRef(product.id(), product.name(), product.archived()),
                item.status(), item.targetQuarter(), item.owner(), item.notes(),
                item.inputs().reach(), plain(item.inputs().impact()), item.inputs().confidence(),
                plain(item.inputs().effort()), scoreDisplay(score), rank, item.inputs().missing(),
                item.createdAt(), item.archived());
    }

    public static RoadmapItemResponse unranked(RoadmapItem item, Map<UUID, Product> products) {
        return of(item, RiceScorer.score(item.inputs()).orElse(null), null, products);
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
