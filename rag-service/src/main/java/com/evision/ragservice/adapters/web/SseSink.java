package com.evision.ragservice.adapters.web;

import com.evision.ragservice.domain.CancelReason;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.ports.EventSink;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Post-open failures are `error` events, never completeWithError, so the client always sees a well-formed stream
final class SseSink implements EventSink {

    private final SseEmitter emitter;
    private final Cancellation cancellation;

    SseSink(SseEmitter emitter, Cancellation cancellation) {
        this.emitter = emitter;
        this.cancellation = cancellation;
    }

    @Override
    public boolean send(String event, Object payload) {
        return deliver(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
    }

    @Override
    public void heartbeat() {
        deliver(SseEmitter.event().comment("hb"));
    }

    @Override
    public void complete() {
        try {
            emitter.complete();
        } catch (IllegalStateException alreadyCompleted) {
            // client already gone; nothing left to close
        }
    }

    private boolean deliver(SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException clientGone) {
            cancellation.cancel(CancelReason.CLIENT_GONE);
            return false;
        }
    }
}
