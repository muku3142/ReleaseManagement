package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.UUID;

// The stored template's full body text is deliberately not exposed here: it is provenance held for
// the audit trail, not something any screen renders, and shipping a second full body on every
// document read would double the payload for nothing.
public record DocumentResponse(UUID id, UUID releaseId, String documentType, int revision,
                               int latestRevision, String markdown, String templateName,
                               boolean templateDeleted, boolean hasLogo, ChromeFieldsResponse chrome,
                               Instant generatedAt, String generatedBy) {
}
