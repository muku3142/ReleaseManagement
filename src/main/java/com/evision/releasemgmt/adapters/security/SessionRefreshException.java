package com.evision.releasemgmt.adapters.security;

public class SessionRefreshException extends RuntimeException {

    public enum Reason {
        INVALID_SESSION,
        SESSION_EXPIRED
    }

    private final Reason reason;

    public SessionRefreshException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
