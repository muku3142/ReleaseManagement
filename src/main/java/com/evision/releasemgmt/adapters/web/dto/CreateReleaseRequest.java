package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReleaseRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String version,
        @Size(max = 64) String jiraProjectKey) {
}
