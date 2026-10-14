package com.evision.ragservice.application;

import com.evision.ragservice.domain.CancelReason;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.ports.EventSink;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class RecordingEventSink implements EventSink {

    record Sent(String event, Object payload) {}

    final List<Sent> sent = new CopyOnWriteArrayList<>();
    final AtomicInteger heartbeats = new AtomicInteger();
    volatile boolean completed;

    private int goneAfter = Integer.MAX_VALUE;
    private Cancellation toCancel;

    void goneAfter(int sends, Cancellation cancellation) {
        this.goneAfter = sends;
        this.toCancel = cancellation;
    }

    @Override
    public boolean send(String event, Object payload) {
        if (sent.size() >= goneAfter) {
            toCancel.cancel(CancelReason.CLIENT_GONE);
            return false;
        }
        sent.add(new Sent(event, payload));
        return true;
    }

    @Override
    public void heartbeat() {
        heartbeats.incrementAndGet();
    }

    @Override
    public void complete() {
        completed = true;
    }

    List<String> events() {
        return sent.stream().map(Sent::event).toList();
    }

    <T> T payload(String event, Class<T> type) {
        return sent.stream().filter(s -> s.event().equals(event)).map(s -> type.cast(s.payload())).findFirst()
                .orElseThrow();
    }
}
