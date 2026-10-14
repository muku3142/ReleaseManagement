package com.evision.ragservice.domain;

public class MixedEmbeddingModelsException extends RuntimeException {

    public MixedEmbeddingModelsException() {
        super("All collections in one request must use the same embedding model");
    }
}
