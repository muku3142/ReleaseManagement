package com.evision.releasemgmt.domain.roadmap;

import java.util.Set;
import java.util.UUID;

/** Empty productIds means all products. */
public record RoadmapFilter(Set<UUID> productIds, boolean includeDone, boolean includeArchived) {
}
