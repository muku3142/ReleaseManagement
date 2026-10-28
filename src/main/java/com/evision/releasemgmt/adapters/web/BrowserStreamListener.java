package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.DoneEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.ErrorEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.Meta;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.StatusEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.TokenEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.ToolView;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformEvents.ProgressEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformEvents.RemainderEvent;
import com.evision.releasemgmt.domain.assistant.AssistantAnswer;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.ports.RagChatPort;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

// Writes browser events for any chat-like flow. A failed write means the browser went away: the exception unwinds
// through the orchestrator and adapter, which closes the upstream exchange.
final class BrowserStreamListener implements RagChatPort.ChatListener {

    private final SseEmitter emitter;
    private final Clock clock;
    private final Instant deadlineAt;

    BrowserStreamListener(SseEmitter emitter, Clock clock, Instant deadlineAt) {
        this.emitter = emitter;
        this.clock = clock;
        this.deadlineAt = deadlineAt;
    }

    @Override
    public void token(String text) {
        send(SseEmitter.event().name("token").data(new TokenEvent(text), MediaType.APPLICATION_JSON));
    }

    @Override
    public void roundDiscarded() {
        send(SseEmitter.event().name("discard").data(Map.of(), MediaType.APPLICATION_JSON));
    }

    @Override
    public void toolStarted(String name) {
        send(SseEmitter.event().name("status").data(new StatusEvent(name), MediaType.APPLICATION_JSON));
    }

    @Override
    public void sectionStarted(int section, int sections) {
        send(SseEmitter.event().name("progress").data(new ProgressEvent(section, sections),
                MediaType.APPLICATION_JSON));
    }

    @Override
    public void heartbeat() {
        send(SseEmitter.event().comment("hb"));
    }

    void finish(AssistantAnswer answer) {
        if (answer.failure() != null) {
            sendTerminal("error", new ErrorEvent(AssistantSourceViews.ofError(answer.failure()),
                    answer.retryAfterSeconds()));
            return;
        }
        sendTerminal("done", new DoneEvent(answer.refused(),
                answer.sources().stream().map(AssistantSourceViews::of).toList(),
                answer.toolsRun().stream()
                        .map(run -> new ToolView(run.round(), run.name(), run.arguments(), run.error())).toList(),
                answer.historyTrimmed(), new Meta(answer.model(), answer.promptHash())));
    }

    // Best effort: the browser may already be gone and there is nobody left to tell.
    void sendTerminal(String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException clientGone) {
            // nothing to close beyond what the caller's finally does
        }
    }

    // Not deadline-checked: it is sent after the deadline has already fired and must still reach the browser.
    @Override
    public void remainder(int keep, String text) {
        write(SseEmitter.event().name("remainder").data(new RemainderEvent(keep, text), MediaType.APPLICATION_JSON));
    }

    private void write(SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            throw new ClientGoneException();
        }
    }

    private void send(SseEventBuilder event) {
        write(event);
        if (clock.instant().isAfter(deadlineAt)) {
            throw new StreamDeadlineExceeded();
        }
    }

    static final class StreamDeadlineExceeded extends RuntimeException {

        private static final long serialVersionUID = 1L;

        StreamDeadlineExceeded() {
            super(null, null, false, false);
        }
    }
}
