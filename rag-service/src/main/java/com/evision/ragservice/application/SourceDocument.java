package com.evision.ragservice.application;

import java.util.List;
import java.util.Map;

public record SourceDocument(int n, String documentId, String title, String url, String collection,
                             Map<String, Object> metadata, List<String> passages, double topScore) {
}
