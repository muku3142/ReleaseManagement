package com.evision.ragservice.domain;

import java.util.List;

public record ChatMessage(Role role, String content, List<ToolCall> toolCalls, String toolName) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public ChatMessage(Role role, String content) {
        this(role, content, null, null);
    }

    @Override
    public String toString() {
        return "ChatMessage[role=" + role + ", contentChars=" + (content == null ? 0 : content.length())
                + ", toolCalls=" + (toolCalls == null ? 0 : toolCalls.size()) + "]";
    }
}
