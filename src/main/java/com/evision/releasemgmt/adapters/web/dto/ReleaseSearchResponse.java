package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;

public record ReleaseSearchResponse(List<ReleaseSummaryResponse> releases) {
}
