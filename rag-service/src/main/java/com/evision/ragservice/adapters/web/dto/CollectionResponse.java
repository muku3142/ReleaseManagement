package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.domain.CollectionStatus;
import java.time.Instant;

public record CollectionResponse(String name, String embeddingModel, long pending, long indexed, long failed,
                                 Instant lastIndexedAt) {

    public static CollectionResponse from(CollectionStatus status) {
        return new CollectionResponse(status.name(), status.embeddingModel(), status.pending(), status.indexed(),
                status.failed(), status.lastIndexedAt());
    }
}
