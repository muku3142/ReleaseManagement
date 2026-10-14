package com.evision.releasemgmt.adapters.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ChurnItemRow {

    UUID getReleaseId();

    String getTicketKey();

    String getEnrichmentStatus();

    boolean getEnrichmentIncomplete();

    String getCommitsSource();

    String getHistoryState();

    Instant getFirstDoneAt();

    Integer getReopenCount();

    long getCommitsToDone();

    long getPostDoneCommits();

    long getCommitTotal();

    BigDecimal getStoryPoints();

    boolean getStoryPointsRead();
}
