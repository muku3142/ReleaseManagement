package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;

public record LogoMetadataResponse(String contentType, String filename, int byteSize,
                                   int width, int height, Instant uploadedAt) {
}
