package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

// Deliberately no matching-count field here: /search/jql does not return one (RESEARCH.md
// Pitfall 1) - modelling one would be permanently null and invite a wrong progress calculation.
@JsonIgnoreProperties(ignoreUnknown = true)
record JqlSearchPage(List<JsonNode> issues, String nextPageToken, Boolean isLast) {
}
