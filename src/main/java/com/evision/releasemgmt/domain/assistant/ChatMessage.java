package com.evision.releasemgmt.domain.assistant;

public record ChatMessage(String role, String content) {

    @Override
    public String toString() {
        return "ChatMessage[" + role + "]";
    }
}
