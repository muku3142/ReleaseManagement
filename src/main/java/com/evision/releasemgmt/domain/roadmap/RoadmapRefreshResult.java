package com.evision.releasemgmt.domain.roadmap;

import java.util.List;
import java.util.UUID;

public record RoadmapRefreshResult(int refreshed, List<Unavailable> unavailable) {

    public record Unavailable(UUID itemId, String jiraKey) {}
}
