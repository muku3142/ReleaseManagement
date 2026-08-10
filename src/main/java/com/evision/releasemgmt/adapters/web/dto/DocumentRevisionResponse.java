package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;

public record DocumentRevisionResponse(int revision, boolean latest, Instant generatedAt,
                                       String generatedBy) {
}
