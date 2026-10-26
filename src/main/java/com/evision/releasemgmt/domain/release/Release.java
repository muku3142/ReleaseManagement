package com.evision.releasemgmt.domain.release;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// D-05-09: itemsChangedAt is stamped by ReleaseRepositoryAdapter on every item mutation - it is
// what makes a ticket REMOVAL visible to DocumentReadinessService's staleness comparison, since
// last_refreshed_at only covers refreshes and max(added_at) cannot see a removal.
public record Release(UUID id, String name, String version, ReleaseStatus status,
                      String jiraProjectKey, Instant createdAt, String createdBy,
                      Instant frozenAt, String frozenBy, Instant lastRefreshedAt,
                      Instant itemsChangedAt, List<ReleaseItem> items, List<ManualItem> manualItems,
                      String packagesUrl) {

    public static Release create(String name, String version, String jiraProjectKey,
                                 String actor, Instant now) {
        return new Release(UUID.randomUUID(), name, version, ReleaseStatus.OPEN, jiraProjectKey,
                now, actor, null, null, null, null, List.of(), List.of(), null);
    }

    public boolean isFrozen() {
        return status == ReleaseStatus.FROZEN;
    }

    // D-03-10/D-03-11: a frozen release never accepts ticket changes directly; it must be unfrozen first (D-15.1-01). Every mutation - add, remove, refresh,
    // freeze, delete - funnels through this one guard rather than repeating the check.
    public void requireOpen() {
        if (isFrozen()) {
            throw new ReleaseFrozenException();
        }
    }
}
