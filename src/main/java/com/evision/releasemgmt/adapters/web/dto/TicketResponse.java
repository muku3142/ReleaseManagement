package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;

public record TicketResponse(String key, String summary, String issueType, String status,
                             String resolution, String assignee, String reporter,
                             List<String> fixVersions, String description,
                             List<String> comments, int commentTotal) {
}
