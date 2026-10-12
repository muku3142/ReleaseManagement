package com.evision.releasemgmt.domain.ado;

public class AdoAccessException extends RuntimeException {

    private final AdoFailureReason reason;

    // The message is the reason name only, never the token, header or URL.
    public AdoAccessException(AdoFailureReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public AdoFailureReason reason() {
        return reason;
    }
}
