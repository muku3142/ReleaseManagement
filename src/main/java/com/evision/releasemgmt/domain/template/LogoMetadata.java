package com.evision.releasemgmt.domain.template;

import java.time.Instant;

// What the API and the aggregate carry. The bytes deliberately are NOT here - a template read must
// never pull an image out of the database (D-04-16 keeps rows small; this keeps reads small too).
public record LogoMetadata(String contentType, String filename, int byteSize,
                           int width, int height, Instant uploadedAt) {
}
