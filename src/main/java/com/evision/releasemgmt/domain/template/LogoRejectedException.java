package com.evision.releasemgmt.domain.template;

public class LogoRejectedException extends RuntimeException {

    private final LogoRejectionReason reason;

    public LogoRejectedException(LogoRejectionReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public LogoRejectionReason reason() {
        return reason;
    }
}
