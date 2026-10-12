package com.evision.releasemgmt.domain.promotion;

/** Constant names are the ProblemDetail reason codes the SPA keys on. */
public enum PromotionRejection {
    RELEASE_NOT_FROZEN, PROMOTION_STAGE_SKIPPED, PROMOTION_TARGET_RETIRED, PROMOTION_ROLLBACK_WITHOUT_SUCCESS,
    PROMOTION_ALREADY_SUPERSEDED, PROMOTION_DEPLOYED_AT_INVALID, PROMOTION_OUTCOME_NOT_RECORDABLE
}
