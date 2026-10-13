package com.evision.ragservice.adapters.ollama;

import com.evision.ragservice.ports.EmbeddingPort;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ollama")
public class OllamaHealthIndicator implements HealthIndicator {

    private final EmbeddingPort embeddingPort;

    public OllamaHealthIndicator(EmbeddingPort embeddingPort) {
        this.embeddingPort = embeddingPort;
    }

    @Override
    public Health health() {
        return embeddingPort.isAvailable()
                ? Health.up().build()
                : Health.down().withDetail("reason", "Ollama unreachable or erroring").build();
    }
}
