package com.evision.releasemgmt.domain.roadmap;

import java.util.Set;
import java.util.UUID;

public record RoadmapFilter(Set<UUID> productIds, boolean includeDone, boolean includeArchived) {

    public boolean matches(RoadmapItem item) {
        if (item.archived() && !includeArchived) {
            return false;
        }
        if (item.status() == RoadmapStatus.DONE && !includeDone) {
            return false;
        }
        return productIds.isEmpty() || (item.productId() != null && productIds.contains(item.productId()));
    }
}
