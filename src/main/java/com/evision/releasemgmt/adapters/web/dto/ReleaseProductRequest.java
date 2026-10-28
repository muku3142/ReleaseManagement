package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseProductRequest(@NotNull UUID productId) {
}
