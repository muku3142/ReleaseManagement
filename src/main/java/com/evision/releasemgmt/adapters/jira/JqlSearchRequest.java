package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record JqlSearchRequest(String jql, List<String> fields, int maxResults, String nextPageToken) {
}
