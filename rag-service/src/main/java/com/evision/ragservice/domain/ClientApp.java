package com.evision.ragservice.domain;

import java.time.Instant;
import java.util.UUID;

public record ClientApp(UUID id, String name, Instant createdAt, Instant revokedAt, Instant lastUsedAt) {
}
