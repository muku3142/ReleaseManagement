package com.evision.ragservice.domain;

public class CollectionNotFoundException extends RuntimeException {

    public CollectionNotFoundException(String collectionName) {
        super("Collection not found: " + collectionName);
    }
}
