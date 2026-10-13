package com.evision.ragservice.domain;

import java.util.Map;

public record SearchHit(String documentId, String title, String url, Map<String, Object> metadata,
                        int chunkIndex, String text, double score) {
}
