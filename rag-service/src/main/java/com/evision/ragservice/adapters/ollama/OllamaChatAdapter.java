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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OllamaChatAdapter implements ChatPort {

    private static final double TOP_P = 0.8;
    private static final int ERROR_BODY_PEEK_BYTES = 1024;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final RestClient streamClient;
    private final RestClient healthClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final String keepAlive;

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
        if (show == null || show.capabilities() == null || !show.capabilities().contains("completion")) {
            throw new ModelNotAvailableException(model);
        }
    }

    @Override
    public Completion stream(List<ChatMessage> messages, GenerationOptions options, Consumer<String> onToken,
                             Cancellation cancel) {
        ChatRequest request = new ChatRequest(model,
                messages.stream().map(m -> new Msg(m.role().name().toLowerCase(Locale.ROOT), m.content())).toList(),
                true, false, keepAlive, new Options(options.numCtx(), options.numPredict(), options.temperature(), TOP_P));
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

    private Completion readNdjson(InputStream in, Consumer<String> onToken, Cancellation cancel) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
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
                if (chunk.done()) {
                    return new Completion(chunk.doneReason(), chunk.promptEvalCount(), chunk.evalCount(),
                            nsToMs(chunk.loadDuration()), nsToMs(chunk.totalDuration()), null);
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
            return peek.contains("does not support chat")
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

    private record ChatRequest(String model, List<Msg> messages, boolean stream, boolean think,
                               @JsonProperty("keep_alive") String keepAlive, Options options) {
    }

    private record Options(@JsonProperty("num_ctx") int numCtx, @JsonProperty("num_predict") int numPredict,
                           double temperature, @JsonProperty("top_p") double topP) {
    }

    private record Msg(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChunkMsg(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChunk(ChunkMsg message, boolean done, String error,
                             @JsonProperty("done_reason") String doneReason,
                             @JsonProperty("prompt_eval_count") Integer promptEvalCount,
                             @JsonProperty("eval_count") Integer evalCount,
                             @JsonProperty("load_duration") Long loadDuration,
                             @JsonProperty("total_duration") Long totalDuration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ShowResponse(List<String> capabilities) {
    }
}
