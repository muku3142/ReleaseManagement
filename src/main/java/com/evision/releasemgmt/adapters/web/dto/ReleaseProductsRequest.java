package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReleaseProductsRequest(@NotNull @Size(max = 100) List<@NotNull UUID> productIds) {
}
