package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

public record ClientCurrentRun(UUID clientId, UUID productId, UUID releaseId, Instant installedAt) {}
