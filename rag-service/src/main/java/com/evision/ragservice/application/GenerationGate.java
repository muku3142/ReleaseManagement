package com.evision.ragservice.application;

import com.evision.ragservice.config.ChatProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

// In-memory and single-instance by design (accepted constraint)
@Component
public class GenerationGate {

    private final int maxPerClient;
    private final int maxGlobal;
    private final Map<UUID, Integer> perClient = new HashMap<>();
    private int global;

    public GenerationGate(ChatProperties properties) {
        this.maxPerClient = properties.maxInFlightPerClient();
        this.maxGlobal = properties.maxInFlightGlobal();
    }

    public synchronized boolean tryAcquire(UUID clientId) {
        int held = perClient.getOrDefault(clientId, 0);
        if (global >= maxGlobal || held >= maxPerClient) {
            return false;
        }
        perClient.put(clientId, held + 1);
        global++;
        return true;
    }

    public synchronized void release(UUID clientId) {
        Integer held = perClient.get(clientId);
        if (held == null) {
            return;
        }
        if (held == 1) {
            perClient.remove(clientId);
        } else {
            perClient.put(clientId, held - 1);
        }
        global--;
    }

    public synchronized boolean anyInFlight() {
        return global > 0;
    }
}
