package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StageOrderRequest(@NotNull @Size(max = 50) List<@NotNull UUID> stageIds) {
}
