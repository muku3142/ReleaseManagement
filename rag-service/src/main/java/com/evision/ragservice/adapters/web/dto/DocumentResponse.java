package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.domain.DocumentStatus;
import com.evision.ragservice.domain.DocumentView;
import java.time.Instant;
import java.util.Map;

public record DocumentResponse(String id, String title, String url, Map<String, Object> metadata,
                               DocumentStatus status, String statusReason, Instant indexedAt, Instant updatedAt) {

    public static DocumentResponse from(DocumentView view) {
        return new DocumentResponse(view.externalId(), view.title(), view.url(), view.metadata(), view.status(),
                view.statusReason(), view.indexedAt(), view.updatedAt());
    }
}
