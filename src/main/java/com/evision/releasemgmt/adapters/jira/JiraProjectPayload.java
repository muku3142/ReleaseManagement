package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraProjectPayload(String id, String key, String name) {
}
