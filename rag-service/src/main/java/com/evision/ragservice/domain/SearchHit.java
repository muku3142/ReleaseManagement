package com.evision.ragservice.domain;

import java.util.Map;
import java.util.UUID;

public record SearchHit(UUID collectionId, String documentId, String title, String url, Map<String, Object> metadata,
                        int chunkIndex, String text, double score) {
}
