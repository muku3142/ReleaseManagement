package com.evision.ragservice.adapters.web;

import com.evision.ragservice.adapters.web.dto.ChatRequest;
import com.evision.ragservice.adapters.web.dto.TransformRequest;
import com.evision.ragservice.application.ChatService;
import com.evision.ragservice.application.TransformService;
import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.domain.CancelReason;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.ports.EventSink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ChatController {

    private static final long EMITTER_BACKSTOP_SECONDS = 15;

    private static final String EVENTS = "Server-sent events. status {requestId, phase: SEARCHING|GENERATING}; "
            + "token {text}; citations {basis: MARKED|RETRIEVED, items: [{n, kind: PASSAGE|TOOL, documentId, title, "
            + "url, collection, metadata}]} (chat only); tool_call {round, questionId, calls: [{id, name, "
            + "arguments}], signature} (chat only; the stream ends after it with done finishReason tool_calls, "
            + "resend the turn unchanged with results); done {refusal, refusalLayer: GATE|MODEL|null, finishReason, "
            + "truncated, promptTokens, completionTokens, historyTrimmed (true when older history turns were not "
            + "used), model, promptHash}; error {reason: DEADLINE|GENERATION_FAILED|"
            + "OLLAMA_UNAVAILABLE|MODEL_NOT_AVAILABLE|EMBEDDING_UNAVAILABLE|TOOL_CALL_INVALID|INTERNAL}. Comment "
            + "lines are heartbeats. Errors before the stream opens are problem+json: 400 (reason includes "
            + "TOOL_TURN_INVALID), 404, 413 (transform; chat reason TOOL_RESULT_TOO_LARGE), 429 with Retry-After, "
            + "503 (reason OLLAMA_UNAVAILABLE or MODEL_NOT_AVAILABLE).";

    private final ChatService chat;
    private final TransformService transform;
    private final ChatProperties props;

    public ChatController(ChatService chat, TransformService transform, ChatProperties props) {
        this.chat = chat;
        this.transform = transform;
        this.props = props;
    }

    @PostMapping("/v1/chat")
    @Operation(summary = "Answer a question from the given collections as a server-sent event stream")
    @ApiResponse(responseCode = "200", description = EVENTS,
            content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string")))
    public ResponseEntity<SseEmitter> chat(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @Valid @RequestBody ChatRequest request) {
        ChatService.Plan plan = chat.prepare(clientId, request.toCommand());
        return stream(plan.cancellation(), sink -> chat.run(plan, sink));
    }

    @PostMapping("/v1/transform")
    @Operation(summary = "Rewrite the given text following an instruction as a server-sent event stream")
    @ApiResponse(responseCode = "200", description = EVENTS,
            content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string")))
    public ResponseEntity<SseEmitter> transform(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @Valid @RequestBody TransformRequest request) {
        TransformService.Plan plan = transform.prepare(clientId, request.toCommand());
        return stream(plan.cancellation(), sink -> transform.run(plan, sink));
    }

    // The emitter timeout is only a backstop: the deadline watchdog in the service always fires first
    private ResponseEntity<SseEmitter> stream(Cancellation cancellation, Consumer<EventSink> work) {
        SseEmitter emitter = new SseEmitter(props.deadline().plusSeconds(EMITTER_BACKSTOP_SECONDS).toMillis());
        emitter.onCompletion(() -> cancellation.cancel(CancelReason.CLIENT_GONE));
        emitter.onError(e -> cancellation.cancel(CancelReason.CLIENT_GONE));
        emitter.onTimeout(() -> cancellation.cancel(CancelReason.DEADLINE));
        SseSink sink = new SseSink(emitter, cancellation);
        Thread.ofVirtual().name("generation").start(() -> work.accept(sink));
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
