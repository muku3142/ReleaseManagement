package com.evision.ragservice.domain;

public class InvalidContentException extends RuntimeException {

    public InvalidContentException(String message) {
        super(message);
    }
}
