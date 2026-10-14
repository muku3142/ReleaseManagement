package com.evision.ragservice.domain;

public record GenerationOptions(int numCtx, int numPredict, double temperature) {
}
