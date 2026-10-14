package com.evision.releasemgmt.domain.dashboard;

import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChurnItem(UUID releaseId, String ticketKey, EnrichmentStatus enrichmentStatus, boolean enrichmentIncomplete,
                        CommitsSource commitsSource, HistoryState historyState, Instant firstDoneAt, Integer reopenCount,
                        long commitsToDone, long postDoneCommits, long commitTotal,
                        BigDecimal storyPoints, boolean storyPointsRead) {

    // D-08-07/D-08-17: incomplete enrichment (COMMIT_LIMIT keeps the newest commits, partial ADO data) biases commit counts.
    public boolean hasCommitData() {
        return historyState == HistoryState.AVAILABLE && firstDoneAt != null && commitTotal > 0 && !enrichmentIncomplete;
    }

    public boolean hasReopenData() {
        return historyState == HistoryState.AVAILABLE && reopenCount != null;
    }
}
