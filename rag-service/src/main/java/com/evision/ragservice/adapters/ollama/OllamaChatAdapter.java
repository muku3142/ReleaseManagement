package com.evision.ragservice.adapters.ollama;

import com.evision.ragservice.config.ChatProperties;
import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.GenerationFailedException;
import com.evision.ragservice.domain.GenerationOptions;
import com.evision.ragservice.domain.ModelNotAvailableException;
import com.evision.ragservice.domain.OllamaUnavailableException;
import com.evision.ragservice.ports.ChatPort;
import com.evision.ragservice.domain.ToolCall;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OllamaChatAdapter implements ChatPort {

    private static final double TOP_P = 0.8;
    private static final int ERROR_BODY_PEEK_BYTES = 1024;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final int MAX_CALLS_PER_STREAM = 16;
    private static final SecureRandom CALL_ID_RANDOM = new SecureRandom();

    private final RestClient streamClient;
    private final RestClient healthClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final String keepAlive;
    private volatile List<String> capabilities = List.of();

    public OllamaChatAdapter(@Qualifier("ollamaStreamClient") RestClient streamClient, ObjectMapper objectMapper,
                             ChatProperties chat, @Value("${rag.ollama.base-url}") String baseUrl,
                             @Value("${rag.ollama.health-timeout}") Duration healthTimeout) {
        this.streamClient = streamClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = chat.model();
        this.keepAlive = chat.keepAlive();
        JdkClientHttpRequestFactory healthFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(healthTimeout).build());
        healthFactory.setReadTimeout(healthTimeout);
        this.healthClient = RestClient.builder().requestFactory(healthFactory).build();
    }

    @Override
    public void assertReady() {
        ShowResponse show;
        try {
            show = healthClient.post().uri(baseUrl + "/api/show").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model)).retrieve().body(ShowResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ModelNotAvailableException(model);
        } catch (RestClientException e) {
            throw new OllamaUnavailableException("Chat backend unreachable", e);
        }
        capabilities = show == null || show.capabilities() == null ? List.of() : show.capabilities();
        if (!capabilities.contains("completion")) {
            throw new ModelNotAvailableException(model);
        }
    }

    @Override
    public void assertToolsReady() {
        if (!capabilities.contains("tools")) {
            throw new ModelNotAvailableException(model);
        }
    }

    @Override
    public Completion stream(List<ChatMessage> messages, GenerationOptions options, Consumer<String> onToken,
                             Cancellation cancel) {
        boolean withTools = options.hasTools();
        boolean think = withTools && capabilities.contains("thinking");
        List<WireTool> tools = withTools
                ? options.tools().stream().map(t -> new WireTool("function",
                        new WireFunction(t.name(), t.description(), t.parameters()))).toList()
                : null;
        ChatRequest request = new ChatRequest(model, messages.stream().map(OllamaChatAdapter::toWire).toList(), tools,
                true, think, keepAlive, new Options(options.numCtx(), options.numPredict(), options.temperature(), TOP_P));
        try {
            return streamClient.post().uri(baseUrl + "/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(request).exchange((req, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            throw statusFailure(status, response);
                        }
                        InputStream in = response.getBody();
                        cancel.onCancel(() -> closeQuietly(in));
                        return readNdjson(in, onToken, cancel);
                    });
        } catch (ResourceAccessException e) {
            if (cancel.isCancelled()) {
                return Completion.cancelled(cancel.reason());
            }
            if (hasCause(e, HttpTimeoutException.class)) {
                throw new GenerationFailedException("Generation exceeded the stream timeout", e);
            }
            throw new OllamaUnavailableException("Chat backend unreachable", e);
        }
    }

    private static Msg toWire(ChatMessage m) {
        List<WireCall> calls = m.toolCalls() == null ? null : m.toolCalls().stream()
                .map(c -> new WireCall(c.id(), new WireCallFunction(null, c.name(), c.arguments()))).toList();
        return new Msg(m.role().name().toLowerCase(Locale.ROOT), m.content(), calls, m.toolName());
    }

    private static ToolCall toDomain(WireCall call) {
        String id = call.id() == null || call.id().isBlank() ? generateCallId() : call.id();
        WireCallFunction function = call.function();
        return new ToolCall(id, function == null ? null : function.name(),
                function == null || function.arguments() == null ? Map.of() : function.arguments());
    }

    private static String generateCallId() {
        byte[] bytes = new byte[4];
        CALL_ID_RANDOM.nextBytes(bytes);
        return "call_" + HexFormat.of().formatHex(bytes);
    }

    private Completion readNdjson(InputStream in, Consumer<String> onToken, Cancellation cancel) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<ToolCall> calls = new ArrayList<>();
        int lineNumber = 0;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                ChatChunk chunk = parse(line, lineNumber, cancel);
                if (chunk == null) {
                    return Completion.cancelled(cancel.reason());
                }
                if (chunk.error() != null) {
                    throw new GenerationFailedException("Chat backend reported a stream error");
                }
                if (chunk.message() != null && chunk.message().content() != null && !chunk.message().content().isEmpty()) {
                    onToken.accept(chunk.message().content());
                }
                if (chunk.message() != null && chunk.message().toolCalls() != null) {
                    chunk.message().toolCalls().forEach(call -> calls.add(toDomain(call)));
                    if (calls.size() > MAX_CALLS_PER_STREAM) {
                        throw new GenerationFailedException("Chat backend returned too many tool calls");
                    }
                }
                if (chunk.done()) {
                    return new Completion(chunk.doneReason(), chunk.promptEvalCount(), chunk.evalCount(),
                            nsToMs(chunk.loadDuration()), nsToMs(chunk.totalDuration()), null,
                            nsToMs(chunk.evalDuration()), List.copyOf(calls));
                }
            }
        } catch (IOException e) {
            if (cancel.isCancelled()) {
                return Completion.cancelled(cancel.reason());
            }
            throw new GenerationFailedException("Chat stream read failed", e);
        }
        // Closing the stream from another thread usually surfaces as EOF rather than an IOException
        if (cancel.isCancelled()) {
            return Completion.cancelled(cancel.reason());
        }
        throw new GenerationFailedException("Chat stream ended without a done message");
    }

    private ChatChunk parse(String line, int lineNumber, Cancellation cancel) {
        try {
            return objectMapper.readValue(line, ChatChunk.class);
        } catch (JacksonException e) {
            if (cancel.isCancelled()) {
                return null;
            }
            throw new GenerationFailedException("Malformed stream line " + lineNumber);
        }
    }

    private RuntimeException statusFailure(int status, ClientHttpResponse response) throws IOException {
        if (status == 404) {
            return new ModelNotAvailableException(model);
        }
        if (status == 400) {
            String peek = new String(response.getBody().readNBytes(ERROR_BODY_PEEK_BYTES), StandardCharsets.UTF_8);
            return peek.contains("does not support chat") || peek.contains("does not support tools")
                    ? new ModelNotAvailableException(model)
                    : new GenerationFailedException("Chat request rejected with 400");
        }
        if (status >= 500) {
            return new OllamaUnavailableException("Chat backend error " + status);
        }
        return new GenerationFailedException("Chat call failed with " + status);
    }

    private static Long nsToMs(Long nanos) {
        return nanos == null ? null : nanos / NANOS_PER_MILLI;
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // closing is the cancellation signal; a failing close changes nothing
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatRequest(String model, List<Msg> messages, List<WireTool> tools, boolean stream, boolean think,
                               @JsonProperty("keep_alive") String keepAlive, Options options) {
    }

    private record WireTool(String type, WireFunction function) {
        // SAFE-03: content-free toString, Spring DEBUG logs the outbound body object
        @Override
        public String toString() {
            return "WireTool[name=" + (function == null ? null : function.name()) + "]";
        }
    }

    private record WireFunction(String name, String description, Map<String, Object> parameters) {
        @Override
        public String toString() {
            return "WireFunction[name=" + name + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireCall(String id, WireCallFunction function) {
        @Override
        public String toString() {
            return "WireCall[function=" + function + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireCallFunction(Integer index, String name, Map<String, Object> arguments) {
        @Override
        public String toString() {
            return "WireCallFunction[name=" + name + ", arguments=" + (arguments == null ? 0 : arguments.size()) + "]";
        }
    }

    private record Options(@JsonProperty("num_ctx") int numCtx, @JsonProperty("num_predict") int numPredict,
                           double temperature, @JsonProperty("top_p") double topP) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Msg(String role, String content, @JsonProperty("tool_calls") List<WireCall> toolCalls,
                       @JsonProperty("tool_name") String toolName) {
        // SAFE-03: Spring's DEBUG logging of the outbound body calls toString; keep prompt content out of it
        @Override
        public String toString() {
            return "Msg[role=" + role + ", contentChars=" + (content == null ? 0 : content.length())
                    + ", toolCalls=" + (toolCalls == null ? 0 : toolCalls.size()) + "]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChunkMsg(String content, @JsonProperty("tool_calls") List<WireCall> toolCalls) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChunk(ChunkMsg message, boolean done, String error,
                             @JsonProperty("done_reason") String doneReason,
                             @JsonProperty("prompt_eval_count") Integer promptEvalCount,
                             @JsonProperty("eval_count") Integer evalCount,
                             @JsonProperty("load_duration") Long loadDuration,
                             @JsonProperty("total_duration") Long totalDuration,
                             @JsonProperty("eval_duration") Long evalDuration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ShowResponse(List<String> capabilities) {
    }
}
