package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientCurrentRun;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.RecentInstall;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.PortfolioReadRepository;
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
class PortfolioReadPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private PortfolioReadRepository portfolio;

    @Autowired
    private ClientDeploymentRepository deployments;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ReleaseProductRepository releaseProducts;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Product p1;
    private Product p2;
    private Client a;
    private Client b;
    private Client c;
    private UUID r1;
    private UUID r2;

    @BeforeEach
    void fixtures() {
        p1 = products.add("P1");
        p2 = products.add("P2");
        a = clients.add("A");
        b = clients.add("B");
        c = clients.add("C");
        for (Client client : List.of(a, b, c)) {
            clients.link(client.id(), p1.id());
        }
        r1 = frozenRelease("R1", Set.of(p1.id(), p2.id()));
        r2 = frozenRelease("R2", Set.of(p1.id()));
    }

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID frozenRelease(String name, Set<UUID> productIds) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        releaseProducts.replace(release.id(), productIds);
        releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        return release.id();
    }

    private static Instant ago(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    private ClientDeployment record(UUID releaseId, ClientDeploymentOutcome outcome, Instant installedAt, Client target) {
        ClientDeploymentDraft draft = new ClientDeploymentDraft(p1.id(), outcome, installedAt, null);
        return deployments.appendUnderLock(releaseId,
                h -> h.record(draft, List.of(target.id()), "acc-1", Instant.now())).getFirst();
    }

    private void correct(UUID releaseId, UUID originalId, ClientDeploymentOutcome outcome) {
        deployments.appendUnderLock(releaseId, h -> List.of(
                h.correct(originalId, outcome, Instant.now().minus(1, ChronoUnit.MINUTES), null, "fix", "acc-2",
                        Instant.now())));
    }

    @Test
    void currentRunsReadTheViewAndHonourSupersedeVoidAndRollback() {
        record(r1, ClientDeploymentOutcome.SUCCESS, ago(5), a);
        record(r2, ClientDeploymentOutcome.SUCCESS, ago(3), a);
        record(r2, ClientDeploymentOutcome.ROLLED_BACK, ago(1), a);
        correct(r1, record(r1, ClientDeploymentOutcome.SUCCESS, ago(4), b).id(), ClientDeploymentOutcome.FAILED);
        correct(r1, record(r1, ClientDeploymentOutcome.SUCCESS, ago(4), c).id(), ClientDeploymentOutcome.VOIDED);

        List<ClientCurrentRun> runs = portfolio.currentRuns();

        assertThat(runs).singleElement().satisfies(run -> {
            assertThat(run.clientId()).isEqualTo(a.id());
            assertThat(run.productId()).isEqualTo(p1.id());
            assertThat(run.releaseId()).isEqualTo(r2);
        });
    }

    @Test
    void productsByReleaseListsEveryReleaseWithItsProducts() {
        assertThat(portfolio.productsByRelease()).containsOnlyKeys(r1, r2)
                .containsEntry(r1, Set.of(p1.id(), p2.id()))
                .containsEntry(r2, Set.of(p1.id()));
    }

    @Test
    void successfulInstallsSinceCountsOnlyEffectiveSuccessInsideTheWindow() {
        record(r1, ClientDeploymentOutcome.SUCCESS, ago(2), a);
        record(r1, ClientDeploymentOutcome.SUCCESS, ago(200), b);
        record(r1, ClientDeploymentOutcome.FAILED, ago(2), b);
        correct(r1, record(r1, ClientDeploymentOutcome.SUCCESS, ago(2), c).id(), ClientDeploymentOutcome.VOIDED);
        ClientDeployment superseded = record(r2, ClientDeploymentOutcome.SUCCESS, ago(2), c);
        correct(r2, superseded.id(), ClientDeploymentOutcome.FAILED);

        List<RecentInstall> installs = portfolio.successfulInstallsSince(ago(24));

        assertThat(installs).containsExactly(new RecentInstall(a.id(), p1.id()));
    }
}
