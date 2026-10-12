package com.evision.releasemgmt.domain.ado;

import java.time.Instant;
import java.time.LocalDate;

public record AdoCredentialStatus(String organization, LocalDate expiresOn, Instant updatedAt, String updatedBy) {
}
