package com.evision.releasemgmt.domain.release;

import java.time.Instant;
import java.util.UUID;

public record ManualItem(UUID id, String title, String description,
                         ManualItemCategory category, Instant addedAt) {

    public static ManualItem create(String title, String description, ManualItemCategory category,
                                    Instant now) {
        return new ManualItem(UUID.randomUUID(), title, description, category, now);
    }
}
