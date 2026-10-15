package com.evision.releasemgmt.domain.assistant;

public enum IndexCollection {
    TICKETS("rm-tickets"),
    DOCUMENTS("rm-documents"),
    MANUAL_ITEMS("rm-manual-items"),
    CODE_LINKS("rm-code-links");

    private final String wireName;

    IndexCollection(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
