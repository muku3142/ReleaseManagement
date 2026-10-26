package com.evision.releasemgmt.application;

import com.evision.releasemgmt.application.PortfolioDashboardService.Cell;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientCurrentRun;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.RecentInstall;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import com.evision.releasemgmt.ports.PortfolioReadRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioDashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Instant T1 = NOW.minus(Duration.ofDays(30));
    private static final Instant T2 = NOW.minus(Duration.ofDays(20));
    private static final Instant T3 = NOW.minus(Duration.ofDays(10));

    private final DashboardReadRepository dashboard = mock(DashboardReadRepository.class);
    private final PortfolioReadRepository portfolio = mock(PortfolioReadRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final PortfolioDashboardService service = new PortfolioDashboardService(dashboard, portfolio,
            productRepository, clientRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final Stage prod = new Stage(UUID.randomUUID(), "PROD", 2, true, null);

    private final Product a = new Product(UUID.randomUUID(), "Alpha", null);
    private final Product b = new Product(UUID.randomUUID(), "Beta", null);

    private final DashboardRelease r = release("R", ReleaseStatus.FROZEN, T1);
    private final DashboardRelease s = release("S", ReleaseStatus.OPEN, null);
    private final DashboardRelease t = release("T", ReleaseStatus.FROZEN, T2);
    private final DashboardRelease u = release("U", ReleaseStatus.FROZEN, T3);

    private final Client c1 = new Client(UUID.randomUUID(), "C1", null, Set.of(a.id(), b.id()));
    private final Client c2 = new Client(UUID.randomUUID(), "C2", null, Set.of(a.id()));
    private final Client c3 = new Client(UUID.randomUUID(), "C3", T1, Set.of(a.id()));

    private List<Product> productList = List.of(a, b);

    private DashboardRelease release(String name, ReleaseStatus status, Instant frozenAt) {
        return new DashboardRelease(UUID.randomUUID(), name, "1." + name, status, T1.minus(Duration.ofDays(1)), frozenAt, 0);
    }

    private Promotion promoted(DashboardRelease release, Stage stage) {
        return new Promotion(UUID.randomUUID(), release.id(), stage.id(), PromotionOutcome.SUCCESS, T3, T3, "acc", null,
                null, null);
    }

    private ClientCurrentRun run(Client client, Product product, DashboardRelease release) {
        return new ClientCurrentRun(client.id(), product.id(), release.id(), T3);
    }

    @BeforeEach
    void fixture() {
        when(dashboard.loadLedger()).thenReturn(new DashboardLedger(List.of(r, s, t, u), List.of(dev, prod),
                List.of(promoted(r, dev), promoted(r, prod), promoted(u, prod))));
        when(portfolio.productsByRelease()).thenReturn(Map.of(
                r.id(), Set.of(a.id(), b.id()), s.id(), Set.of(a.id()), t.id(), Set.of(b.id()), u.id(), Set.of(a.id())));
        when(productRepository.findAll()).thenAnswer(i -> productList);
        when(clientRepository.findAll()).thenReturn(List.of(c1, c2, c3));
        when(portfolio.currentRuns()).thenReturn(List.of(run(c1, a, r), run(c1, b, t), run(c3, a, u)));
        when(portfolio.successfulInstallsSince(any())).thenReturn(List.of(
                new RecentInstall(c1.id(), a.id()), new RecentInstall(c1.id(), b.id()), new RecentInstall(c3.id(), a.id())));
    }

    @Test
    void totalsCountAMultiProductReleaseOnceAndAStageSkipAsInProduction() {
        PortfolioDashboardService.Summary summary = service.summary(Set.of(a.id(), b.id()), false);

        PortfolioDashboardService.ProductRow rowA = summary.products().get(0);
        PortfolioDashboardService.ProductRow rowB = summary.products().get(1);
        assertThat(rowA.releases()).isEqualTo(new PortfolioDashboardService.StatusCounts(1, 0, 2));
        assertThat(rowB.releases()).isEqualTo(new PortfolioDashboardService.StatusCounts(0, 1, 1));
        assertThat(summary.totals()).isEqualTo(new PortfolioDashboardService.StatusCounts(1, 1, 2));
        assertThat(summary.unassigned()).isNull();
    }

    @Test
    void productRowsCarryNewestFrozenAndClientsOnNewest() {
        PortfolioDashboardService.Summary summary = service.summary(Set.of(a.id(), b.id()), false);

        PortfolioDashboardService.ProductRow rowA = summary.products().get(0);
        PortfolioDashboardService.ProductRow rowB = summary.products().get(1);
        assertThat(rowA.newestFrozen().releaseId()).isEqualTo(u.id());
        assertThat(rowA.linkedClients()).isEqualTo(2);
        assertThat(rowA.clientsOnNewest()).isZero();
        assertThat(rowB.newestFrozen().releaseId()).isEqualTo(t.id());
        assertThat(rowB.linkedClients()).isEqualTo(1);
        assertThat(rowB.clientsOnNewest()).isEqualTo(1);
    }

    @Test
    void behindListsOnlyOlderRunsAndKeepsNotInstalledSeparate() {
        PortfolioDashboardService.Behind behind = service.behind(Set.of(), false);

        assertThat(behind.behind()).hasSize(1);
        PortfolioDashboardService.BehindRow row = behind.behind().get(0);
        assertThat(row.clientId()).isEqualTo(c1.id());
        assertThat(row.productId()).isEqualTo(a.id());
        assertThat(row.runs().releaseId()).isEqualTo(r.id());
        assertThat(row.releasesBehind()).isEqualTo(1);
        assertThat(row.newest().releaseId()).isEqualTo(u.id());
        assertThat(behind.notInstalled()).hasSize(1);
        assertThat(behind.notInstalled().get(0).clientId()).isEqualTo(c2.id());
        assertThat(behind.notInstalled().get(0).newest().releaseId()).isEqualTo(u.id());
    }

    @Test
    void archivingAClientNeverChangesAnotherClientsDistance() {
        PortfolioDashboardService.Behind behind = service.behind(Set.of(), true);

        assertThat(behind.behind()).hasSize(1);
        assertThat(behind.behind().get(0).releasesBehind()).isEqualTo(1);
        assertThat(behind.notInstalled()).hasSize(1);
    }

    @Test
    void tilesRespectScopeAndTheThirtyDayWindow() {
        PortfolioDashboardService.Tiles tiles = service.summary(Set.of(), false).tiles();

        assertThat(tiles).isEqualTo(new PortfolioDashboardService.Tiles(1, 1, 2, 2, 2));
        verify(portfolio).successfulInstallsSince(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void matrixFilteredToOneProductHasLinkedClientsOnly() {
        PortfolioDashboardService.Matrix matrix = service.matrix(Set.of(a.id()), false);

        assertThat(matrix.products()).extracting(PortfolioDashboardService.Column::id).containsExactly(a.id());
        assertThat(matrix.clients()).extracting(PortfolioDashboardService.Column::id).containsExactly(c1.id(), c2.id());
        assertThat(matrix.cells()).hasSize(2);
        Cell c1a = matrix.cells().get(0);
        assertThat(c1a.clientId()).isEqualTo(c1.id());
        assertThat(c1a.run().releaseId()).isEqualTo(r.id());
        assertThat(c1a.run().inProduction()).isTrue();
        assertThat(c1a.releasesBehind()).isEqualTo(1);
        assertThat(c1a.newest().releaseId()).isEqualTo(u.id());
        assertThat(c1a.upToDate()).isFalse();
        assertThat(matrix.cells().get(1).run()).isNull();
    }

    @Test
    void matrixWithoutFilterKeepsAClientLinkedToNothingAsEmptyRow() {
        Client loner = new Client(UUID.randomUUID(), "Loner", null, Set.of());
        when(clientRepository.findAll()).thenReturn(List.of(c1, c2, c3, loner));

        PortfolioDashboardService.Matrix matrix = service.matrix(Set.of(), false);

        assertThat(matrix.clients()).extracting(PortfolioDashboardService.Column::id).contains(loner.id());
        assertThat(matrix.cells()).noneMatch(cell -> cell.clientId().equals(loner.id()));
    }

    @Test
    void anArchivedProductNamedExplicitlyIsIncluded() {
        Product archived = new Product(a.id(), "Alpha", T1);
        productList = List.of(archived, b);

        assertThat(service.matrix(Set.of(a.id()), false).products()).hasSize(1);
        assertThat(service.matrix(Set.of(), false).products()).extracting(PortfolioDashboardService.Column::id)
                .containsExactly(b.id());
    }

    @Test
    void aProductWithNoFrozenReleaseHasNoDistance() {
        Product empty = new Product(UUID.randomUUID(), "Empty", null);
        Client linked = new Client(UUID.randomUUID(), "Linked", null, Set.of(empty.id()));
        productList = List.of(a, b, empty);
        when(clientRepository.findAll()).thenReturn(List.of(linked));
        when(portfolio.currentRuns()).thenReturn(List.of(new ClientCurrentRun(linked.id(), empty.id(), s.id(), T3)));

        Cell withRun = service.matrix(Set.of(empty.id()), false).cells().get(0);
        assertThat(withRun.releasesBehind()).isNull();
        assertThat(withRun.upToDate()).isFalse();

        when(portfolio.currentRuns()).thenReturn(List.of());
        PortfolioDashboardService.Behind behind = service.behind(Set.of(empty.id()), false);
        assertThat(behind.notInstalled()).hasSize(1);
        assertThat(behind.notInstalled().get(0).newest()).isNull();
    }

    @Test
    void theLedgerIsLoadedOncePerCall() {
        service.summary(Set.of(), false);

        verify(dashboard, times(1)).loadLedger();
    }
}
