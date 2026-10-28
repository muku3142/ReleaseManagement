package com.evision.releasemgmt.adapters.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface ClientInstallRow {

    UUID getId();

    UUID getReleaseId();

    UUID getProductId();

    UUID getClientId();

    String getOutcome();

    Instant getInstalledAt();

    Instant getRecordedAt();

    String getRecordedBy();

    String getNote();

    UUID getSupersedesId();

    String getCorrectionReason();

    String getReleaseName();

    String getReleaseVersion();
}
