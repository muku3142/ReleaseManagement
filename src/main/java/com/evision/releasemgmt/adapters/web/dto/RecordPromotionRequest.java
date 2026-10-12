package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

// Deliberately no actor field (D-07-12): who recorded it always comes from the signed-in principal.
public record RecordPromotionRequest(
        @NotNull UUID stageId,
        @NotNull PromotionOutcome outcome,
        Instant deployedAt,
        @Size(max = 2000) String note) {
}
