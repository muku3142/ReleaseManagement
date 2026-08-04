package com.evision.releasemgmt.domain.jira;

public enum TicketField {
    SUMMARY("Summary"),
    ISSUE_TYPE("Issue Type"),
    STATUS("Status"),
    RESOLUTION("Resolution"),
    ASSIGNEE("Assignee"),
    REPORTER("Reporter"),
    DESCRIPTION("Description"),
    COMMENT("Comment"),
    FIX_VERSIONS("Fix versions");

    private final String displayName;

    TicketField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
