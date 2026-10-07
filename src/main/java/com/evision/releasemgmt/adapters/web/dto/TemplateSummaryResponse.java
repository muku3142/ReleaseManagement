package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateSummaryResponse(UUID id, String name, String documentType, boolean active, boolean myDefault,
                                      boolean hasLogo, int warningCount, Instant updatedAt) {
}
