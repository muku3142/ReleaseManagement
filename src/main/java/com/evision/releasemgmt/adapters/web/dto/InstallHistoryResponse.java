package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstallHistoryResponse(UUID releaseId, boolean frozen, boolean inProduction, List<Product> products,
                                     List<Entry> entries) {

    public record Product(UUID id, String name, boolean archived) {}

    public record Entry(UUID id, UUID productId, String productName, UUID clientId, String clientName,
                        ClientDeploymentOutcome outcome, Instant installedAt, Instant recordedAt, String recordedBy,
                        String note, UUID supersedesId, UUID supersededById, String correctionReason,
                        boolean effective) {}
}
