package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;

public record PlaceholderCatalogResponse(List<PlaceholderEntryResponse> entries,
                                         LogoLimitsResponse logoLimits) {
}
