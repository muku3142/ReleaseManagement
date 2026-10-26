package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Deliberately no actor field (D-07-12 pattern): who recorded it always comes from the signed-in principal.
public record RecordInstallRequest(
        @NotNull UUID productId,
        @NotEmpty @Size(max = 200) List<@NotNull UUID> clientIds,
        @NotNull ClientDeploymentOutcome outcome,
        Instant installedAt,
        @Size(max = 2000) String note) {
}
