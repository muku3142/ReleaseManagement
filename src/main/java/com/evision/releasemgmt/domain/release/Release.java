package com.evision.releasemgmt.domain.release;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Release(UUID id, String name, String version, ReleaseStatus status,
                      String jiraProjectKey, Instant createdAt, String createdBy,
                      Instant frozenAt, String frozenBy, Instant lastRefreshedAt,
                      List<ReleaseItem> items, List<ManualItem> manualItems) {

    public static Release create(String name, String version, String jiraProjectKey,
                                 String actor, Instant now) {
        return new Release(UUID.randomUUID(), name, version, ReleaseStatus.OPEN, jiraProjectKey,
                now, actor, null, null, null, List.of(), List.of());
    }

    public boolean isFrozen() {
        return status == ReleaseStatus.FROZEN;
    }

    // D-03-10/D-03-11: freeze is a one-way door, so every mutation - add, remove, refresh,
    // freeze, delete - funnels through this one guard rather than repeating the check.
    public void requireOpen() {
        if (isFrozen()) {
            throw new ReleaseFrozenException();
        }
    }
}
