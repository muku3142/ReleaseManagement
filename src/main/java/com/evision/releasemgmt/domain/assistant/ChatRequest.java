package com.evision.releasemgmt.domain.assistant;

import java.util.List;
import java.util.Map;

public record ChatRequest(String question, List<ChatMessage> history, List<IndexCollection> collections,
                          Map<String, Object> filter, List<ToolDefinition> tools, List<AnsweredTurn> turns) {

    @Override
    public String toString() {
        return "ChatRequest[history=" + history.size() + ", collections=" + collections.size()
                + ", tools=" + tools.size() + ", turns=" + turns.size() + "]";
    }
}
