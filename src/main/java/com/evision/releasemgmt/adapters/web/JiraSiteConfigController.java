package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.JiraSiteConfigResponse;
import com.evision.releasemgmt.adapters.web.dto.UpdateJiraSiteConfigRequest;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.JiraSiteConfigService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/settings/jira-site")
public class JiraSiteConfigController {

    private final JiraSiteConfigService service;
    private final ActorResolutionService actorResolution;

    public JiraSiteConfigController(JiraSiteConfigService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @GetMapping
    public JiraSiteConfigResponse get() {
        return toResponse(service.get());
    }

    @PutMapping
    public JiraSiteConfigResponse update(@Valid @RequestBody UpdateJiraSiteConfigRequest request, Principal principal) {
        // PUT falls under SecurityConfig's /api/** authenticated rule, so Spring Security
        // guarantees a principal by the time this runs
        return toResponse(service.updateBaseUrl(request.baseUrl(), principal.getName()));
    }

    private JiraSiteConfigResponse toResponse(JiraSiteConfig config) {
        ResolvedActors actors = actorResolution.resolve(config.updatedBy());
        return new JiraSiteConfigResponse(config.baseUrl(), config.updatedAt(),
                actors.display(config.updatedBy()));
    }
}
