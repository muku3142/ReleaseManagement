package com.evision.ragservice.domain;

public record ToolResult(String callId, String content, boolean isError, ToolSource source) {

    @Override
    public String toString() {
        return "ToolResult[contentChars=" + (content == null ? 0 : content.length())
                + ", isError=" + isError + ", source=" + (source != null) + "]";
    }
}
