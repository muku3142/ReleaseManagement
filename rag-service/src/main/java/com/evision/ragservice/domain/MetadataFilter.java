package com.evision.ragservice.domain;

import java.util.List;

public record MetadataFilter(List<Clause> clauses) {

    // anyOf.size() == 1 is equality, more than one is an in-list
    public record Clause(String key, List<Object> anyOf) {
    }

    public static MetadataFilter none() {
        return new MetadataFilter(List.of());
    }
}
