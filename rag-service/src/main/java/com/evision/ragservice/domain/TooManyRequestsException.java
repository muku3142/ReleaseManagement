package com.evision.ragservice.domain;

public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException() {
        super("Too many generation requests in flight for this client");
    }
}
