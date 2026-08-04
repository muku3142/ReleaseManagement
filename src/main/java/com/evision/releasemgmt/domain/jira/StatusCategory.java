package com.evision.releasemgmt.domain.jira;

public enum StatusCategory {

    TO_DO("To Do"),
    IN_PROGRESS("In Progress"),
    DONE("Done");

    private final String jqlName;

    StatusCategory(String jqlName) {
        this.jqlName = jqlName;
    }

    public String jqlName() {
        return jqlName;
    }
}
