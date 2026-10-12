package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.List;

public record ReleaseItemResponse(String key, String summary, String issueType, String status,
                                  String resolution, String assignee, String reporter,
                                  List<String> fixVersions, String component, String description,
                                  List<String> comments, int commentTotal, Instant addedAt,
                                  CodeLinksResponse codeLinks, ItemChurnResponse churn) {
}
