package com.evision.ragservice.domain;

public class GenerationFailedException extends RuntimeException {

    public GenerationFailedException(String message) {
        super(message);
    }

    public GenerationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
