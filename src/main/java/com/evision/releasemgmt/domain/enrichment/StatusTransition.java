package com.evision.releasemgmt.domain.enrichment;

import java.time.Instant;

public record StatusTransition(Instant at, String fromStatusId, String toStatusId) {
}
