package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

public record CurrentRun(UUID productId, UUID releaseId, String releaseName, String releaseVersion, Instant installedAt) {}
