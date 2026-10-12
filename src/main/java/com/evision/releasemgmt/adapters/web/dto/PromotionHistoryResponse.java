package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;
import java.util.UUID;

public record PromotionHistoryResponse(List<StageResponse> stages, UUID currentStageId, UUID nextStageId,
                                       List<PromotionEntryResponse> entries) {
}
