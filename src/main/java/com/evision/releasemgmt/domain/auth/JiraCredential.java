package com.evision.releasemgmt.domain.auth;

import java.time.Instant;

public record JiraCredential(String accountId, String email, String apiToken, int keyVersion, Instant updatedAt) {
}
