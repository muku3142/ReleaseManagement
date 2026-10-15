package com.evision.releasemgmt.domain.assistant;

// The message carries metadata only (operation, collection, id, status), never document text.
public class RagServiceException extends RuntimeException {

    private final int status;

    public RagServiceException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
