package com.evision.releasemgmt.domain.release;

import java.time.Instant;
import java.util.UUID;

public record ReleaseSummary(UUID id, String name, String version, ReleaseStatus status,
                             String jiraProjectKey, Instant createdAt, String createdBy,
                             Instant frozenAt, String frozenBy, Instant lastRefreshedAt,
                             int itemCount) {
}
