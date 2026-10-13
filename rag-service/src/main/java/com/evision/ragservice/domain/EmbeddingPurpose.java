package com.evision.ragservice.domain;

// Callers state intent; the embedding adapter maps it to its model's convention (nomic prefix, Cohere input_type, ...)
public enum EmbeddingPurpose {
    DOCUMENT, QUERY
}
