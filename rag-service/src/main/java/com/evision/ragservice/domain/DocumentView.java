package com.evision.ragservice.domain;

import java.time.Instant;
import java.util.Map;

public record DocumentView(String externalId, String title, String url, Map<String, Object> metadata,
                           DocumentStatus status, String statusReason, Instant indexedAt, Instant updatedAt) {
}
