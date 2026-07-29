package com.evision.releasemgmt.adapters.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record JiraMyselfResponse(String accountId, String emailAddress, String displayName, Boolean active) {
}
