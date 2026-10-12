package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.AdoCredentialResponse;
import com.evision.releasemgmt.adapters.web.dto.UpdateAdoCredentialRequest;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.AdoCredentialService;
import com.evision.releasemgmt.application.AdoCredentialService.View;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/settings/azure-devops")
public class AdoCredentialController {

    private final AdoCredentialService service;
    private final ActorResolutionService actorResolution;

    public AdoCredentialController(AdoCredentialService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @GetMapping
    public AdoCredentialResponse get() {
        return toResponse(service.status());
    }

    @PutMapping
    public AdoCredentialResponse update(@Valid @RequestBody UpdateAdoCredentialRequest request, Principal principal) {
        // PUT falls under SecurityConfig's /api/** authenticated rule, so Spring Security
        // guarantees a principal by the time this runs
        return toResponse(service.save(request.organization(), request.token(), request.expiresOn(),
                principal.getName()));
    }

    private AdoCredentialResponse toResponse(View view) {
        if (!view.configured()) {
            return new AdoCredentialResponse(false, null, null, null, null, null);
        }
        String updatedBy = actorResolution.resolve(view.updatedBy()).display(view.updatedBy());
        return new AdoCredentialResponse(true, view.organization(), view.expiresOn(), view.state().name(),
                view.updatedAt(), updatedBy);
    }
}
