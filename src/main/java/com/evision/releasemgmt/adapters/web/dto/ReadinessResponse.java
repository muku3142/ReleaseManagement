package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;

public record ReadinessResponse(List<ReadinessEntryResponse> entries) {
}
