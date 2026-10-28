package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

/** installedAt null means "now". */
public record ClientDeploymentDraft(UUID productId, ClientDeploymentOutcome outcome, Instant installedAt, String note) {}
