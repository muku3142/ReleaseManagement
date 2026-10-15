package com.evision.releasemgmt.domain.assistant;

import java.util.List;

// rawJson is the tool_call event data exactly as received, so it can be resent unchanged.
public record SignedTurn(String rawJson, List<ToolCall> calls) {

    @Override
    public String toString() {
        return "SignedTurn[calls=" + calls.size() + "]";
    }
}
