package com.evision.releasemgmt.domain.auth;

public record JiraAccessContext(String siteBaseUrl, String accountId, String email, String apiToken) {
}
