package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.ClientNotFoundException;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClientService {

    private final ClientRepository clients;
    private final ProductRepository products;
    private final ClientDeploymentRepository deployments;
    private final PromotionRepository promotions;
    private final Clock clock;

    public ClientService(ClientRepository clients, ProductRepository products, ClientDeploymentRepository deployments,
                         PromotionRepository promotions, Clock clock) {
        this.clients = clients;
        this.products = products;
        this.deployments = deployments;
        this.promotions = promotions;
        this.clock = clock;
    }

    public List<Client> list() {
        return clients.findAll();
    }

    public void add(String name) {
        clients.add(name.strip());
    }

    public void rename(UUID id, String name) {
        find(id);
        clients.rename(id, name.strip());
    }

    public void archive(UUID id) {
        if (!find(id).archived()) {
            clients.setArchivedAt(id, clock.instant());
        }
    }

    public void unarchive(UUID id) {
        if (find(id).archived()) {
            clients.setArchivedAt(id, null);
        }
    }

    public void link(UUID clientId, UUID productId) {
        clients.link(clientId, productId);
    }

    public void unlink(UUID clientId, UUID productId) {
        clients.unlink(clientId, productId);
    }

    public ClientDetail detail(UUID clientId) {
        Client client = find(clientId);
        Map<UUID, Product> productsById = products.findAll().stream()
                .collect(Collectors.toMap(Product::id, p -> p));
        List<ClientInstall> installs = deployments.historyOfClient(clientId);
        Map<UUID, CurrentRun> current = deployments.currentRunsOfClient(clientId).stream()
                .collect(Collectors.toMap(CurrentRun::productId, r -> r));

        // One load per distinct release (tens at most for one client); Phase 16 batches this for the matrix.
        Map<UUID, Boolean> inProduction = new HashMap<>();
        installs.forEach(i -> inProduction.computeIfAbsent(i.entry().releaseId(), this::reachedProduction));
        current.values().forEach(r -> inProduction.computeIfAbsent(r.releaseId(), this::reachedProduction));

        Map<UUID, UUID> supersededBy = new HashMap<>();
        installs.stream().map(ClientInstall::entry).filter(e -> e.supersedesId() != null)
                .forEach(e -> supersededBy.put(e.supersedesId(), e.id()));
        Map<UUID, Boolean> hasInstalls = installs.stream()
                .collect(Collectors.toMap(i -> i.entry().productId(), i -> true, (a, b) -> a));

        List<ClientDetail.Run> runs = client.productIds().stream()
                .map(productsById::get)
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()))
                .map(p -> {
                    CurrentRun run = current.get(p.id());
                    return new ClientDetail.Run(p, hasInstalls.getOrDefault(p.id(), false), run,
                            run != null && inProduction.get(run.releaseId()));
                })
                .toList();
        List<ClientDetail.HistoryRow> history = installs.stream()
                .map(i -> new ClientDetail.HistoryRow(i, productsById.get(i.entry().productId()).name(),
                        inProduction.get(i.entry().releaseId()), supersededBy.get(i.entry().id())))
                .toList();
        return new ClientDetail(client, runs, history);
    }

    private boolean reachedProduction(UUID releaseId) {
        return promotions.load(releaseId).firstProductionSuccess().isPresent();
    }

    private Client find(UUID id) {
        return clients.findById(id).orElseThrow(ClientNotFoundException::new);
    }
}
