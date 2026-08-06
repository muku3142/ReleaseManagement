package com.evision.releasemgmt.domain.release;

// D-03-13: every requested key lands in exactly one bucket. notFound covers the keys Jira did
// not return - a deleted issue, one the credential cannot see, or one that failed the issue-key
// pattern - which would otherwise be reported to the user as part of a plain success.
public record AddTicketsResult(Release release, int added, int alreadyPresent, int notFound) {
}
