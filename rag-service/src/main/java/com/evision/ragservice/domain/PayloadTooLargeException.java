package com.evision.ragservice.domain;

public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(long limitBytes) {
        super("Payload exceeds the limit of " + limitBytes + " bytes");
    }
}
