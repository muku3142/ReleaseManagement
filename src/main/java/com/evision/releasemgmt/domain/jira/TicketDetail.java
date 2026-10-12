package com.evision.releasemgmt.domain.jira;

import java.time.Instant;
import java.util.List;

// Phase 6: jiraIssueId feeds dev-status (numeric id is present on /search/jql), updatedAt scopes the changelog read (D-06-08),
// and the current status category comes free from the status field.
public record TicketDetail(
        String key,
        String summary,
        String issueType,
        String status,
        String resolution,
        String assignee,
        String reporter,
        List<String> fixVersions,
        String component,
        String description,
        List<String> comments,
        int commentTotal,
        String jiraIssueId,
        Instant updatedAt,
        StatusCategory statusCategory) {
}
