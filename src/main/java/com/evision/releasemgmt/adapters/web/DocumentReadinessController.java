package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.ReadinessEntryResponse;
import com.evision.releasemgmt.adapters.web.dto.ReadinessResponse;
import com.evision.releasemgmt.application.DocumentReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A separate controller from DocumentController: this endpoint is not scoped to one release and
// does not belong under /api/releases/{id}/... - it answers DOC-09's whole cross-release grid in
// a single request (T-05-59).
@RestController
@RequestMapping("/api/documents")
public class DocumentReadinessController {

    private final DocumentReadinessService service;

    public DocumentReadinessController(DocumentReadinessService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public ReadinessResponse readiness() {
        var entries = service.all().stream()
                .map(entry -> new ReadinessEntryResponse(entry.releaseId(), entry.documentType().name(),
                        entry.state().name()))
                .toList();
        return new ReadinessResponse(entries);
    }
}
