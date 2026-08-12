package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.AddTicketsRequest;
import com.evision.releasemgmt.adapters.web.dto.CreateReleaseRequest;
import com.evision.releasemgmt.adapters.web.dto.ManualItemRequest;
import com.evision.releasemgmt.adapters.web.dto.ManualItemResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseAddResultResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseItemResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseSearchResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseSummaryResponse;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.release.AddTicketsResult;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseService service;
    private final ActorResolutionService actorResolution;

    public ReleaseController(ReleaseService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseAddResultResponse create(@Valid @RequestBody CreateReleaseRequest request,
                                           Principal principal) {
        // Every /api/** path is authenticated() in SecurityConfig, so Spring Security guarantees a
        // principal here; its name is the Jira accountId recorded as the actor (D-03-12).
        return toAddResult(service.createWithTickets(request.name(), request.version(),
                request.jiraProjectKey(),
                request.ticketKeys() == null ? List.of() : request.ticketKeys(),
                principal.getName()));
    }

    @GetMapping
    public ReleaseSearchResponse list(@RequestParam(name = "query", defaultValue = "") String query) {
        List<ReleaseSummary> summaries = service.search(query);
        // D-05.3-05: this is the phase's worst N+1 site - two actors per row. One call for the page,
        // and toSummaryResponse stays static so a row cannot resolve for itself.
        ResolvedActors actors = actorResolution.resolve(summaries.stream()
                .flatMap(s -> Stream.of(s.createdBy(), s.frozenBy()))
                .toArray(String[]::new));
        return new ReleaseSearchResponse(summaries.stream()
                .map(s -> toSummaryResponse(s, actors)).toList());
    }

    @GetMapping("/{releaseId}")
    public ReleaseResponse get(@PathVariable UUID releaseId) {
        return toResponse(service.get(releaseId));
    }

    @PostMapping("/{releaseId}/items")
    public ReleaseAddResultResponse addTickets(@PathVariable UUID releaseId,
                                               @Valid @RequestBody AddTicketsRequest request) {
        return toAddResult(service.addTickets(releaseId, request.ticketKeys()));
    }

    @DeleteMapping("/{releaseId}/items/{ticketKey}")
    public ReleaseResponse removeTicket(@PathVariable UUID releaseId, @PathVariable String ticketKey) {
        return toResponse(service.removeTicket(releaseId, ticketKey));
    }

    @PostMapping("/{releaseId}/refresh")
    public ReleaseResponse refresh(@PathVariable UUID releaseId) {
        return toResponse(service.refresh(releaseId));
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

    @PostMapping("/{releaseId}/manual-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseResponse addManualItem(@PathVariable UUID releaseId,
                                         @Valid @RequestBody ManualItemRequest request) {
        return toResponse(service.addManualItem(releaseId, request.title(), request.description(),
                request.category()));
    }

    @PutMapping("/{releaseId}/manual-items/{manualItemId}")
    public ReleaseResponse updateManualItem(@PathVariable UUID releaseId,
                                            @PathVariable UUID manualItemId,
                                            @Valid @RequestBody ManualItemRequest request) {
        return toResponse(service.updateManualItem(releaseId, manualItemId, request.title(),
                request.description(), request.category()));
    }

    @DeleteMapping("/{releaseId}/manual-items/{manualItemId}")
    public ReleaseResponse removeManualItem(@PathVariable UUID releaseId,
                                            @PathVariable UUID manualItemId) {
        return toResponse(service.removeManualItem(releaseId, manualItemId));
    }

    private ReleaseAddResultResponse toAddResult(AddTicketsResult result) {
        return new ReleaseAddResultResponse(toResponse(result.release()), result.added(),
                result.alreadyPresent(), result.notFound());
    }

    private ReleaseResponse toResponse(Release release) {
        ResolvedActors actors = actorResolution.resolve(release.createdBy(), release.frozenBy());
        return new ReleaseResponse(release.id(), release.name(), release.version(),
                release.status().name(), release.jiraProjectKey(), release.createdAt(),
                actors.display(release.createdBy()), release.frozenAt(),
                actors.display(release.frozenBy()), release.lastRefreshedAt(),
                release.items().stream().map(ReleaseController::toItemResponse).toList(),
                release.manualItems().stream().map(ReleaseController::toManualItemResponse).toList());
    }

    private static ReleaseSummaryResponse toSummaryResponse(ReleaseSummary summary, ResolvedActors actors) {
        return new ReleaseSummaryResponse(summary.id(), summary.name(), summary.version(),
                summary.status().name(), summary.jiraProjectKey(), summary.createdAt(),
                actors.display(summary.createdBy()), summary.frozenAt(),
                actors.display(summary.frozenBy()), summary.lastRefreshedAt(), summary.itemCount());
    }

    private static ReleaseItemResponse toItemResponse(ReleaseItem item) {
        return new ReleaseItemResponse(item.ticket().key(), item.ticket().summary(),
                item.ticket().issueType(), item.ticket().status(), item.ticket().resolution(),
                item.ticket().assignee(), item.ticket().reporter(), item.ticket().fixVersions(),
                item.ticket().component(), item.ticket().description(), item.ticket().comments(),
                item.ticket().commentTotal(), item.addedAt());
    }

    private static ManualItemResponse toManualItemResponse(ManualItem item) {
        return new ManualItemResponse(item.id(), item.title(), item.description(),
                item.category().name(), item.addedAt());
    }
}
