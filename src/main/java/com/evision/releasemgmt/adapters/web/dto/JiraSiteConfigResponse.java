package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;

public record JiraSiteConfigResponse(String baseUrl, Instant updatedAt, String updatedBy) {
}
