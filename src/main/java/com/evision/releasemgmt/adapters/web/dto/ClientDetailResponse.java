package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClientDetailResponse(UUID id, String name, boolean archived, List<Run> runs, List<Entry> history) {

    public record Run(UUID productId, String productName, boolean productArchived, boolean hasInstalls,
                      Current current) {
    }

    public record Current(UUID releaseId, String releaseName, String releaseVersion, Instant installedAt,
                          boolean inProduction) {
    }

    public record Entry(UUID id, UUID productId, String productName, UUID releaseId, String releaseName,
                        String releaseVersion, boolean inProduction, ClientDeploymentOutcome outcome,
                        Instant installedAt, Instant recordedAt, String recordedBy, String note, UUID supersedesId,
                        UUID supersededById, String correctionReason, boolean effective) {
    }
}
