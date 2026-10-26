package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CorrectInstallRequest(
        @NotNull ClientDeploymentOutcome outcome,
        @NotNull Instant installedAt,
        @Size(max = 2000) String note,
        @NotBlank @Size(max = 2000) String reason) {
}
