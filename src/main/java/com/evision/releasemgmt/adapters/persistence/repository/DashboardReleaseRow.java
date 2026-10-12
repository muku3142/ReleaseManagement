package com.evision.releasemgmt.adapters.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface DashboardReleaseRow {

    UUID getId();

    String getName();

    String getVersion();

    String getStatus();

    Instant getCreatedAt();

    Instant getFrozenAt();

    long getManualCount();
}
