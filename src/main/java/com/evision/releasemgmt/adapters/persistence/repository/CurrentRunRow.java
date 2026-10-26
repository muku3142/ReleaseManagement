package com.evision.releasemgmt.adapters.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface CurrentRunRow {

    UUID getProductId();

    UUID getReleaseId();

    String getReleaseName();

    String getReleaseVersion();

    Instant getInstalledAt();
}
