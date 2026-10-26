package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReleaseSummaryResponse(UUID id, String name, String version, String status,
                                     String jiraProjectKey, Instant createdAt, String createdBy,
                                     Instant frozenAt, String frozenBy, Instant lastRefreshedAt,
                                     int itemCount, List<ReleaseProductResponse> products) {
}
