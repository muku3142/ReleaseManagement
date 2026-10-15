package com.evision.releasemgmt.domain.assistant;

public record ToolResult(String callId, String content, boolean isError, String sourceId, String sourceTitle) {

    @Override
    public String toString() {
        return "ToolResult[" + callId + ", error=" + isError + "]";
    }
}
