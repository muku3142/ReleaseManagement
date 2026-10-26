package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientCurrentRun;
import com.evision.releasemgmt.domain.portfolio.FrozenRelease;
import com.evision.releasemgmt.domain.portfolio.FrozenReleaseOrder;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import com.evision.releasemgmt.ports.PortfolioReadRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Phase 16 portfolio read model: every figure from this system's tables, computed from one snapshot so tiles, tables, matrix and the installs tool agree.
@Service
public class PortfolioDashboardService {

    static final Duration RECENT_INSTALL_WINDOW = Duration.ofDays(30);

    public record ReleaseBrief(UUID releaseId, String name, String version, Instant frozenAt) {
    }

    public record RunBrief(UUID releaseId, String name, String version, Instant installedAt, boolean inProduction) {
    }

    public record StatusCounts(int open, int frozen, int inProduction) {
    }

    public record ProductRow(UUID id, String name, boolean archived, StatusCounts releases, ReleaseBrief newestFrozen,
                             int linkedClients, int clientsOnNewest) {
    }

    public record Tiles(int clientsBehind, int notInstalled, int activeProducts, int activeClients,
                        int installsLast30Days) {
    }

    public record Summary(Tiles tiles, List<ProductRow> products, StatusCounts totals, StatusCounts unassigned) {
    }

    public record BehindRow(UUID clientId, String clientName, boolean clientArchived, UUID productId, String productName,
                            RunBrief runs, int releasesBehind, ReleaseBrief newest) {
    }

    public record NotInstalledRow(UUID clientId, String clientName, boolean clientArchived, UUID productId,
                                  String productName, ReleaseBrief newest) {
    }

    public record Behind(List<BehindRow> behind, List<NotInstalledRow> notInstalled) {
    }

    public record Column(UUID id, String name, boolean archived) {
    }

    public record Cell(UUID clientId, UUID productId, RunBrief run, Integer releasesBehind, ReleaseBrief newest,
                       boolean upToDate) {
    }

    public record Matrix(List<Column> products, List<Column> clients, List<Cell> cells) {
    }

    private record Snapshot(List<Product> products, List<Client> clients, List<Cell> cells,
                            Map<UUID, FrozenReleaseOrder> orders, List<DashboardRelease> releases,
                            Map<UUID, Set<UUID>> productsByRelease, Set<UUID> inProduction,
                            int installsLast30Days) {
    }

