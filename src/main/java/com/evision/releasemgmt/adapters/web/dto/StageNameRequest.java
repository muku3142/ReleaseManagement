package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StageNameRequest(@NotBlank @Size(max = 100) String name) {
}
