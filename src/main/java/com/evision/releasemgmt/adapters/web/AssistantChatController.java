package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.BrowserStreamListener.StreamDeadlineExceeded;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatEvents.ErrorEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantChatRequest;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.assistant.AssistantService;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.assistant.AssistantAnswer;
import com.evision.releasemgmt.domain.assistant.ChatMessage;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;

@RestController
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class AssistantChatController {

    private static final Logger log = LoggerFactory.getLogger(AssistantChatController.class);
    // The listener enforces the real deadline; the emitter timeout only reclaims a stream whose worker died.
    private static final Duration EMITTER_BACKSTOP = Duration.ofSeconds(30);

    private final AssistantService assistant;
    private final ReleaseService releaseService;
    private final AssistantProperties properties;
    private final Clock clock;

    public AssistantChatController(AssistantService assistant, ReleaseService releaseService,
                                   AssistantProperties properties, Clock clock) {
        this.assistant = assistant;
        this.releaseService = releaseService;
        this.properties = properties;
        this.clock = clock;
    }

    // No `produces`: validation and 404 problem+json responses must still render before the stream opens.
    @PostMapping("/api/assistant/chat")
    public ResponseEntity<SseEmitter> chat(@Valid @RequestBody AssistantChatRequest request) {
        if (request.releaseId() != null) {
            releaseService.get(request.releaseId());
        }
        SseEmitter emitter = new SseEmitter(properties.streamDeadline().plus(EMITTER_BACKSTOP).toMillis());
        Thread.ofVirtual().name("assistant-stream").start(() -> stream(request, emitter));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private void stream(AssistantChatRequest request, SseEmitter emitter) {
        BrowserStreamListener listener = new BrowserStreamListener(emitter, clock,
                clock.instant().plus(properties.streamDeadline()));
        try {
            AssistantAnswer answer = assistant.ask(request.question(),
                    request.history().stream().map(m -> new ChatMessage(m.role(), m.content())).toList(),
                    request.releaseId(), listener);
            listener.finish(answer);
        } catch (ClientGoneException clientGone) {
            // browser disconnected; nothing more to send
        } catch (StreamDeadlineExceeded deadline) {
            listener.sendTerminal("error", new ErrorEvent(FailureReason.DEADLINE.name(), null));
        } catch (RuntimeException e) {
            log.warn("assistant stream failed type={}", e.getClass().getSimpleName());
            listener.sendTerminal("error", new ErrorEvent(AssistantSourceViews.ofError(FailureReason.FAILED), null));
        } finally {
            complete(emitter);
        }
    }

    private static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException alreadyCompleted) {
            // the container already closed it after a failed write
        }
    }
}
