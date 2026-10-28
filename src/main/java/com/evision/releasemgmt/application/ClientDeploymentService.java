package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ClientDeploymentService {

    private final ClientDeploymentRepository deployments;
    private final PromotionRepository promotions;
    private final Clock clock;

    public ClientDeploymentService(ClientDeploymentRepository deployments, PromotionRepository promotions, Clock clock) {
        this.deployments = deployments;
        this.promotions = promotions;
        this.clock = clock;
    }

    public ClientDeploymentHistory history(UUID releaseId) {
        return deployments.load(releaseId);
    }

    // PORT-04: "not in Production" is derived at read time from the promotion ledger, never stored on an install.
    public boolean reachedProduction(UUID releaseId) {
        return promotions.load(releaseId).firstProductionSuccess().isPresent();
    }

    public ClientDeploymentHistory record(UUID releaseId, ClientDeploymentDraft draft, List<UUID> clientIds, String actor) {
        Instant now = clock.instant();
        deployments.appendUnderLock(releaseId, history -> history.record(draft, clientIds, actor, now));
        return deployments.load(releaseId);
    }

    public ClientDeploymentHistory correct(UUID releaseId, UUID installId, ClientDeploymentOutcome outcome,
                                           Instant installedAt, String note, String reason, String actor) {
        Instant now = clock.instant();
        deployments.appendUnderLock(releaseId, history ->
                List.of(history.correct(installId, outcome, installedAt, note, reason, actor, now)));
        return deployments.load(releaseId);
    }
}
