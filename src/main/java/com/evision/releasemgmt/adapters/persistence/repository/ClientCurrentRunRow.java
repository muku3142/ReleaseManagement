package com.evision.releasemgmt.adapters.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface ClientCurrentRunRow {

    UUID getClientId();

    UUID getProductId();

    UUID getReleaseId();

    Instant getInstalledAt();
}
