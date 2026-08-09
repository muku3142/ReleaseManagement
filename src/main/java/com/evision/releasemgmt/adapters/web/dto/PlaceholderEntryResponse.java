package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;

public record PlaceholderEntryResponse(String scope, String token, String kind, String description,
                                       List<String> tags) {
}
