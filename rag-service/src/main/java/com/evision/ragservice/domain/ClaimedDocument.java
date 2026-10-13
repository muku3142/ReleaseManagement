package com.evision.ragservice.domain;

import java.util.UUID;

public record ClaimedDocument(UUID id, UUID collectionId, String embeddingModel, String title, String text,
                              byte[] contentHash) {
}
