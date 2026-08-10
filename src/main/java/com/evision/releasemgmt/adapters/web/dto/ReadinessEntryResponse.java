package com.evision.releasemgmt.adapters.web.dto;

import java.util.UUID;

public record ReadinessEntryResponse(UUID releaseId, String documentType, String state) {
}
