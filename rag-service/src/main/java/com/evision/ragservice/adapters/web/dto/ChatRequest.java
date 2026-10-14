package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.adapters.web.CollectionController;
import com.evision.ragservice.application.ChatService;
import com.evision.ragservice.domain.ChatMessage;
import jakarta.validation.Valid;
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
        Integer k) {

    public static final int MAX_COLLECTIONS = 10;

    public record Turn(@NotNull @Pattern(regexp = "user|assistant") String role, @NotBlank String content) {
        @Override
        public String toString() {
            return "Turn[role=" + role + ", contentChars=" + (content == null ? 0 : content.length()) + "]";
        }
    }

    // SAFE-03: Spring's DEBUG logging of the request body calls toString; keep question, history and filter out of it
    @Override
    public String toString() {
        return "ChatRequest[collections=" + (collections == null ? 0 : collections.size()) + ", questionChars="
                + (question == null ? 0 : question.length()) + ", turns=" + (history == null ? 0 : history.size())
                + "]";
    }

    public ChatService.Command toCommand() {
        List<ChatMessage> turns = history == null ? List.of() : history.stream()
                .map(t -> new ChatMessage(
                        "user".equals(t.role()) ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT, t.content()))
                .toList();
        return new ChatService.Command(collections, question, turns, filter, k);
    }
}
