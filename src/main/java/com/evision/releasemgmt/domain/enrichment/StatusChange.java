package com.evision.releasemgmt.domain.enrichment;

import com.evision.releasemgmt.domain.jira.StatusCategory;

import java.time.Instant;

// A null category means the status id is unknown to Jira's /status list; categories are mapped by id, never by name.
public record StatusChange(Instant at, String fromStatusId, String toStatusId, StatusCategory fromCategory,
                           StatusCategory toCategory) {
}
