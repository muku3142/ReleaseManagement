package com.evision.ragservice.domain;

import java.util.List;

public record ToolTurn(int round, String questionId, List<ToolCall> calls, String signature, List<ToolResult> results) {

    public ToolResult resultFor(String callId) {
        if (results == null) {
            return null;
        }
        for (ToolResult result : results) {
            if (result.callId() != null && result.callId().equals(callId)) {
                return result;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ToolTurn[round=" + round + ", calls=" + (calls == null ? 0 : calls.size())
                + ", results=" + (results == null ? 0 : results.size()) + "]";
    }
}
