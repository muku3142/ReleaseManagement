package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

// PORT-05: append-only by construction. There is deliberately no update, delete, remove or save here -
// a mutation method on this port is a defect, because the audit guarantee rests on there being no way
// to alter a stored entry. Corrections are new entries that supersede old ones.
public interface ClientDeploymentRepository {

    /**
     * The release's products, linked clients and every install entry, oldest recorded first.
     *
     * @throws com.evision.releasemgmt.domain.release.ReleaseNotFoundException when the release does not exist
     */
    ClientDeploymentHistory load(UUID releaseId);

    /** Locks the release row, builds its history, applies decide, inserts the results; one transaction. Insert only. */
    List<ClientDeployment> appendUnderLock(UUID releaseId, Function<ClientDeploymentHistory, List<ClientDeployment>> decide);

    /** Every row of the client, superseded and voided included. */
    List<ClientInstall> historyOfClient(UUID clientId);

    /** From current_client_release. */
    List<CurrentRun> currentRunsOfClient(UUID clientId);
}
