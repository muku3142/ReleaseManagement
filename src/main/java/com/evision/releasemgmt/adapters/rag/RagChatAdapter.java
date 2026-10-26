package com.evision.releasemgmt.adapters.rag;

import com.evision.releasemgmt.domain.assistant.AnsweredTurn;
import com.evision.releasemgmt.domain.assistant.ChatMessage;
import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ChatRequest;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.SignedTurn;
import com.evision.releasemgmt.domain.assistant.Source;
import com.evision.releasemgmt.domain.assistant.ToolCall;
import com.evision.releasemgmt.domain.assistant.ToolDefinition;
import com.evision.releasemgmt.domain.assistant.ToolResult;
import com.evision.releasemgmt.ports.RagChatPort;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.evision.releasemgmt.adapters.rag.ListenerFailure.notifyListener;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class RagChatAdapter implements RagChatPort {

    private static final String CHAT_URI = "/v1/chat";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final int BAD_REQUEST = 400;
    private static final int PAYLOAD_TOO_LARGE = 413;
    private static final Pattern SECONDS = Pattern.compile("\\d{1,9}");

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public RagChatAdapter(@Qualifier("ragChatRestClient") RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatOutcome chat(ChatRequest request, ChatListener listener) {
        try {
            String body = objectMapper.writeValueAsString(body(request));
            return client.post().uri(CHAT_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((req, res) -> res.getStatusCode().is2xxSuccessful()
                            ? readStream(res.getBody(), listener)
                            : failure(res.getStatusCode().value(), res.getHeaders(), res.getBody()));
        } catch (ListenerFailure f) {
            // the exchange lambda has already exited, so the upstream response is closed and rag-service sees CLIENT_GONE
            throw f.listenerException();
        } catch (ResourceAccessException e) {
            return failed(isTimeout(e) ? FailureReason.DEADLINE : FailureReason.OFFLINE);
        } catch (JacksonException e) {
            return failed(FailureReason.FAILED);
        }
    }

    private static ChatOutcome failed(FailureReason reason) {
        return new ChatOutcome.Failed(reason, null);
    }

    private ObjectNode body(ChatRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode collections = body.putArray("collections");
        for (IndexCollection collection : request.collections()) {
            collections.add(collection.wireName());
        }
        body.put("question", request.question());
        ArrayNode history = body.putArray("history");
        for (ChatMessage message : request.history()) {
            history.addObject().put("role", message.role()).put("content", message.content());
        }
        if (request.filter() != null) {
            body.set("filter", objectMapper.valueToTree(request.filter()));
        }
        ArrayNode tools = body.putArray("tools");
        for (ToolDefinition tool : request.tools()) {
            ObjectNode node = tools.addObject();
            node.put("name", tool.name()).put("description", tool.description());
            node.set("parameters", objectMapper.valueToTree(tool.parameters()));
        }
        ArrayNode toolTurns = body.putArray("toolTurns");
        for (AnsweredTurn turn : request.turns()) {
            toolTurns.add(toolTurn(turn));
        }
        return body;
    }

    // The signed fields are copied from the received tool_call data untouched; only results are added.
    private ObjectNode toolTurn(AnsweredTurn answered) {
        JsonNode received = objectMapper.readTree(answered.turn().rawJson());
        ObjectNode turn = objectMapper.createObjectNode();
        for (String field : List.of("round", "questionId", "calls", "signature")) {
            turn.set(field, received.get(field));
        }
        ArrayNode results = turn.putArray("results");
        for (ToolResult result : answered.results()) {
            ObjectNode node = results.addObject();
            node.put("callId", result.callId()).put("content", result.content()).put("isError", result.isError());
            if (result.sourceId() != null) {
                node.putObject("source").put("id", result.sourceId()).put("title", result.sourceTitle());
            }
        }
        return turn;
    }

    private ChatOutcome failure(int status, HttpHeaders headers, InputStream problem) {
        if (status == TOO_MANY_REQUESTS) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            return new ChatOutcome.Failed(FailureReason.BUSY,
                    retryAfter != null && SECONDS.matcher(retryAfter).matches() ? Integer.valueOf(retryAfter) : null);
        }
        return failed(switch (status) {
            case SERVICE_UNAVAILABLE -> "MODEL_NOT_AVAILABLE".equals(problemReason(problem))
                    ? FailureReason.MODEL_MISSING : FailureReason.OFFLINE;
            case BAD_REQUEST, PAYLOAD_TOO_LARGE -> FailureReason.REJECTED;
            default -> FailureReason.FAILED;
        });
    }

    private String problemReason(InputStream problem) {
        try {
            return objectMapper.readTree(problem).path("reason").asString("");
        } catch (JacksonException e) {
            return "";
        }
    }

    private ChatOutcome readStream(InputStream stream, ChatListener listener) throws IOException {
        StreamState state = new StreamState();
        try {
            SseStreamReader.read(stream, new SseStreamReader.Handler() {
                @Override
                public void onComment() {
                    notifyListener(listener::heartbeat);
                }

                @Override
                public boolean onEvent(String event, String data) {
                    return handle(event, data, state, listener);
                }
            });
        } catch (JacksonException e) {
            return failed(FailureReason.FAILED);
        }
        return state.outcome();
    }

    // Returns true when the stream is finished (done or error). Comment lines and unknown events are ignored.
    private boolean handle(String event, String data, StreamState state, ChatListener listener) {
        switch (event) {
            case "token" -> {
                String text = objectMapper.readTree(data).path("text").asString("");
                state.text.append(text);
                notifyListener(() -> listener.token(text));
            }
            case "citations" -> state.citations = citations(objectMapper.readTree(data).path("items"));
            case "tool_call" -> state.turn = new SignedTurn(data, calls(objectMapper.readTree(data).path("calls")));
            case "done" -> {
                JsonNode done = objectMapper.readTree(data);
                state.refused = done.path("refusal").asBoolean(false);
                state.finishReason = done.path("finishReason").asString("");
                state.historyTrimmed = done.path("historyTrimmed").asBoolean(false);
                state.model = done.path("model").asString(null);
                state.promptHash = done.path("promptHash").asString(null);
                state.done = true;
                return true;
            }
            case "error" -> {
                state.error = streamError(objectMapper.readTree(data).path("reason").asString(""));
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private List<Source> citations(JsonNode items) {
        List<Source> sources = new ArrayList<>();
        for (JsonNode item : items) {
            sources.add(new Source(item.path("n").asInt(), item.path("collection").asString(null),
                    item.path("documentId").asString(null), item.path("title").asString(null),
                    item.path("url").asString(null), item.path("kind").asString(null),
                    item.hasNonNull("metadata") ? asMap(item.get("metadata")) : Map.of()));
        }
        return sources;
    }

    private List<ToolCall> calls(JsonNode calls) {
        List<ToolCall> result = new ArrayList<>();
        for (JsonNode call : calls) {
            result.add(new ToolCall(call.path("id").asString(), call.path("name").asString(),
                    asMap(call.path("arguments"))));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }

    private static FailureReason streamError(String reason) {
        return switch (reason) {
            case "DEADLINE" -> FailureReason.DEADLINE;
            case "OLLAMA_UNAVAILABLE", "EMBEDDING_UNAVAILABLE" -> FailureReason.OFFLINE;
            case "MODEL_NOT_AVAILABLE" -> FailureReason.MODEL_MISSING;
            default -> FailureReason.FAILED;
        };
    }

    static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static final class StreamState {
        final StringBuilder text = new StringBuilder();
        List<Source> citations = List.of();
        SignedTurn turn;
        boolean refused;
        String finishReason;
        boolean historyTrimmed;
        String model;
        String promptHash;
        boolean done;
        FailureReason error;

        ChatOutcome outcome() {
            if (error != null) {
                return failed(error);
            }
            if (!done) {
                return failed(FailureReason.FAILED);
            }
            if (turn != null) {
                return new ChatOutcome.ToolCallsRequested(turn);
            }
            return new ChatOutcome.Answered(text.toString(), citations, refused, finishReason, historyTrimmed, model,
                    promptHash);
        }
    }
}
