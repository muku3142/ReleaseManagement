package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.AssistantFreshnessResponse;
import com.evision.releasemgmt.adapters.web.dto.AssistantStatusResponse;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.ports.RagStatusPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

// Unconditional on purpose: the UI asks this before it knows whether the assistant exists.
@RestController
@RequestMapping("/api/assistant")
public class AssistantStatusController {

    private final boolean enabled;
    private final ObjectProvider<AssistantProperties> properties;
    private final ObjectProvider<RagStatusPort> status;

    public AssistantStatusController(@Value("${app.assistant.enabled}") boolean enabled,
                                     ObjectProvider<AssistantProperties> properties,
                                     ObjectProvider<RagStatusPort> status) {
        this.enabled = enabled;
        this.properties = properties;
        this.status = status;
    }

    @GetMapping("/status")
    public AssistantStatusResponse status() {
        AssistantProperties assistant = properties.getIfAvailable();
        if (!enabled || assistant == null) {
            return new AssistantStatusResponse(false, null);
        }
        return new AssistantStatusResponse(true, new AssistantStatusResponse.Starters(
                assistant.starters().unscoped(), assistant.starters().release()));
    }

    @GetMapping("/freshness")
    public ResponseEntity<AssistantFreshnessResponse> freshness() {
        RagStatusPort port = status.getIfAvailable();
        if (port == null) {
            return ResponseEntity.notFound().build();
        }
        Instant newest = Arrays.stream(IndexCollection.values())
                .map(port::lastIndexedAt)
                .flatMap(Optional::stream)
                .max(Instant::compareTo)
                .orElse(null);
        return ResponseEntity.ok(new AssistantFreshnessResponse(newest));
    }
}
