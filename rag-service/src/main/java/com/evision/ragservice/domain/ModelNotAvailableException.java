package com.evision.ragservice.domain;

public class ModelNotAvailableException extends RuntimeException {

    public ModelNotAvailableException(String model) {
        super("Chat model not available: " + model);
    }
}
