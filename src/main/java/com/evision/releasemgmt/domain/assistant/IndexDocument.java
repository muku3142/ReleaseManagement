package com.evision.releasemgmt.domain.assistant;

import java.util.Map;

public record IndexDocument(IndexCollection collection, String id, String title, String text,
                            Map<String, Object> metadata) {

    public DocRef ref() {
        return new DocRef(collection, id);
    }

    @Override
    public String toString() {
        return "IndexDocument[" + collection + ", " + id + "]";
    }
}
