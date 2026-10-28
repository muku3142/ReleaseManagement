package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AssistantChatRequest(
        @NotBlank
        // leaves room for the scoped-question prefix under rag-service's 2000-char question limit
        @Size(max = AssistantChatRequest.QUESTION_MAX_CHARS) String question,
        // payload bound only; AssistantService sends the newest 20
        @Size(max = AssistantChatRequest.HISTORY_MAX_MESSAGES) List<@Valid HistoryMessage> history,
        UUID releaseId) {

    public static final int QUESTION_MAX_CHARS = 1500;
    public static final int HISTORY_MAX_MESSAGES = 200;

    public AssistantChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public record HistoryMessage(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            // rag.chat.history-turn-max-chars
            @NotBlank @Size(max = 4000) String content) {

        @Override
        public String toString() {
            return "HistoryMessage[" + role + "]";
        }
    }

    @Override
    public String toString() {
        return "AssistantChatRequest[history=" + history.size() + ", releaseId=" + releaseId + "]";
    }
}
