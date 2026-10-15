package com.evision.releasemgmt.application.assistant.tools;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class ToolRegistry {

    private final List<AssistantTool> tools;

    public ToolRegistry(List<AssistantTool> tools) {
        Set<String> names = new HashSet<>();
        for (AssistantTool tool : tools) {
            if (!names.add(tool.name())) {
                throw new IllegalStateException("Duplicate assistant tool name: " + tool.name());
            }
        }
        // sorted so the definitions block sent to the model is stable between requests
        this.tools = tools.stream().sorted(Comparator.comparing(AssistantTool::name)).toList();
    }

    public List<AssistantTool> tools() {
        return tools;
    }

    public Optional<AssistantTool> find(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst();
    }
}
