package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotNull;

public record BriefingFallbackRequest(@NotNull Boolean enabled) { }
