package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.application.StatusService.ServiceStatus;

public record StatusResponse(String ollama, long pending, long failed) {

    public static StatusResponse from(ServiceStatus status) {
        return new StatusResponse(status.ollamaAvailable() ? "UP" : "DOWN", status.queue().pending(),
                status.queue().failed());
    }
}
