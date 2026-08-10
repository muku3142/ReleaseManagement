package com.evision.releasemgmt.domain.release;

import java.time.Instant;
import java.util.UUID;

// D-05-09: itemsChangedAt travels alongside lastRefreshedAt so DocumentReadinessService.all() can
// evaluate the whole grid from summaries alone, without a second per-release load of the full
// Release aggregate.
public record ReleaseSummary(UUID id, String name, String version, ReleaseStatus status,
                             String jiraProjectKey, Instant createdAt, String createdBy,
                             Instant frozenAt, String frozenBy, Instant lastRefreshedAt,
                             Instant itemsChangedAt, int itemCount) {
}
