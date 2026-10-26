package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.ClientDetailResponse;
import com.evision.releasemgmt.adapters.web.dto.ClientListResponse;
import com.evision.releasemgmt.adapters.web.dto.PortfolioNameRequest;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ClientDetail;
import com.evision.releasemgmt.application.ClientService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.UUID;

// Clients are archived, never deleted (D-15-07); the only DELETE route removes a product link.
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private static final Comparator<Client> ARCHIVED_LAST_THEN_NAME =
            Comparator.comparing(Client::archived).thenComparing(Client::name, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<ClientDetail.HistoryRow> NEWEST_FIRST =
            Comparator.comparing((ClientDetail.HistoryRow r) -> r.install().entry().installedAt())
                    .thenComparing(r -> r.install().entry().recordedAt())
                    .reversed();

    private final ClientService service;
    private final ActorResolutionService actorResolution;

    public ClientController(ClientService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @GetMapping
    public ClientListResponse list() {
        return respondList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientListResponse add(@Valid @RequestBody PortfolioNameRequest request) {
        service.add(request.name());
        return respondList();
    }

    @PutMapping("/{clientId}")
    public ClientListResponse rename(@PathVariable UUID clientId, @Valid @RequestBody PortfolioNameRequest request) {
        service.rename(clientId, request.name());
        return respondList();
    }

    @PostMapping("/{clientId}/archive")
    public ClientListResponse archive(@PathVariable UUID clientId) {
        service.archive(clientId);
        return respondList();
    }

    @PostMapping("/{clientId}/unarchive")
    public ClientListResponse unarchive(@PathVariable UUID clientId) {
        service.unarchive(clientId);
        return respondList();
    }

    @GetMapping("/{clientId}")
    public ClientDetailResponse detail(@PathVariable UUID clientId) {
        return respondDetail(clientId);
    }

    @PutMapping("/{clientId}/products/{productId}")
    public ClientDetailResponse link(@PathVariable UUID clientId, @PathVariable UUID productId) {
        service.link(clientId, productId);
        return respondDetail(clientId);
    }

    @DeleteMapping("/{clientId}/products/{productId}")
    public ClientDetailResponse unlink(@PathVariable UUID clientId, @PathVariable UUID productId) {
        service.unlink(clientId, productId);
        return respondDetail(clientId);
    }

    private ClientListResponse respondList() {
        return new ClientListResponse(service.list().stream()
                .sorted(ARCHIVED_LAST_THEN_NAME)
                .map(c -> new ClientListResponse.Item(c.id(), c.name(), c.archived(), c.productIds().stream().toList()))
                .toList());
    }

    private ClientDetailResponse respondDetail(UUID clientId) {
        ClientDetail detail = service.detail(clientId);
        // D-05.3-05: resolved once per response, never per row.
        ResolvedActors actors = actorResolution.resolve(detail.history().stream()
                .map(h -> h.install().entry().recordedBy()).toArray(String[]::new));
        Client client = detail.client();
        return new ClientDetailResponse(client.id(), client.name(), client.archived(),
                detail.runs().stream().map(ClientController::toRun).toList(),
                detail.history().stream().sorted(NEWEST_FIRST).map(h -> toEntry(h, actors)).toList());
    }

    private static ClientDetailResponse.Run toRun(ClientDetail.Run run) {
        CurrentRun current = run.current();
        return new ClientDetailResponse.Run(run.product().id(), run.product().name(), run.product().archived(),
                run.hasInstalls(),
                current == null ? null : new ClientDetailResponse.Current(current.releaseId(), current.releaseName(),
                        current.releaseVersion(), current.installedAt(), run.currentInProduction()));
    }

    private static ClientDetailResponse.Entry toEntry(ClientDetail.HistoryRow row, ResolvedActors actors) {
        ClientDeployment e = row.install().entry();
        return new ClientDetailResponse.Entry(e.id(), e.productId(), row.productName(), e.releaseId(),
                row.install().releaseName(), row.install().releaseVersion(), row.inProduction(), e.outcome(),
                e.installedAt(), e.recordedAt(), actors.display(e.recordedBy()), e.note(), e.supersedesId(),
                row.supersededById(), e.correctionReason(), row.effective());
    }
}