    private static final Comparator<Product> PRODUCT_ORDER = Comparator.comparing(Product::archived)
            .thenComparing(Product::name, String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<Client> CLIENT_ORDER = Comparator.comparing(Client::archived)
            .thenComparing(Client::name, String.CASE_INSENSITIVE_ORDER);

    private final DashboardReadRepository dashboard;
    private final PortfolioReadRepository portfolio;
    private final ProductRepository products;
    private final ClientRepository clients;
    private final Clock clock;

    public PortfolioDashboardService(DashboardReadRepository dashboard, PortfolioReadRepository portfolio,
                                     ProductRepository products, ClientRepository clients, Clock clock) {
        this.dashboard = dashboard;
        this.portfolio = portfolio;
        this.products = products;
        this.clients = clients;
        this.clock = clock;
    }

    public Summary summary(Set<UUID> productIds, boolean includeArchived) {
        Snapshot s = snapshot(productIds, includeArchived);
        Set<UUID> inScopeProducts = s.products().stream().map(Product::id).collect(Collectors.toSet());

        List<ProductRow> rows = s.products().stream().map(p -> productRow(s, p)).toList();

        Set<UUID> covered = new HashSet<>();
        Set<UUID> unassigned = new HashSet<>();
        for (DashboardRelease r : s.releases()) {
            Set<UUID> covering = s.productsByRelease().getOrDefault(r.id(), Set.of());
            if (covering.isEmpty()) {
                unassigned.add(r.id());
            } else if (covering.stream().anyMatch(inScopeProducts::contains)) {
                covered.add(r.id());
            }
        }
        StatusCounts unassignedCounts = productIds.isEmpty() && !unassigned.isEmpty() ? counts(s, unassigned) : null;

        Behind behind = behind(s);
        Tiles tiles = new Tiles(behind.behind().size(), behind.notInstalled().size(), s.products().size(),
                s.clients().size(), s.installsLast30Days());
        return new Summary(tiles, rows, counts(s, covered), unassignedCounts);
    }

    public Behind behind(Set<UUID> productIds, boolean includeArchived) {
        return behind(snapshot(productIds, includeArchived));
    }

    public Matrix matrix(Set<UUID> productIds, boolean includeArchived) {
        Snapshot s = snapshot(productIds, includeArchived);
        return new Matrix(
                s.products().stream().map(p -> new Column(p.id(), p.name(), p.archived())).toList(),
                s.clients().stream().map(c -> new Column(c.id(), c.name(), c.archived())).toList(),
                s.cells());
    }

    private ProductRow productRow(Snapshot s, Product product) {
        Set<UUID> covering = s.releases().stream().map(DashboardRelease::id)
                .filter(id -> s.productsByRelease().getOrDefault(id, Set.of()).contains(product.id()))
                .collect(Collectors.toSet());
        ReleaseBrief newest = brief(s.orders().get(product.id()).newest().orElse(null));
        List<Cell> linked = s.cells().stream().filter(c -> c.productId().equals(product.id())).toList();
        int onNewest = newest == null ? 0 : (int) linked.stream()
                .filter(c -> c.run() != null && c.run().releaseId().equals(newest.releaseId())).count();
        return new ProductRow(product.id(), product.name(), product.archived(), counts(s, covering), newest,
                linked.size(), onNewest);
    }

    // In Production is sticky and wins over open/frozen; open is the remaining unfrozen set.
    private static StatusCounts counts(Snapshot s, Set<UUID> releaseIds) {
        int open = 0;
        int frozen = 0;
        int inProduction = 0;
        for (DashboardRelease r : s.releases()) {
            if (!releaseIds.contains(r.id())) {
                continue;
            }
            if (s.inProduction().contains(r.id())) {
                inProduction++;
            } else if (r.status() == ReleaseStatus.OPEN) {
                open++;
            } else {
                frozen++;
            }
        }
        return new StatusCounts(open, frozen, inProduction);
    }

    private Behind behind(Snapshot s) {
        Map<UUID, Product> productsById = s.products().stream().collect(Collectors.toMap(Product::id, Function.identity()));
        Map<UUID, Client> clientsById = s.clients().stream().collect(Collectors.toMap(Client::id, Function.identity()));
        List<BehindRow> behind = new ArrayList<>();
        List<NotInstalledRow> notInstalled = new ArrayList<>();
        for (Cell cell : s.cells()) {
            Client client = clientsById.get(cell.clientId());
            Product product = productsById.get(cell.productId());
            if (cell.run() == null) {
                notInstalled.add(new NotInstalledRow(client.id(), client.name(), client.archived(), product.id(),
                        product.name(), cell.newest()));
            } else if (cell.releasesBehind() != null && cell.releasesBehind() > 0) {
                behind.add(new BehindRow(client.id(), client.name(), client.archived(), product.id(), product.name(),
                        cell.run(), cell.releasesBehind(), cell.newest()));
            }
        }
        behind.sort(Comparator.comparingInt(BehindRow::releasesBehind).reversed()
                .thenComparing(BehindRow::clientName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BehindRow::productName, String.CASE_INSENSITIVE_ORDER));
        notInstalled.sort(Comparator.comparing(NotInstalledRow::productName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NotInstalledRow::clientName, String.CASE_INSENSITIVE_ORDER));
        return new Behind(behind, notInstalled);
    }

    private Snapshot snapshot(Set<UUID> productIds, boolean includeArchived) {
        DashboardLedger ledger = dashboard.loadLedger();
        Map<UUID, List<Promotion>> promotionsByRelease = ledger.promotions().stream()
                .collect(Collectors.groupingBy(Promotion::releaseId));
        Set<UUID> inProduction = new HashSet<>();
        for (DashboardRelease r : ledger.releases()) {
            PromotionHistory history = new PromotionHistory(r.id(), r.status() == ReleaseStatus.FROZEN, r.frozenAt(),
                    ledger.stages(), promotionsByRelease.getOrDefault(r.id(), List.of()));
            if (history.firstProductionSuccess().isPresent()) {
                inProduction.add(r.id());
            }
        }
        Map<UUID, DashboardRelease> releasesById = ledger.releases().stream()
                .collect(Collectors.toMap(DashboardRelease::id, Function.identity()));
        Map<UUID, Set<UUID>> productsByRelease = portfolio.productsByRelease();

        List<Product> allProducts = products.findAll();
        List<Client> allClients = clients.findAll();

        // Built over every product so archiving never changes N (D-16-07); unfrozen releases are not targets.
        Map<UUID, List<FrozenRelease>> frozenByProduct = new HashMap<>();
        for (DashboardRelease r : ledger.releases()) {
            if (r.status() != ReleaseStatus.FROZEN) {
                continue;
            }
            for (UUID productId : productsByRelease.getOrDefault(r.id(), Set.of())) {
                frozenByProduct.computeIfAbsent(productId, k -> new ArrayList<>())
                        .add(new FrozenRelease(r.id(), r.name(), r.version(), r.frozenAt()));
            }
        }
        Map<UUID, FrozenReleaseOrder> orders = new HashMap<>();
        for (Product p : allProducts) {
            orders.put(p.id(), FrozenReleaseOrder.of(frozenByProduct.getOrDefault(p.id(), List.of())));
        }

        List<Product> scopedProducts = allProducts.stream()
                .filter(p -> (productIds.isEmpty() || productIds.contains(p.id()))
                        && (includeArchived || !p.archived() || productIds.contains(p.id())))
                .sorted(PRODUCT_ORDER).toList();
        Set<UUID> scopedProductIds = scopedProducts.stream().map(Product::id).collect(Collectors.toSet());
        List<Client> activeClients = allClients.stream().filter(c -> includeArchived || !c.archived()).toList();
        List<Client> scopedClients = activeClients.stream()
                .filter(c -> productIds.isEmpty() || c.productIds().stream().anyMatch(scopedProductIds::contains))
                .sorted(CLIENT_ORDER).toList();

        Map<String, ClientCurrentRun> runsByPair = portfolio.currentRuns().stream()
                .collect(Collectors.toMap(r -> pairKey(r.clientId(), r.productId()), Function.identity()));
        List<Cell> cells = new ArrayList<>();
        for (Client client : scopedClients) {
            for (Product product : scopedProducts) {
                if (client.linkedTo(product.id())) {
                    cells.add(cell(client, product, runsByPair.get(pairKey(client.id(), product.id())),
                            orders.get(product.id()), releasesById, inProduction));
                }
            }
        }

        Set<UUID> activeClientIds = activeClients.stream().map(Client::id).collect(Collectors.toSet());
        int installs = (int) portfolio.successfulInstallsSince(clock.instant().minus(RECENT_INSTALL_WINDOW)).stream()
                .filter(i -> activeClientIds.contains(i.clientId()) && scopedProductIds.contains(i.productId()))
                .count();
        return new Snapshot(scopedProducts, scopedClients, cells, orders, ledger.releases(), productsByRelease,
                inProduction, installs);
    }

    private static Cell cell(Client client, Product product, ClientCurrentRun run, FrozenReleaseOrder order,
                             Map<UUID, DashboardRelease> releasesById, Set<UUID> inProduction) {
        ReleaseBrief newest = brief(order.newest().orElse(null));
        if (run == null) {
            return new Cell(client.id(), product.id(), null, null, newest, false);
        }
        DashboardRelease running = releasesById.get(run.releaseId());
        RunBrief runBrief = new RunBrief(running.id(), running.name(), running.version(), run.installedAt(),
                inProduction.contains(running.id()));
        if (order.isEmpty()) {
            return new Cell(client.id(), product.id(), runBrief, null, null, false);
        }
        int behind = order.releasesBehind(running.id());
        return new Cell(client.id(), product.id(), runBrief, behind, newest, behind == 0);
    }

    private static ReleaseBrief brief(FrozenRelease release) {
        return release == null ? null
                : new ReleaseBrief(release.id(), release.name(), release.version(), release.frozenAt());
    }

    private static String pairKey(UUID clientId, UUID productId) {
        return clientId + ":" + productId;
    }
}
