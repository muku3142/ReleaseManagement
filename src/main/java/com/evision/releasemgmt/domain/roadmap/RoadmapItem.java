package com.evision.releasemgmt.domain.roadmap;

import java.time.Instant;
import java.util.UUID;

public record RoadmapItem(
        UUID id,
        RoadmapItemSource source,
        String jiraKey,
        String jiraIssueId,
        String title,
        String jiraStatus,
        Instant jiraUpdatedAt,
        Instant jiraSyncedAt,
        Instant jiraUnavailableSince,
        UUID productId,
        RoadmapStatus status,
        String notes,
        String targetQuarter,
        String owner,
        RiceInputs inputs,
        Instant createdAt,
        String createdBy,
        Instant archivedAt) {

    public static RoadmapItem manual(String title, UUID productId, String actor, Instant now) {
        return new RoadmapItem(UUID.randomUUID(), RoadmapItemSource.MANUAL, null, null, title, null, null, null, null,
                productId, RoadmapStatus.IDEA, null, null, null, RiceInputs.NONE, now, actor, null);
    }

    public static RoadmapItem fromJira(JiraSnapshot snapshot, String actor, Instant now) {
        return new RoadmapItem(UUID.randomUUID(), RoadmapItemSource.JIRA, snapshot.jiraKey(), snapshot.jiraIssueId(),
                snapshot.title(), snapshot.jiraStatus(), snapshot.jiraUpdatedAt(), snapshot.syncedAt(), null,
                null, RoadmapStatus.IDEA, null, null, null, RiceInputs.NONE, now, actor, null);
    }

    public boolean archived() {
        return archivedAt != null;
    }

    public boolean jiraUnavailable() {
        return jiraUnavailableSince != null;
    }

    public boolean isJira() {
        return source == RoadmapItemSource.JIRA;
    }
}
