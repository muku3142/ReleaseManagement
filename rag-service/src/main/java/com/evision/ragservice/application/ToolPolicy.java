package com.evision.ragservice.application;

import com.evision.ragservice.domain.MetadataRules;
import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolDefinition;
import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolResultTooLargeException;
import com.evision.ragservice.domain.ToolSchema;
import com.evision.ragservice.domain.ToolTurn;
import com.evision.ragservice.domain.ToolTurnRejectedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ToolPolicy {

    private final int maxRounds;
    private final int maxCalls;
    private final int resultMaxChars;

    public ToolPolicy(int maxRounds, int maxCalls, int resultMaxChars) {
        this.maxRounds = maxRounds;
        this.maxCalls = maxCalls;
        this.resultMaxChars = resultMaxChars;
    }

    /** Problems texts go back into the prompt: they name only validated tool names, never values or model-made names. */
    public List<String> check(List<ToolCall> calls, List<ToolDefinition> definitions, List<ToolTurn> answered) {
        List<String> problems = new ArrayList<>();
        if (calls.size() > maxCalls) {
            problems.add("too many tool calls: at most " + maxCalls + " per round");
        }
        Set<String> ids = new HashSet<>();
        boolean idProblem = false;
        for (ToolCall call : calls) {
            idProblem |= call.id() == null || call.id().isBlank() || !ids.add(call.id());
        }
        if (idProblem) {
            problems.add("duplicate or missing call id");
        }
        Map<String, ToolDefinition> byName = definitions.stream()
                .collect(Collectors.toMap(ToolDefinition::name, Function.identity()));
        for (ToolCall call : calls) {
            ToolDefinition definition = byName.get(call.name());
            if (definition == null) {
                problems.add("unknown tool; available tools: "
                        + definitions.stream().map(ToolDefinition::name).collect(Collectors.joining(", ")));
                continue;
            }
            ToolSchema.violations(definition.parameters(), call.arguments())
                    .forEach(v -> problems.add("tool '" + call.name() + "': " + v));
            if (alreadyAnswered(call, answered)) {
                problems.add("tool '" + call.name()
                        + "' was already called with these arguments; use its result above");
            }
        }
        return problems;
    }

    public void requireValidResume(List<ToolTurn> turns, List<ToolDefinition> definitions) {
        if (turns.isEmpty()) {
            return;
        }
        if (turns.size() > maxRounds) {
            throw new ToolTurnRejectedException("at most " + maxRounds + " tool rounds");
        }
        if (definitions == null || definitions.isEmpty()) {
            throw new ToolTurnRejectedException("tool turns require tool definitions");
        }
        Set<String> names = definitions.stream().map(ToolDefinition::name).collect(Collectors.toSet());
        for (int i = 0; i < turns.size(); i++) {
            requireWellFormed(i + 1, turns.get(i), names);
        }
    }

    public boolean isFinalRound(List<ToolTurn> turns) {
        return turns.size() >= maxRounds;
    }

    private void requireWellFormed(int number, ToolTurn turn, Set<String> toolNames) {
        List<ToolCall> calls = turn.calls();
        List<ToolResult> results = turn.results();
        if (calls == null || calls.isEmpty() || results == null) {
            throw new ToolTurnRejectedException("tool turn " + number + " has no calls");
        }
        Set<String> callIds = new HashSet<>();
        for (ToolCall call : calls) {
            if (call.id() == null || !callIds.add(call.id()) || !toolNames.contains(call.name())) {
                throw new ToolTurnRejectedException("tool turn " + number + " calls are not valid");
            }
        }
        Set<String> resultIds = new HashSet<>();
        for (ToolResult result : results) {
            if (result.callId() == null || !resultIds.add(result.callId())) {
                throw new ToolTurnRejectedException("tool turn " + number + " results do not match its calls");
            }
        }
        if (!resultIds.equals(callIds)) {
            throw new ToolTurnRejectedException("tool turn " + number + " results do not match its calls");
        }
        for (ToolResult result : results) {
            String content = result.content() == null ? "" : result.content();
            if (content.length() > resultMaxChars) {
                throw new ToolResultTooLargeException("tool result exceeds " + resultMaxChars + " characters");
            }
            MetadataRules.rejectNul("tool result", content);
        }
    }

    private static boolean alreadyAnswered(ToolCall call, List<ToolTurn> answered) {
        Object arguments = ToolTurnSigner.canonical(call.arguments());
        return answered.stream()
                .flatMap(turn -> turn.calls().stream())
                .anyMatch(earlier -> earlier.name().equals(call.name())
                        && arguments.equals(ToolTurnSigner.canonical(earlier.arguments())));
    }
}
