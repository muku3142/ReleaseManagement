package com.evision.releasemgmt.domain.enrichment;

import com.evision.releasemgmt.domain.jira.StatusCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatusHistoryTest {

    private static final Instant T1 = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-07-03T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-07-04T00:00:00Z");

    private static StatusChange change(Instant at, String from, String to, StatusCategory fromCat, StatusCategory toCat) {
        return new StatusChange(at, from, to, fromCat, toCat);
    }

    @Test
    void derivesFirstDoneAndReopenCountFromCategoriesRegardlessOfInputOrder() {
        List<StatusChange> changes = List.of(
                change(T4, "2", "3", StatusCategory.IN_PROGRESS, StatusCategory.DONE),
                change(T1, "1", "2", StatusCategory.TO_DO, StatusCategory.IN_PROGRESS),
                change(T3, "3", "2", StatusCategory.DONE, StatusCategory.IN_PROGRESS),
                change(T2, "2", "3", StatusCategory.IN_PROGRESS, StatusCategory.DONE));

        StatusHistory history = StatusHistory.derive(changes, T4);

        assertThat(history.state()).isEqualTo(HistoryState.AVAILABLE);
        assertThat(history.firstDoneAt()).isEqualTo(T2);
        assertThat(history.reopenCount()).isEqualTo(1);
        assertThat(history.changes()).extracting(StatusChange::at).containsExactly(T1, T2, T3, T4);
    }

    @Test
    void refusesFiguresWhenAnyCategoryIsUnknown() {
        List<StatusChange> changes = List.of(
                change(T1, "1", "99", StatusCategory.TO_DO, null),
                change(T2, "99", "3", null, StatusCategory.DONE));

        StatusHistory history = StatusHistory.derive(changes, T2);

        assertThat(history.state()).isEqualTo(HistoryState.UNAVAILABLE);
        assertThat(history.firstDoneAt()).isNull();
        assertThat(history.reopenCount()).isNull();
        assertThat(history.syncedUpdatedAt()).isEqualTo(T2);
    }

    @Test
    void needsReadWhenNotReadUnavailableOrTicketMoved() {
        assertThat(StatusHistory.notRead().needsRead(T1)).isTrue();
        assertThat(StatusHistory.unavailable().needsRead(T1)).isTrue();
        StatusHistory synced = StatusHistory.derive(List.of(), T1);
        assertThat(synced.needsRead(T2)).isTrue();
        assertThat(synced.needsRead(T1)).isFalse();
    }
}
