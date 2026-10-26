package com.evision.releasemgmt.domain.portfolio;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Client(UUID id, String name, Instant archivedAt, Set<UUID> productIds) {

    public Client {
        productIds = Set.copyOf(productIds);
    }

    public boolean archived() {
        return archivedAt != null;
    }

    public boolean linkedTo(UUID productId) {
        return productIds.contains(productId);
    }
}
