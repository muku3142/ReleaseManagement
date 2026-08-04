package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraBoardPayload(Long id, String name, String type) {
}
