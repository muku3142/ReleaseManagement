package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.BriefingsResponse;
import com.evision.releasemgmt.application.briefing.BriefingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Read-only: the Claude result files are the source of truth and the app never writes them.
@RestController
@RequestMapping("/api/briefings")
public class BriefingController {

    private final BriefingService service;

    public BriefingController(BriefingService service) {
        this.service = service;
    }

    @GetMapping
    public BriefingsResponse list() {
        return new BriefingsResponse(service.overview().stream().map(BriefingsResponse.Task::of).toList());
    }
}
