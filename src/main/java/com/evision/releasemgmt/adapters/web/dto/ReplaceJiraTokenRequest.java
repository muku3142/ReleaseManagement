package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReplaceJiraTokenRequest(@NotBlank String apiToken) {
}
