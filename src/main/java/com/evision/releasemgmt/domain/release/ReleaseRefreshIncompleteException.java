package com.evision.releasemgmt.domain.release;

// D-03-07: last_refreshed_at is a claim about the whole release. Stamping it after a partial
// Jira response would put a fresh timestamp over rows that still hold their old values.
public class ReleaseRefreshIncompleteException extends RuntimeException {
}
