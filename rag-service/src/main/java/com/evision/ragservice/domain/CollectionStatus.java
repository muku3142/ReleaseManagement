package com.evision.ragservice.domain;

import java.time.Instant;

public record CollectionStatus(String name, String embeddingModel, long pending, long indexed, long failed,
                               Instant lastIndexedAt) {
}
