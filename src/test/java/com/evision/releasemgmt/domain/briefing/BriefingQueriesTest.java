package com.evision.releasemgmt.domain.briefing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BriefingQueriesTest {

    private static final String IN = "project in (\"CNV\",\"ABC\")";

    @Test
    void buildsTheActiveSprintQuery() {
        assertThat(BriefingQueries.activeSprintOpen(List.of("CNV", "ABC")))
                .isEqualTo(IN + " AND sprint in openSprints() AND statusCategory != Done ORDER BY Rank ASC");
    }

    @Test
    void buildsTheOtherQueries() {
        List<String> keys = List.of("CNV", "ABC");
        assertThat(BriefingQueries.activeSprintAll(keys)).isEqualTo(IN + " AND sprint in openSprints()");
        assertThat(BriefingQueries.nextSprint(keys))
                .isEqualTo(IN + " AND sprint in futureSprints() AND statusCategory != Done ORDER BY Rank ASC");
        assertThat(BriefingQueries.backlog(keys)).isEqualTo(IN + " AND statusCategory != Done AND (sprint is EMPTY OR "
                + "sprint not in (openSprints(), futureSprints())) ORDER BY Rank ASC");
        assertThat(BriefingQueries.staleCandidates(keys, 3))
                .isEqualTo(IN + " AND statusCategory = \"In Progress\" AND updated <= -3d");
        assertThat(BriefingQueries.inProgress(keys)).isEqualTo(IN + " AND statusCategory = \"In Progress\"");
        assertThat(BriefingQueries.createdSince(keys, 2)).isEqualTo(IN + " AND created >= -2d");
        assertThat(BriefingQueries.doneSince(keys, 2))
                .isEqualTo(IN + " AND statusCategory = Done AND resolutiondate >= -2d");
        assertThat(BriefingQueries.reopenCandidates(keys, 2))
                .isEqualTo(IN + " AND statusCategory != Done AND updated >= -2d ORDER BY updated DESC");
        assertThat(BriefingQueries.dueWithin(keys, 5)).isEqualTo(IN + " AND statusCategory != Done AND duedate <= 5d");
    }

    @Test
    void rejectsInvalidKeysAndInjection() {
        for (String bad : List.of("cnv", "C", "CNV\" OR 1=1")) {
            assertThatThrownBy(() -> BriefingQueries.activeSprintOpen(List.of(bad)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> BriefingQueries.backlog(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void daysBackRoundsUpAndClamps() {
        Instant now = Instant.parse("2026-10-25T10:00:00Z");
        assertThat(BriefingQueries.daysBack(now.minus(Duration.ofHours(25)), now)).isEqualTo(3);
        assertThat(BriefingQueries.daysBack(now.minus(Duration.ofDays(90)), now)).isEqualTo(30);
        assertThat(BriefingQueries.daysBack(now, now)).isEqualTo(1);
    }
}
