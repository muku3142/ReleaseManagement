package com.evision.ragservice.domain;

public class CollectionModelConflictException extends RuntimeException {

    public CollectionModelConflictException(String collectionName) {
        super("Collection already exists with a different embedding model: " + collectionName);
    }
}
