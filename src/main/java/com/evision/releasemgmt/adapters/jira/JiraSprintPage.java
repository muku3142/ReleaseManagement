package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraSprintPage(Integer startAt, Integer maxResults, Integer total, Boolean isLast,
                      List<JiraSprintPayload> values) {
}
