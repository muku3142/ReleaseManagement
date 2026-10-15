package com.evision.releasemgmt.domain.assistant;

import java.util.Map;

public record Source(int n, String collection, String documentId, String title, String url, String kind,
                     Map<String, Object> metadata) {
}
