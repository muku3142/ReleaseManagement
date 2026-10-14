package com.evision.ragservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.chat")
public record ChatProperties(String model, int numCtx, int numPredict, double temperature, double transformTemperature,
        String keepAlive, Duration deadline, Duration heartbeatInterval, int maxInFlightPerClient, int maxInFlightGlobal,
        int retryAfterSeconds, int kDefault, int kMax, double minScore, int questionMaxChars, int historyMaxTurns,
        int historyTurnMaxChars, int historyTokenBudget, int instructionMaxChars, double charsPerToken) {
}
