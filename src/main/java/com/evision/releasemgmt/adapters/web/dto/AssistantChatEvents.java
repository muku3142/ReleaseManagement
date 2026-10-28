package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;
import java.util.Map;

// Browser event protocol v1; the frontend client reads these field names.
public final class AssistantChatEvents {

    private AssistantChatEvents() {
    }

    public record StatusEvent(String tool) {
    }

    public record TokenEvent(String text) {

        @Override
        public String toString() {
            return "TokenEvent[" + text.length() + " chars]";
        }
    }

    public record DoneEvent(boolean refused, List<SourceView> sources, List<ToolView> tools, boolean historyTrimmed,
                            Meta meta) {
    }

    public record Meta(String model, String promptHash) {
    }

    public record ErrorEvent(String outcome, Integer retryAfterSeconds) {
    }

    public record SourceView(int n, String kind, String title, String releaseId, String releaseVersion,
                             String ticketKey, String documentType, Integer revision) {
    }

    public record ToolView(int round, String name, Map<String, Object> arguments, boolean error) {

        @Override
        public String toString() {
            return "ToolView[" + round + ", " + name + "]";
        }
    }
}
