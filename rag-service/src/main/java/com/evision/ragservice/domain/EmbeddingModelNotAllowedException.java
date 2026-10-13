package com.evision.ragservice.domain;

public class EmbeddingModelNotAllowedException extends RuntimeException {

    public EmbeddingModelNotAllowedException(String model) {
        super("Embedding model not allowed: " + model);
    }
}
