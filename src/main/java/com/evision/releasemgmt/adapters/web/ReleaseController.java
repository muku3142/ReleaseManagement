package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.CreateReleaseRequest;
import com.evision.releasemgmt.adapters.web.dto.ManualItemResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseItemResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseSearchResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseSummaryResponse;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseService service;

    public ReleaseController(ReleaseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseResponse create(@Valid @RequestBody CreateReleaseRequest request, Principal principal) {
        // Every /api/** path is authenticated() in SecurityConfig, so Spring Security guarantees a
        // principal here; its name is the Jira accountId recorded as the actor (D-03-12).
        return toResponse(service.create(request.name(), request.version(), request.jiraProjectKey(),
                principal.getName()));
    }

    @GetMapping
    public ReleaseSearchResponse list(@RequestParam(name = "query", defaultValue = "") String query) {
        return new ReleaseSearchResponse(service.search(query).stream()
                .map(ReleaseController::toSummaryResponse).toList());
    }

    @GetMapping("/{releaseId}")
    public ReleaseResponse get(@PathVariable UUID releaseId) {
        return toResponse(service.get(releaseId));
    }

    @PostMapping("/{releaseId}/freeze")
    public ReleaseResponse freeze(@PathVariable UUID releaseId, Principal principal) {
        return toResponse(service.freeze(releaseId, principal.getName()));
    }

    @DeleteMapping("/{releaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID releaseId) {
        service.delete(releaseId);
    }

    private static ReleaseResponse toResponse(Release release) {
        return new ReleaseResponse(release.id(), release.name(), release.version(),
                release.status().name(), release.jiraProjectKey(), release.createdAt(),
                release.createdBy(), release.frozenAt(), release.frozenBy(), release.lastRefreshedAt(),
                release.items().stream().map(ReleaseController::toItemResponse).toList(),
                release.manualItems().stream().map(ReleaseController::toManualItemResponse).toList());
    }

    private static ReleaseSummaryResponse toSummaryResponse(ReleaseSummary summary) {
        return new ReleaseSummaryResponse(summary.id(), summary.name(), summary.version(),
                summary.status().name(), summary.jiraProjectKey(), summary.createdAt(),
                summary.createdBy(), summary.frozenAt(), summary.frozenBy(), summary.lastRefreshedAt(),
                summary.itemCount());
    }

    private static ReleaseItemResponse toItemResponse(ReleaseItem item) {
        return new ReleaseItemResponse(item.ticket().key(), item.ticket().summary(),
                item.ticket().issueType(), item.ticket().status(), item.ticket().resolution(),
                item.ticket().assignee(), item.ticket().reporter(), item.ticket().fixVersions(),
                item.ticket().description(), item.ticket().comments(), item.ticket().commentTotal(),
                item.addedAt());
    }

    private static ManualItemResponse toManualItemResponse(ManualItem item) {
        return new ManualItemResponse(item.id(), item.title(), item.description(),
                item.category().name(), item.addedAt());
    }
}
