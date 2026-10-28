package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.BrowserStreamListener.StreamDeadlineExceeded;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformEvents.DoneEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformEvents.ErrorEvent;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformEvents.LimitsResponse;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformEvents.SavedResponse;
import com.evision.releasemgmt.adapters.web.dto.AssistantTransformRequest;
import com.evision.releasemgmt.adapters.web.dto.SaveAiRevisionRequest;
import com.evision.releasemgmt.application.assistant.AssistantTransformService;
import com.evision.releasemgmt.application.assistant.AssistantTransformService.Limits;
import com.evision.releasemgmt.application.assistant.AssistantTransformService.Prepared;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;

@RestController
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class AssistantTransformController {

    private static final Logger log = LoggerFactory.getLogger(AssistantTransformController.class);
    // The listener enforces the real deadline; the emitter timeout only reclaims a stream whose worker died.
    private static final Duration EMITTER_BACKSTOP = Duration.ofSeconds(30);

    private final AssistantTransformService service;
    private final AssistantProperties properties;
    private final Clock clock;

    public AssistantTransformController(AssistantTransformService service, AssistantProperties properties,
                                        Clock clock) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/api/assistant/transform/limits")
    public LimitsResponse limits() {
        Limits limits = service.limits();
        return new LimitsResponse(limits.numCtx(), limits.charsPerToken(), limits.systemTokens(),
                limits.templateSlack(), limits.instructionMaxChars(), limits.presetMaxChars());
    }

    // No `produces`: validation, 404, 409 and 413 problem+json responses must still render before the stream opens.
    @PostMapping("/api/assistant/transform")
    public ResponseEntity<SseEmitter> transform(@Valid @RequestBody AssistantTransformRequest request) {
        Prepared prepared = service.prepare(request.releaseId(), request.documentType(), request.sourceRevision(),
                request.mode(), request.instruction());
        SseEmitter emitter =
                new SseEmitter(properties.documentAssist().streamDeadline().plus(EMITTER_BACKSTOP).toMillis());
        Thread.ofVirtual().name("assistant-transform").start(() -> stream(prepared, emitter));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @PostMapping("/api/assistant/documents/ai-revisions")
    public ResponseEntity<SavedResponse> save(@Valid @RequestBody SaveAiRevisionRequest request,
                                              Principal principal) {
        int revision = service.saveAiRevision(request.releaseId(), request.documentType(), request.sourceRevision(),
                request.mode(), request.instruction(), request.markdown(), principal.getName()).revision();
        return ResponseEntity.status(HttpStatus.CREATED).body(new SavedResponse(revision));
    }

    private void stream(Prepared prepared, SseEmitter emitter) {
        BrowserStreamListener listener = new BrowserStreamListener(emitter, clock,
                clock.instant().plus(properties.documentAssist().streamDeadline()));
        try {
            switch (service.run(prepared, listener)) {
                case TransformOutcome.Done done -> listener.sendTerminal("done", new DoneEvent(done.finishReason(),
                        done.truncated(), "stop".equals(done.finishReason()) && !done.truncated()));
                case TransformOutcome.TooLarge tooLarge -> listener.sendTerminal("error",
                        new ErrorEvent("TOO_LARGE", null, prepared.documentChars(), tooLarge.maxChars()));
                case TransformOutcome.Failed failed -> listener.sendTerminal("error", new ErrorEvent(
                        (failed.reason() == FailureReason.REJECTED ? FailureReason.FAILED : failed.reason()).name(),
                        failed.retryAfterSeconds(), null, null));
            }
        } catch (ClientGoneException clientGone) {
            // browser disconnected; nothing more to send
        } catch (StreamDeadlineExceeded deadline) {
            listener.sendTerminal("error", new ErrorEvent(FailureReason.DEADLINE.name(), null, null, null));
        } catch (RuntimeException e) {
            log.warn("assistant transform stream failed type={}", e.getClass().getSimpleName());
            listener.sendTerminal("error", new ErrorEvent(FailureReason.FAILED.name(), null, null, null));
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
