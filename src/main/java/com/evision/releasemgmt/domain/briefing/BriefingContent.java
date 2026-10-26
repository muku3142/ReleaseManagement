package com.evision.releasemgmt.domain.briefing;

import java.util.List;

public record BriefingContent(BriefingStatus status, int attention, String headline, String summary,
        List<Section> sections) {

    public record Section(String title, List<Item> items) { }

    /** issueKey is null for lines that are not about one issue. */
    public record Item(String issueKey, String text) { }
}
