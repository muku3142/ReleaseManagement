package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.ClientCurrentRun;
import com.evision.releasemgmt.domain.portfolio.RecentInstall;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Phase 16 portfolio read model. Read-only by construction: an update, delete, remove or save method here is a defect.
public interface PortfolioReadRepository {

    /** Every row of current_client_release (D-15-05 lives only in that view). */
    List<ClientCurrentRun> currentRuns();

    /** Non-archived clients per product whose current release (current_client_release) exists; products with none are absent. */
    Map<UUID, Integer> runningClientCounts();

    /** Product ids per release from release_product; releases without products are absent. */
    Map<UUID, Set<UUID>> productsByRelease();

    /** Effective SUCCESS installs (effective_client_deployment) with installed_at >= since. */
    List<RecentInstall> successfulInstallsSince(Instant since);
}
