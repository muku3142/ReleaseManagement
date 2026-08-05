package com.evision.releasemgmt.adapters.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface ReleaseSummaryRow {

    UUID getId();

    String getName();

    String getVersion();

    String getStatus();

    String getJiraProjectKey();

    Instant getCreatedAt();

    String getCreatedBy();

    Instant getFrozenAt();

    String getFrozenBy();

    Instant getLastRefreshedAt();

    long getItemCount();
}
