package com.evision.releasemgmt.adapters.jira;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class JiraTimestamps {

    // Jira writes offsets with no colon and in the tenant's zone (+0300 captured 2026-10-12), which the ISO parsers
    // reject; never assume UTC.
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx");

    private JiraTimestamps() {
    }

    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value, FORMAT).toInstant();
    }
}
