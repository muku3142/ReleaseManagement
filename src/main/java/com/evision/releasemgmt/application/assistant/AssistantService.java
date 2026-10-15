package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.application.assistant.tools.ToolArgumentException;
import com.evision.releasemgmt.application.assistant.tools.ToolOutput;
import com.evision.releasemgmt.application.assistant.tools.ToolRegistry;
import com.evision.releasemgmt.domain.assistant.AnsweredTurn;
import com.evision.releasemgmt.domain.assistant.AssistantAnswer;
import com.evision.releasemgmt.domain.assistant.ChatMessage;
import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ChatRequest;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.ToolCall;
import com.evision.releasemgmt.domain.assistant.ToolDefinition;
import com.evision.releasemgmt.domain.assistant.ToolResult;
import com.evision.releasemgmt.domain.assistant.ToolRun;
import com.evision.releasemgmt.ports.RagChatPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Caller side of the stateless tool loop: ask, run the requested tools in-process, resend the signed turn
 * unchanged. Stores nothing; logs only tool name, round, outcome and duration.
 */
@Service
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String UNKNOWN_TOOL = "unknown tool";
    private static final String TOOL_FAILED = "tool failed";
    private static final String RESULT_TOO_LARGE = "result too large, narrow the arguments";

    private final RagChatPort chatPort;
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final int maxRounds;
    private final int toolResultMaxChars;

    public AssistantService(RagChatPort chatPort, ToolRegistry registry, ObjectMapper objectMapper,
                            @Value("${app.assistant.max-rounds}") int maxRounds,
                            @Value("${app.assistant.tool-result-max-chars}") int toolResultMaxChars) {
        this.chatPort = chatPort;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.maxRounds = maxRounds;
        this.toolResultMaxChars = toolResultMaxChars;
    }

    public AssistantAnswer ask(String question, List<ChatMessage> history, UUID releaseIdOrNull,
                               RagChatPort.ChatListener listener) {
        List<ToolDefinition> definitions = registry.tools().stream()
                .map(tool -> new ToolDefinition(tool.name(), tool.description(), tool.parameters()))
                .toList();
        List<IndexCollection> collections = List.of(IndexCollection.values());
        Map<String, Object> filter = releaseIdOrNull == null ? null : Map.of("release_id", releaseIdOrNull.toString());
        List<AnsweredTurn> turns = new ArrayList<>();
        List<ToolRun> toolsRun = new ArrayList<>();

        // one POST beyond maxRounds: the last chance for the model to answer once its tool rounds are spent
        for (int attempt = 1; attempt <= maxRounds + 1; attempt++) {
            ChatOutcome outcome = chatPort.chat(
                    new ChatRequest(question, history, collections, filter, definitions, List.copyOf(turns)), listener);
            switch (outcome) {
                case ChatOutcome.Answered answered -> {
                    return new AssistantAnswer(answered.text(), answered.citations(), List.copyOf(toolsRun),
                            answered.refused(), null);
                }
                case ChatOutcome.Failed failed -> {
                    return failure(failed.reason(), toolsRun);
                }
                case ChatOutcome.ToolCallsRequested requested -> {
                    listener.roundDiscarded();
                    List<ToolResult> results = new ArrayList<>();
                    for (ToolCall call : requested.turn().calls()) {
                        results.add(execute(attempt, call, toolsRun));
                    }
                    turns.add(new AnsweredTurn(requested.turn(), results));
                }
            }
        }
        return failure(FailureReason.FAILED, toolsRun);
    }

    private AssistantAnswer failure(FailureReason reason, List<ToolRun> toolsRun) {
        return new AssistantAnswer(null, List.of(), List.copyOf(toolsRun), false, reason);
    }

    private ToolResult execute(int round, ToolCall call, List<ToolRun> toolsRun) {
        long start = System.nanoTime();
        ToolResult result;
        Optional<AssistantTool> tool = registry.find(call.name());
        if (tool.isEmpty()) {
            result = error(call, UNKNOWN_TOOL);
        } else {
            result = run(tool.get(), call);
        }
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        toolsRun.add(new ToolRun(round, call.name(), call.arguments(), result.isError(), millis));
        log.info("assistant tool round={} name={} error={} durationMs={}", round, call.name(), result.isError(), millis);
        return result;
    }

    private ToolResult run(AssistantTool tool, ToolCall call) {
        try {
            ToolOutput output = tool.call(call.arguments());
            String content = objectMapper.writeValueAsString(output.result());
            if (content.length() > toolResultMaxChars) {
                return error(call, RESULT_TOO_LARGE);
            }
            return new ToolResult(call.id(), content, false, output.sourceId(), output.sourceTitle());
        } catch (ToolArgumentException e) {
            return error(call, e.getMessage());
        } catch (RuntimeException e) {
            return error(call, TOOL_FAILED);
        }
    }

    private ToolResult error(ToolCall call, String message) {
        return new ToolResult(call.id(), objectMapper.writeValueAsString(Map.of("error", message)), true, null, null);
    }
}
