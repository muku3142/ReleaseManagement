package com.evision.releasemgmt.domain.assistant;

// The detail is always a fixed, content-free sentence chosen by the service, so it is safe to show and log.
public class InvalidAssistRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAssistRequestException(String detail) {
        super(detail);
    }
}
