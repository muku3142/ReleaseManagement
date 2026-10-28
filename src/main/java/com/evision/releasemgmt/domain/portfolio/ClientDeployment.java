package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

public record ClientDeployment(UUID id, UUID releaseId, UUID productId, UUID clientId, ClientDeploymentOutcome outcome,
        Instant installedAt, Instant recordedAt, String recordedBy, String note, UUID supersedesId, String correctionReason) {}
