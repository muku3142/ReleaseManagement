package com.evision.releasemgmt.adapters.jira;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JiraTimestampsTest {

    @Test
    void parsesNoColonUtcOffset() {
        assertThat(JiraTimestamps.parse("2026-07-13T09:41:34.000+0000")).isEqualTo(Instant.parse("2026-07-13T09:41:34Z"));
    }

    @Test
    void parsesTenantZoneOffset() {
        assertThat(JiraTimestamps.parse("2026-08-13T16:31:06.042+0300"))
                .isEqualTo(Instant.parse("2026-08-13T13:31:06.042Z"));
    }

    @Test
    void nullStaysNull() {
        assertThat(JiraTimestamps.parse(null)).isNull();
    }
}
