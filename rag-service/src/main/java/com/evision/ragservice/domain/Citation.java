package com.evision.ragservice.domain;

import java.util.Map;

public record Citation(int n, String documentId, String title, String url, String collection,
                       Map<String, Object> metadata) {
}
