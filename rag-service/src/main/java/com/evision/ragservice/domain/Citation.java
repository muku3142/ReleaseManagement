package com.evision.ragservice.domain;

import java.util.Map;

public record Citation(int n, String documentId, String title, String url, String collection,
                       Map<String, Object> metadata, CitationKind kind) {

    public Citation(int n, String documentId, String title, String url, String collection,
                    Map<String, Object> metadata) {
        this(n, documentId, title, url, collection, metadata, CitationKind.PASSAGE);
    }
}
