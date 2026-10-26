package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

public record FrozenRelease(UUID id, String name, String version, Instant frozenAt) {}
