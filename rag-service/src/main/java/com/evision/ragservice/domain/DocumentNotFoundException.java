package com.evision.ragservice.domain;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String externalId) {
        super("Document not found: " + externalId);
    }
}
