package com.evision.releasemgmt.domain.auth;

public enum JiraAuthFailureReason {
    INVALID_CREDENTIALS,
    JIRA_FORBIDDEN,
    JIRA_RATE_LIMITED,
    JIRA_UNREACHABLE,
    JIRA_ERROR,
    SITE_NOT_CONFIGURED
}
