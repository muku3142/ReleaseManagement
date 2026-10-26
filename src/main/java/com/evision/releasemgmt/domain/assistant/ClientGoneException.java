package com.evision.releasemgmt.domain.assistant;

public class ClientGoneException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ClientGoneException() {
        super("client disconnected");
    }
}
