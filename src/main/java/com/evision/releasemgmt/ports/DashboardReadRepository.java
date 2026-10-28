package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.dashboard.ChurnItem;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// D-08-08 read model. Read-only by construction: an update, delete, remove or save method here is a defect.
public interface DashboardReadRepository {

    /** Every release (newest created first) with its manual-item count, every stage incl. retired, every promotion. */
    DashboardLedger loadLedger();

    /** One row per Jira-backed item of the given releases; empty input returns an empty list without querying. */
    List<ChurnItem> findChurnItems(Collection<UUID> releaseIds);

    /** Effective, non-VOIDED promotions with from <= deployed_at < to; stageId null = all stages; productIds empty = all products. Newest first. */
    List<DeploymentRow> findDeployments(UUID stageId, Instant from, Instant to, int limit, int offset, Set<UUID> productIds);

    /** Distinct ids of releases covering any of the products; empty input returns an empty set without querying. */
    Set<UUID> releaseIdsCoveringAny(Collection<UUID> productIds);
}
