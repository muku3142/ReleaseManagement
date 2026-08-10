package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;

public record DocumentRevisionListResponse(List<DocumentRevisionResponse> revisions) {
}
