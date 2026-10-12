package com.evision.releasemgmt.adapters.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface DeploymentHistoryRow {

    UUID getId();

    UUID getReleaseId();

    String getReleaseName();

    String getReleaseVersion();

    UUID getStageId();

    String getStageName();

    Instant getStageRetiredAt();

    String getOutcome();

    Instant getDeployedAt();

    String getRecordedBy();

    String getNote();

    boolean getCorrected();
}
