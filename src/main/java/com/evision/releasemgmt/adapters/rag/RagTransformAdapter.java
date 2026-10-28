package com.evision.releasemgmt.adapters.rag;

import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.ports.RagChatPort.ChatListener;
import com.evision.releasemgmt.ports.RagTransformPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.evision.releasemgmt.adapters.rag.ListenerFailure.notifyListener;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class RagTransformAdapter implements RagTransformPort {

    private static final String TRANSFORM_URI = "/v1/transform";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final int BAD_REQUEST = 400;
    private static final int PAYLOAD_TOO_LARGE = 413;
    private static final Pattern SECONDS = Pattern.compile("\\d{1,9}");
    private static final Pattern MAX_CHARS = Pattern.compile("maximum of (\\d{1,9}) characters");

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public RagTransformAdapter(@Qualifier("ragChatRestClient") RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public TransformOutcome transform(String text, String instruction, ChatListener listener) {
        try {
            String body = objectMapper.writeValueAsString(new TransformWire(text, instruction));
            return client.post().uri(TRANSFORM_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((req, res) -> res.getStatusCode().is2xxSuccessful()
                            ? readStream(res.getBody(), listener)
                            : failure(res.getStatusCode().value(), res.getHeaders(), res.getBody()));
        } catch (ListenerFailure f) {
            // the exchange lambda has already exited, so the upstream response is closed and rag-service frees its slot
            throw f.listenerException();
        } catch (ResourceAccessException e) {
            return failed(RagChatAdapter.isTimeout(e) ? FailureReason.DEADLINE : FailureReason.OFFLINE);
        } catch (JacksonException e) {
            return failed(FailureReason.FAILED);
        }
    }

    private static TransformOutcome failed(FailureReason reason) {
        return new TransformOutcome.Failed(reason, null);
    }

    private TransformOutcome failure(int status, HttpHeaders headers, InputStream problem) {
        if (status == TOO_MANY_REQUESTS) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            return new TransformOutcome.Failed(FailureReason.BUSY,
                    retryAfter != null && SECONDS.matcher(retryAfter).matches() ? Integer.valueOf(retryAfter) : null);
        }
        if (status == PAYLOAD_TOO_LARGE) {
            return new TransformOutcome.TooLarge(maxChars(problem));
        }
        return failed(switch (status) {
            case SERVICE_UNAVAILABLE -> "MODEL_NOT_AVAILABLE".equals(problemField(problem, "reason"))
                    ? FailureReason.MODEL_MISSING : FailureReason.OFFLINE;
            case BAD_REQUEST -> FailureReason.REJECTED;
            default -> FailureReason.FAILED;
        });
    }

    private Integer maxChars(InputStream problem) {
        Matcher matcher = MAX_CHARS.matcher(problemField(problem, "detail"));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String problemField(InputStream problem, String field) {
        try {
            return objectMapper.readTree(problem).path(field).asString("");
        } catch (JacksonException e) {
            return "";
        }
    }

    private TransformOutcome readStream(InputStream stream, ChatListener listener) throws IOException {
        TransformResult result = new TransformResult();
        try {
            SseStreamReader.read(stream, new SseStreamReader.Handler() {
                @Override
                public void onComment() {
                    notifyListener(listener::heartbeat);
                }

                @Override
                public boolean onEvent(String event, String data) {
                    return handle(event, data, result, listener);
                }
            });
        } catch (JacksonException e) {
            return failed(FailureReason.FAILED);
        }
        return result.outcome != null ? result.outcome : failed(FailureReason.FAILED);
    }

    // Returns true when the stream is finished (done or error). Unknown events, including status, are ignored.
    private boolean handle(String event, String data, TransformResult result, ChatListener listener) {
        switch (event) {
            case "token" -> {
                String text = objectMapper.readTree(data).path("text").asString("");
                notifyListener(() -> listener.token(text));
            }
            case "done" -> {
                JsonNode done = objectMapper.readTree(data);
                result.outcome = new TransformOutcome.Done(done.path("finishReason").asString(""),
                        done.path("truncated").asBoolean(false), tokenCount(done, "promptTokens"),
                        tokenCount(done, "completionTokens"));
                return true;
            }
            case "error" -> {
                result.outcome = failed(streamError(objectMapper.readTree(data).path("reason").asString("")));
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private static Integer tokenCount(JsonNode done, String field) {
        return done.hasNonNull(field) ? done.get(field).asInt() : null;
    }

    private static FailureReason streamError(String reason) {
        return switch (reason) {
            case "DEADLINE" -> FailureReason.DEADLINE;
            case "OLLAMA_UNAVAILABLE" -> FailureReason.OFFLINE;
            case "MODEL_NOT_AVAILABLE" -> FailureReason.MODEL_MISSING;
            default -> FailureReason.FAILED;
        };
    }

    private static final class TransformResult {
        TransformOutcome outcome;
    }

    private record TransformWire(String text, String instruction) {

        @Override
        public String toString() {
            return "TransformWire[redacted]";
        }
    }
}
