package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.PortfolioDashboardService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PortfolioDashboardPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private PortfolioDashboardService service;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ReleaseProductRepository releaseProducts;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private ClientDeploymentRepository deployments;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private JdbcTemplate jdbc;

    private Product a;
    private Product b;
    private Client c1;
    private Client c2;
    private UUID r;
    private UUID s;
    private UUID t;

    @BeforeEach
    void fixtures() {
        a = products.add("A");
        b = products.add("B");
        c1 = clients.add("C1");
        c2 = clients.add("C2");
        clients.link(c1.id(), a.id());
        clients.link(c1.id(), b.id());
        clients.link(c2.id(), a.id());
        r = frozenRelease("R", Set.of(a.id(), b.id()), 180);
        s = openRelease("S");
        releaseProducts.replace(s, Set.of(a.id()));
        t = frozenRelease("T", Set.of(b.id()), 60);
        promotionService.record(r, new PromotionDraft(productionStageId(), PromotionOutcome.SUCCESS, null, null), "acc-1");
    }

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID frozenRelease(String name, Set<UUID> productIds, long frozenMinutesAgo) {
        UUID id = openRelease(name);
        releaseProducts.replace(id, productIds);
        releaseRepository.freeze(id, "rm@acme.com", Instant.now().minus(frozenMinutesAgo, ChronoUnit.MINUTES));
        return id;
    }

    private UUID openRelease(String name) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.DAYS));
        releaseRepository.create(release, List.of());
        return release.id();
    }

    private UUID productionStageId() {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE is_production", UUID.class);
    }

    private ClientDeployment install(UUID releaseId, Client client, Product product) {
        ClientDeploymentDraft draft = new ClientDeploymentDraft(product.id(), ClientDeploymentOutcome.SUCCESS, null, null);
        return deployments.appendUnderLock(releaseId,
                h -> h.record(draft, List.of(client.id()), "acc-1", Instant.now())).getFirst();
    }

    private void supersedeWithFailure(UUID releaseId, ClientDeployment original) {
        deployments.appendUnderLock(releaseId, h -> List.of(h.correct(original.id(), ClientDeploymentOutcome.FAILED,
                Instant.now().minus(1, ChronoUnit.MINUTES), null, "did not run", "acc-2", Instant.now())));
    }

    @Test
    void totalsCountAMultiProductReleaseOnceWhileTheRowsSumHigher() {
        PortfolioDashboardService.Summary summary = service.summary(Set.of(a.id(), b.id()), false);

        assertThat(summary.totals()).isEqualTo(new PortfolioDashboardService.StatusCounts(1, 1, 1));
        int rowSum = summary.products().stream()
                .mapToInt(p -> p.releases().open() + p.releases().frozen() + p.releases().inProduction()).sum();
        assertThat(rowSum).isEqualTo(4);
    }

    @Test
    void behindListsTheOlderRunAndNotInstalledStaysSeparate() {
        install(r, c1, a);
        install(r, c1, b);

        PortfolioDashboardService.Behind behind = service.behind(Set.of(), false);

        assertThat(behind.behind()).singleElement().satisfies(row -> {
            assertThat(row.clientId()).isEqualTo(c1.id());
            assertThat(row.productId()).isEqualTo(b.id());
            assertThat(row.releasesBehind()).isEqualTo(1);
            assertThat(row.newest().releaseId()).isEqualTo(t);
        });
        assertThat(behind.notInstalled()).singleElement().satisfies(row -> {
            assertThat(row.clientId()).isEqualTo(c2.id());
            assertThat(row.productId()).isEqualTo(a.id());
        });
    }

    @Test
    void addingAProductToAFrozenReleaseMakesItTheNewestForThatProductToo() {
        install(r, c1, a);
        install(r, c1, b);

        releaseProducts.replace(t, Set.of(a.id(), b.id()));

        assertThat(service.behind(Set.of(), false).behind())
                .extracting(row -> row.productId() + ":" + row.releasesBehind())
                .containsExactlyInAnyOrder(a.id() + ":1", b.id() + ":1");
    }

    @Test
    void aCorrectionSupersedingTheInstallMovesThePairToNotInstalled() {
        install(r, c1, a);
        ClientDeployment onB = install(r, c1, b);

        supersedeWithFailure(r, onB);

        PortfolioDashboardService.Matrix matrix = service.matrix(Set.of(), false);
        assertThat(matrix.cells()).filteredOn(c -> c.clientId().equals(c1.id()) && c.productId().equals(b.id()))
                .singleElement().satisfies(cell -> assertThat(cell.run()).isNull());
        assertThat(service.behind(Set.of(), false).notInstalled())
                .anyMatch(row -> row.clientId().equals(c1.id()) && row.productId().equals(b.id()));
    }
}
