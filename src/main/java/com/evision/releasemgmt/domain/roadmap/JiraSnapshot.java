package com.evision.releasemgmt.domain.roadmap;

import java.time.Instant;

public record JiraSnapshot(
        String jiraKey, String jiraIssueId, String title, String jiraStatus, Instant jiraUpdatedAt, Instant syncedAt) {}
