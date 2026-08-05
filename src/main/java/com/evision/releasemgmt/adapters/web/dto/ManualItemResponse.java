package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ManualItemResponse(UUID id, String title, String description, String category,
                                 Instant addedAt) {
}
