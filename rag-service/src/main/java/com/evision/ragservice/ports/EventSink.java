package com.evision.ragservice.ports;

public interface EventSink {

    /** Send returns false when the client is gone; implementations never throw. */
    boolean send(String event, Object payload);

    void heartbeat();

    void complete();
}
