package com.evision.releasemgmt.domain.enrichment;

// The first six are FAILED causes; the rest only ever mark a LINKED item incomplete, because an ADO failure never
// fails an item. A never-configured PAT is the designed Jira-only mode and deliberately has no reason of its own.
public enum EnrichmentReason {
    JIRA_UNAUTHORIZED, JIRA_RATE_LIMITED, JIRA_UNREACHABLE, DEV_STATUS_ERROR, DEV_STATUS_UNRECOGNISED, TIMEOUT,
    ADO_UNAUTHORIZED, ADO_CREDENTIAL_EXPIRED, ADO_CREDENTIAL_UNREADABLE, ADO_ORG_MISMATCH, ADO_NOT_FOUND,
    ADO_RATE_LIMITED, ADO_UNREACHABLE, ADO_ERROR, COMMIT_LIMIT
}
