package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.adapters.web.CollectionController;
import com.evision.ragservice.application.ChatService;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolDefinition;
import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolSource;
import com.evision.ragservice.domain.ToolTurn;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ChatRequest(
        @NotEmpty @Size(max = ChatRequest.MAX_COLLECTIONS)
        List<@NotNull @Pattern(regexp = CollectionController.NAME_PATTERN) String> collections,
        @NotBlank String question,
        List<@NotNull @Valid Turn> history,
        Map<String, Object> filter,
        Integer k,
        @Size(max = ChatRequest.MAX_TOOLS) List<@NotNull @Valid Tool> tools,
        @Size(max = ChatRequest.MAX_TOOL_TURNS) List<@NotNull @Valid ToolTurnDto> toolTurns) {

    public static final int MAX_COLLECTIONS = 10;
    // Wire backstops; the configured caps are enforced by the service with clearer messages
    public static final int MAX_TOOLS = 20;
    public static final int MAX_TOOL_TURNS = 20;
    public static final int MAX_CALLS = 16;

    public record Turn(@NotNull @Pattern(regexp = "user|assistant") String role, @NotBlank String content) {
        @Override
        public String toString() {
            return "Turn[role=" + role + ", contentChars=" + (content == null ? 0 : content.length()) + "]";
        }
    }

    public record Tool(@NotNull @Pattern(regexp = "[A-Za-z0-9_]{1,64}") String name,
                       @Size(max = 300) String description, @NotNull Map<String, Object> parameters) {
        @Override
        public String toString() {
            return "Tool[name=" + name + "]";
        }
    }

    public record ToolTurnDto(@Min(1) int round, @NotBlank @Size(max = 64) String questionId,
                              @NotEmpty @Size(max = MAX_CALLS) List<@NotNull @Valid Call> calls,
                              @NotBlank @Size(max = 128) String signature,
                              @NotEmpty @Size(max = MAX_CALLS) List<@NotNull @Valid Result> results) {
        @Override
        public String toString() {
            return "ToolTurnDto[round=" + round + ", calls=" + (calls == null ? 0 : calls.size()) + ", results="
                    + (results == null ? 0 : results.size()) + "]";
        }
    }

    public record Call(@NotBlank @Size(max = 64) String id, @NotNull @Size(max = 64) String name,
                       @NotNull Map<String, Object> arguments) {
        @Override
        public String toString() {
            return "Call[argumentCount=" + (arguments == null ? 0 : arguments.size()) + "]";
        }
    }

    public record Result(@NotBlank @Size(max = 64) String callId, @NotNull String content,
                         @JsonProperty("isError") boolean isError, @Valid Source source) {
        @Override
        public String toString() {
            return "Result[contentChars=" + (content == null ? 0 : content.length()) + ", isError=" + isError + "]";
        }
    }

    public record Source(@NotBlank @Size(max = 200) String id, @Size(max = 300) String title,
                         @Pattern(regexp = "https?://\\S{1,2040}") String url) {
        @Override
        public String toString() {
            return "Source[]";
        }
    }

    // SAFE-03: Spring's DEBUG logging of the request body calls toString; keep question, history, filter and tool
    // content out of it
    @Override
    public String toString() {
        return "ChatRequest[collections=" + (collections == null ? 0 : collections.size()) + ", questionChars="
                + (question == null ? 0 : question.length()) + ", turns=" + (history == null ? 0 : history.size())
                + ", tools=" + (tools == null ? 0 : tools.size()) + ", toolTurns="
                + (toolTurns == null ? 0 : toolTurns.size()) + "]";
    }

    public ChatService.Command toCommand() {
        List<ChatMessage> turns = history == null ? List.of() : history.stream()
                .map(t -> new ChatMessage(
                        "user".equals(t.role()) ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT, t.content()))
                .toList();
        List<ToolDefinition> definitions = tools == null ? List.of() : tools.stream()
                .map(t -> new ToolDefinition(t.name(), t.description(), t.parameters())).toList();
        List<ToolTurn> rounds = toolTurns == null ? List.of() : toolTurns.stream().map(ChatRequest::toDomain).toList();
        return new ChatService.Command(collections, question, turns, filter, k, definitions, rounds);
    }

    private static ToolTurn toDomain(ToolTurnDto turn) {
        return new ToolTurn(turn.round(), turn.questionId(),
                turn.calls().stream().map(c -> new ToolCall(c.id(), c.name(), c.arguments())).toList(),
                turn.signature(),
                turn.results().stream().map(r -> new ToolResult(r.callId(), r.content(), r.isError(),
                        r.source() == null ? null
                                : new ToolSource(r.source().id(), r.source().title(), r.source().url()))).toList());
    }
}
