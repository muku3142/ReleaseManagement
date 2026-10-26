package com.evision.releasemgmt.domain.roadmap;

import java.util.UUID;

// title is applied only to MANUAL items (D-17-09: a Jira item's title is its Jira summary); null for Jira items
public record RoadmapItemDetails(
        String title, RoadmapStatus status, UUID productId, String targetQuarter, String owner, String notes) {}
