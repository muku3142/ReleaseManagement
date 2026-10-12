package com.evision.releasemgmt.domain.promotion;

public class LadderEditRejectedException extends RuntimeException {

    private final LadderEditRejection reason;

    public LadderEditRejectedException(LadderEditRejection reason) {
        super(reason.name());
        this.reason = reason;
    }

    public LadderEditRejection reason() {
        return reason;
    }
}
