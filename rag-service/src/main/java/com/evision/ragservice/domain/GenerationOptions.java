package com.evision.ragservice.domain;

import java.util.List;

public record GenerationOptions(int numCtx, int numPredict, double temperature, List<ToolDefinition> tools) {

    public GenerationOptions(int numCtx, int numPredict, double temperature) {
        this(numCtx, numPredict, temperature, null);
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }
}
