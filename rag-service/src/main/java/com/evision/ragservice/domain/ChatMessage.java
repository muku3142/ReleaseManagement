package com.evision.ragservice.domain;

public record ChatMessage(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT }

    @Override
    public String toString() {
        return "ChatMessage[role=" + role + ", contentChars=" + (content == null ? 0 : content.length()) + "]";
    }
}
