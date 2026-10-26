package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.application.assistant.tools.ToolArgumentException;
import com.evision.releasemgmt.application.assistant.tools.ToolOutput;
import com.evision.releasemgmt.application.assistant.tools.ToolRegistry;
import com.evision.releasemgmt.config.AssistantProperties;
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
import com.evision.releasemgmt.domain.release.Release;
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
    private final ReleaseService releaseService;
    private final AssistantProperties properties;

    public AssistantService(RagChatPort chatPort, ToolRegistry registry, ObjectMapper objectMapper,
                            @Value("${app.assistant.max-rounds}") int maxRounds,
                            @Value("${app.assistant.tool-result-max-chars}") int toolResultMaxChars,
                            ReleaseService releaseService, AssistantProperties properties) {
        this.chatPort = chatPort;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.maxRounds = maxRounds;
        this.toolResultMaxChars = toolResultMaxChars;
        this.releaseService = releaseService;
        this.properties = properties;
    }

    public AssistantAnswer ask(String question, List<ChatMessage> history, UUID releaseIdOrNull,
                               RagChatPort.ChatListener listener) {
        Release scope = releaseIdOrNull == null ? null : releaseService.get(releaseIdOrNull);
        String sentQuestion = scope == null ? question : scopedQuestion(scope, question);
        // a scoped chat only gets tools that can be pinned to the release; the others would leak across releases
        List<AssistantTool> offered = registry.tools().stream()
                .filter(tool -> scope == null || declaresRelease(tool))
                .toList();
        List<ToolDefinition> definitions = offered.stream()
                .map(tool -> new ToolDefinition(tool.name(),
                        properties.prompts().toolDescriptions().getOrDefault(tool.name(), tool.description()),
                        tool.parameters()))
                .toList();
        int maxHistory = properties.historyMaxTurns();
        boolean trimmed = history.size() > maxHistory;
        List<ChatMessage> sentHistory = trimmed
                ? List.copyOf(history.subList(history.size() - maxHistory, history.size())) : history;
        List<IndexCollection> collections = List.of(IndexCollection.values());
        Map<String, Object> filter = scope == null ? null : Map.of("release_id", scope.id().toString());
        List<AnsweredTurn> turns = new ArrayList<>();
        List<ToolRun> toolsRun = new ArrayList<>();
        List<String> argumentErrors = new ArrayList<>();

        // one POST beyond maxRounds: the last chance for the model to answer once its tool rounds are spent
        for (int attempt = 1; attempt <= maxRounds + 1; attempt++) {
            ChatOutcome outcome = chatPort.chat(
                    new ChatRequest(sentQuestion, sentHistory, collections, filter, definitions, List.copyOf(turns)),
                    listener);
            switch (outcome) {
                case ChatOutcome.Answered answered -> {
                    if (answered.refused() && !argumentErrors.isEmpty()) {
                        return toolErrorAnswer(answered, argumentErrors, toolsRun, trimmed, listener);
                    }
                    return new AssistantAnswer(answered.text(), answered.citations(), List.copyOf(toolsRun),
                            answered.refused(), null, trimmed || answered.historyTrimmed(),
                            answered.model(), answered.promptHash(), null);
                }
                case ChatOutcome.Failed failed -> {
                    return failure(failed.reason(), failed.retryAfterSeconds(), toolsRun, trimmed);
                }
                case ChatOutcome.ToolCallsRequested requested -> {
                    listener.roundDiscarded();
                    List<ToolResult> results = new ArrayList<>();
                    for (ToolCall call : requested.turn().calls()) {
                        results.add(execute(attempt, call, offered, scope, listener, toolsRun, argumentErrors));
                    }
                    turns.add(new AnsweredTurn(requested.turn(), results));
                }
            }
        }
        return failure(FailureReason.FAILED, null, toolsRun, trimmed);
    }

    // Single pass over the template: neither a release name containing "{question}" nor a question containing
    // "{release}" gets substituted a second time. The template is validated to hold both placeholders.
    private String scopedQuestion(Release scope, String question) {
        String template = properties.prompts().scopedQuestion();
        String releaseText = scope.name() + " " + scope.version();
        int at = template.indexOf("{question}");
        return template.substring(0, at).replace("{release}", releaseText) + question
                + template.substring(at + "{question}".length()).replace("{release}", releaseText);
    }

    private static boolean declaresRelease(AssistantTool tool) {
        return tool.parameters().get("properties") instanceof Map<?, ?> properties && properties.containsKey("release");
    }

    private AssistantAnswer failure(FailureReason reason, Integer retryAfterSeconds, List<ToolRun> toolsRun,
                                    boolean trimmed) {
        return new AssistantAnswer(null, List.of(), List.copyOf(toolsRun), false, reason, trimmed, null, null,
                retryAfterSeconds);
    }

    private ToolResult execute(int round, ToolCall call, List<AssistantTool> offered, Release scope,
                               RagChatPort.ChatListener listener, List<ToolRun> toolsRun,
                               List<String> argumentErrors) {
        long start = System.nanoTime();
        ToolResult result;
        Map<String, Object> arguments = call.arguments();
        Optional<AssistantTool> tool = offered.stream().filter(t -> t.name().equals(call.name())).findFirst();
        if (tool.isEmpty()) {
            result = error(call, UNKNOWN_TOOL);
        } else {
            if (scope != null) {
                arguments = Map.of("release", scope.id().toString());
            }
            // not inside run(): listener exceptions (ClientGoneException) must reach the caller, D-13-10
            listener.toolStarted(tool.get().name());
            result = run(tool.get(), call, arguments, argumentErrors);
        }
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        toolsRun.add(new ToolRun(round, call.name(), arguments, result.isError(), millis));
        log.info("assistant tool round={} name={} error={} durationMs={}", round, call.name(), result.isError(), millis);
        return result;
    }

    // The refusal tokens already reached the browser and done{} carries no text, so the replacement is streamed
    // as a discard followed by the new text.
    private AssistantAnswer toolErrorAnswer(ChatOutcome.Answered answered, List<String> argumentErrors,
                                            List<ToolRun> toolsRun, boolean trimmed,
                                            RagChatPort.ChatListener listener) {
        String text = properties.prompts().toolErrorAnswer().replace("{error}", String.join("; ", argumentErrors));
        listener.roundDiscarded();
        listener.token(text);
        log.info("assistant answer outcome=TOOL_ERROR_ANSWER errors={}", argumentErrors.size());
        return new AssistantAnswer(text, List.of(), List.copyOf(toolsRun), false, null,
                trimmed || answered.historyTrimmed(), answered.model(), answered.promptHash(), null);
    }

    private ToolResult run(AssistantTool tool, ToolCall call, Map<String, Object> arguments,
                           List<String> argumentErrors) {
        try {
            ToolOutput output = tool.call(arguments);
            String content = objectMapper.writeValueAsString(output.result());
            if (content.length() > toolResultMaxChars) {
                return error(call, RESULT_TOO_LARGE);
            }
            return new ToolResult(call.id(), content, false, output.sourceId(), output.sourceTitle());
        } catch (ToolArgumentException e) {
            if (!argumentErrors.contains(e.getMessage())) {
                argumentErrors.add(e.getMessage());
            }
            return error(call, e.getMessage());
        } catch (RuntimeException e) {
            return error(call, TOOL_FAILED);
        }
    }

    private ToolResult error(ToolCall call, String message) {
        return new ToolResult(call.id(), objectMapper.writeValueAsString(Map.of("error", message)), true, null, null);
    }
}
