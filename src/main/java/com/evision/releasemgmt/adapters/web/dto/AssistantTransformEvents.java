package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;

import java.util.Map;

// Browser transform protocol; token events reuse AssistantChatEvents.TokenEvent (same shape).
public final class AssistantTransformEvents {

    private AssistantTransformEvents() {
    }

    public record DoneEvent(String finishReason, boolean truncated, boolean complete) {
    }

    public record ProgressEvent(int section, int sections) {
    }

    public record RemainderEvent(int keep, String text) {
    }

    public record ErrorEvent(String outcome, Integer retryAfterSeconds, Integer characters, Integer maxCharacters) {
    }

    public record SavedResponse(int revision) {
    }

    public record LimitsResponse(int numCtx, double charsPerToken, int systemTokens, int templateSlack,
                                 int instructionMaxChars,
                                 Map<TransformMode, Map<TemplateDocumentType, Integer>> presets) {
    }
}
