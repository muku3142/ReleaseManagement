package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

// Deliberately no actor field (D-07-12): who corrected it always comes from the signed-in principal.
public record CorrectPromotionRequest(
        @NotNull UUID stageId,
        @NotNull PromotionOutcome outcome,
        @NotNull Instant deployedAt,
        @Size(max = 2000) String note,
        @NotBlank @Size(max = 2000) String reason) {
}
