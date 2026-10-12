package com.evision.releasemgmt.domain.enrichment;

import com.evision.releasemgmt.domain.jira.StatusCategory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record StatusHistory(HistoryState state, Instant syncedUpdatedAt, Instant firstDoneAt, Integer reopenCount,
                            List<StatusChange> changes) {

    public StatusHistory {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public static StatusHistory notRead() {
        return new StatusHistory(HistoryState.NOT_READ, null, null, null, List.of());
    }

    // A null synced time makes the next run retry the read.
    public static StatusHistory unavailable() {
        return new StatusHistory(HistoryState.UNAVAILABLE, null, null, null, List.of());
    }

    // Done means status category Done, not a status name.
    public static StatusHistory derive(List<StatusChange> changes, Instant syncedUpdatedAt) {
        List<StatusChange> sorted = changes.stream().sorted(Comparator.comparing(StatusChange::at)).toList();
        boolean unknownCategory = sorted.stream().anyMatch(c ->
                (c.fromStatusId() != null && c.fromCategory() == null)
                        || (c.toStatusId() != null && c.toCategory() == null));
        if (unknownCategory) {
            return new StatusHistory(HistoryState.UNAVAILABLE, syncedUpdatedAt, null, null, sorted);
        }
        Instant firstDoneAt = sorted.stream().filter(c -> c.toCategory() == StatusCategory.DONE)
                .map(StatusChange::at).findFirst().orElse(null);
        int reopenCount = (int) sorted.stream()
                .filter(c -> c.fromCategory() == StatusCategory.DONE && c.toCategory() != StatusCategory.DONE).count();
        return new StatusHistory(HistoryState.AVAILABLE, syncedUpdatedAt, firstDoneAt, reopenCount, sorted);
    }

    public boolean needsRead(Instant ticketUpdatedAt) {
        return state == HistoryState.NOT_READ || syncedUpdatedAt == null || !syncedUpdatedAt.equals(ticketUpdatedAt);
    }
}
