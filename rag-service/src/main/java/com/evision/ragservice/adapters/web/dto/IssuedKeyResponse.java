package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.application.ClientService;
import java.util.UUID;

public record IssuedKeyResponse(UUID id, String name, String apiKey) {

    public static IssuedKeyResponse from(ClientService.IssuedKey issued) {
        return new IssuedKeyResponse(issued.client().id(), issued.client().name(), issued.apiKey());
    }
}
