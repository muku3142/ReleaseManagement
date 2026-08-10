package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateResponse(UUID id, String name, String documentType, String body, boolean active,
                               ChromeFieldsResponse chrome, String groupingDimension,
                               LogoMetadataResponse logo, List<String> warnings, Instant createdAt,
                               String createdBy, Instant updatedAt, String updatedBy) {
}
