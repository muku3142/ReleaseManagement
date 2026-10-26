package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

public record Product(UUID id, String name, Instant archivedAt) {

    public boolean archived() {
        return archivedAt != null;
    }
}
