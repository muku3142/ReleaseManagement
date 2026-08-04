package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraSprintPayload(Long id, String name, String state, String startDate) {
}
