package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RoadmapManualItemRequest(@NotBlank @Size(max = 500) String title, @NotNull UUID productId) {
}
