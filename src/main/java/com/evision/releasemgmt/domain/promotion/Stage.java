package com.evision.releasemgmt.domain.promotion;

import java.time.Instant;
import java.util.UUID;

public record Stage(UUID id, String name, int position, boolean production, Instant retiredAt) {

    public boolean retired() {
        return retiredAt != null;
    }
}
