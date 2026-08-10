package com.evision.releasemgmt.domain.template.view;

import java.util.List;

// D-04-01: ONE collection for ticket-backed and manual items. Two collections would let a template
// loop only the tickets and silently drop every config change and database script from a runbook.
// Ticket-only fields are "" for manual items; category is "" for ticket items.
public record ItemView(String title, boolean isManual, String key, String issueType, String status,
                       String resolution, String assignee, String reporter, String component,
                       String description, String category, List<CommentView> comments,
                       List<CommitView> commits, List<PullRequestView> pullRequests) {
}
