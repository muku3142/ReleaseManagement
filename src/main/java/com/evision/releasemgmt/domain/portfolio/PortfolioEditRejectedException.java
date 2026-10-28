package com.evision.releasemgmt.domain.portfolio;

public class PortfolioEditRejectedException extends RuntimeException {

    private final PortfolioEditRejection reason;

    public PortfolioEditRejectedException(PortfolioEditRejection reason) {
        super(reason.name());
        this.reason = reason;
    }

    public PortfolioEditRejection reason() {
        return reason;
    }
}
