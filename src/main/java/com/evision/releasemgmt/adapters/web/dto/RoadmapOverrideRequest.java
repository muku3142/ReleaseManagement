package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoadmapOverrideRequest(@NotNull BigDecimal value, @NotBlank @Size(max = 200) String reason) {
}
