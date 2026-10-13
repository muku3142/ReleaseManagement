package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.domain.ClientApp;
import java.time.Instant;
import java.util.UUID;

public record ClientResponse(UUID id, String name, Instant createdAt, Instant revokedAt, Instant lastUsedAt) {

    public static ClientResponse from(ClientApp client) {
        return new ClientResponse(client.id(), client.name(), client.createdAt(), client.revokedAt(),
                client.lastUsedAt());
    }
}
