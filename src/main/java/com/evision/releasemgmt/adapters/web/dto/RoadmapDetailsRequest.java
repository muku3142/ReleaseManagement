package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RoadmapDetailsRequest(
        @Size(max = 500) String title,
        @NotNull RoadmapStatus status,
        UUID productId,
        @Size(max = 20) String targetQuarter,
        @Size(max = 100) String owner,
        @Size(max = 4000) String notes) {
}
