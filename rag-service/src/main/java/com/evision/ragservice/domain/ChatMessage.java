package com.evision.ragservice.domain;

public record ChatMessage(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT }
}
