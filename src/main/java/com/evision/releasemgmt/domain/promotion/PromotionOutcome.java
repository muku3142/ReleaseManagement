package com.evision.releasemgmt.domain.promotion;

/** VOIDED is reachable only through a correction (D-07-15). */
public enum PromotionOutcome {
    SUCCESS, FAILED, ROLLED_BACK, VOIDED
}
