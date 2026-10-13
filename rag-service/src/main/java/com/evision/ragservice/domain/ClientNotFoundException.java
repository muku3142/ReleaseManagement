package com.evision.ragservice.domain;

import java.util.UUID;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID clientId) {
        super("Client not found: " + clientId);
    }
}
