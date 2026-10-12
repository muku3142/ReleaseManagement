package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AdoCredentialResponse(boolean configured, String organization, LocalDate expiresOn, String state,
                                    Instant updatedAt, String updatedBy) {
}
