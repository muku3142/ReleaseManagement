package com.evision.releasemgmt.domain.jira;

import java.util.List;

public record TicketDetail(
        String key,
        String summary,
        String issueType,
        String status,
        String resolution,
        String assignee,
        String reporter,
        List<String> fixVersions,
        String description,
        List<String> comments,
        int commentTotal) {
}
