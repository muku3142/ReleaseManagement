package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.CorrectInstallRequest;
import com.evision.releasemgmt.adapters.web.dto.InstallHistoryResponse;
import com.evision.releasemgmt.adapters.web.dto.RecordInstallRequest;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ClientDeploymentService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/releases/{releaseId}/installs")
public class ClientDeploymentController {

    private final ClientDeploymentService service;
    private final ActorResolutionService actorResolution;

    public ClientDeploymentController(ClientDeploymentService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @GetMapping
    public InstallHistoryResponse history(@PathVariable UUID releaseId) {
        return respond(releaseId, service.history(releaseId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstallHistoryResponse record(@PathVariable UUID releaseId,
                                         @Valid @RequestBody RecordInstallRequest request,
                                         Principal principal) {
        // Every /api/** path is authenticated() in SecurityConfig, so a principal is guaranteed. The actor
        // always comes from the principal, never from the body (D-07-12).
        ClientDeploymentDraft draft = new ClientDeploymentDraft(request.productId(), request.outcome(),
                request.installedAt(), request.note());
        return respond(releaseId, service.record(releaseId, draft, request.clientIds(), principal.getName()));
    }

    @PostMapping("/{installId}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public InstallHistoryResponse correct(@PathVariable UUID releaseId,
                                          @PathVariable UUID installId,
                                          @Valid @RequestBody CorrectInstallRequest request,
                                          Principal principal) {
        return respond(releaseId, service.correct(releaseId, installId, request.outcome(), request.installedAt(),
                request.note(), request.reason(), principal.getName()));
    }

    private InstallHistoryResponse respond(UUID releaseId, ClientDeploymentHistory history) {
        // D-05.3-05: resolved once per response, never per row.
        ResolvedActors actors = actorResolution.resolve(
                history.entries().stream().map(ClientDeployment::recordedBy).toArray(String[]::new));
        // Looked up by id at read time, so renaming a product or client relabels its history.
        Map<UUID, String> productNames = history.products().stream()
                .collect(Collectors.toMap(ReleaseProduct::id, ReleaseProduct::name));
        Map<UUID, String> clientNames = history.clients().stream()
                .collect(Collectors.toMap(Client::id, Client::name));
        return new InstallHistoryResponse(
                releaseId,
                history.frozen(),
                service.reachedProduction(releaseId),
                history.products().stream()
                        .map(p -> new InstallHistoryResponse.Product(p.id(), p.name(), p.archived()))
                        .toList(),
                history.entries().stream()
                        .sorted(ClientDeploymentHistory.LEDGER_ORDER.reversed())
                        .map(e -> new InstallHistoryResponse.Entry(e.id(), e.productId(),
                                productNames.get(e.productId()), e.clientId(), clientNames.get(e.clientId()),
                                e.outcome(), e.installedAt(), e.recordedAt(), actors.display(e.recordedBy()),
                                e.note(), e.supersedesId(), history.supersededBy(e.id()).orElse(null),
                                e.correctionReason(), history.isEffective(e)))
                        .toList());
    }
}
