package com.evision.releasemgmt.domain.release;

public class UnfreezeRejectedException extends RuntimeException {

    private final UnfreezeRejection reason;

    public UnfreezeRejectedException(UnfreezeRejection reason) {
        super(reason.name());
        this.reason = reason;
    }

    public UnfreezeRejection reason() {
        return reason;
    }
}
