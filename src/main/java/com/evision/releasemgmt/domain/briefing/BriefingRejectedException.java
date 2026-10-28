package com.evision.releasemgmt.domain.briefing;

public class BriefingRejectedException extends RuntimeException {

    private final BriefingRejection reason;

    public BriefingRejectedException(BriefingRejection reason) {
        super(reason.name());
        this.reason = reason;
    }

    public BriefingRejection reason() {
        return reason;
    }
}
