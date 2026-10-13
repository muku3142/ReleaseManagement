package com.evision.ragservice.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentCollection(UUID id, UUID clientId, String name, String embeddingModel, Instant createdAt) {
}
