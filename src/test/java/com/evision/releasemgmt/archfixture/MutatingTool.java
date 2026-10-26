package com.evision.releasemgmt.archfixture;

import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.application.assistant.tools.ToolOutput;
import java.util.Map;
import java.util.UUID;

/** ArchUnit non-vacuity fixture: calls a mutating service method. Never wired into the application. */
public class MutatingTool implements AssistantTool {

    private ReleaseService releaseService;

    @Override
    public String name() {
        return "mutating";
    }

    @Override
    public String description() {
        return "fixture";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of();
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        releaseService.freeze(UUID.randomUUID(), "fixture");
        return null;
    }
}
