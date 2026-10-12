package com.evision.releasemgmt.domain.promotion;

public class PromotionRejectedException extends RuntimeException {

    private final PromotionRejection reason;

    public PromotionRejectedException(PromotionRejection reason) {
        super(reason.name());
        this.reason = reason;
    }

    public PromotionRejection reason() {
        return reason;
    }
}
