package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraBoardPage(Integer startAt, Integer maxResults, Integer total, Boolean isLast,
                     List<JiraBoardPayload> values) {
}
