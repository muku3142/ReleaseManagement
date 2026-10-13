package com.evision.ragservice.domain;

public class ClientNameTakenException extends RuntimeException {

    public ClientNameTakenException(String name) {
        super("Client name already taken: " + name);
    }
}
