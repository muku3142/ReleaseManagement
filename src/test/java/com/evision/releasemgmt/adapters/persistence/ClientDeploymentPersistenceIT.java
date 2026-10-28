package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentRejectedException;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentRejection;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.RejectedClient;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ClientDeploymentPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

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
    private PromotionService promotionService;

    @Autowired
    private JdbcTemplate jdbc;

    private Product p1;
    private Product p2;
    private Client a;
    private Client b;
    private Client c;
    private Client d;
    private UUID r;
    private UUID r2;

    @BeforeEach
    void fixtures() {
        p1 = products.add("P1");
        p2 = products.add("P2");
        a = clients.add("A");
        b = clients.add("B");
        c = clients.add("C");
        d = clients.add("D");
        for (Client linked : List.of(a, b, c)) {
            clients.link(linked.id(), p1.id());
        }
        clients.link(a.id(), p2.id());
        clients.setArchivedAt(c.id(), Instant.now());
        r = frozenRelease("R", p1.id());
        r2 = frozenRelease("R2", p1.id());
        promotionService.record(r, new PromotionDraft(stageId("DEV"), PromotionOutcome.SUCCESS, null, null), "acc-1");
        promotionService.record(r2, new PromotionDraft(stageId("DEV"), PromotionOutcome.FAILED, null, null), "acc-1");
    }

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID frozenRelease(String name, UUID productId) {
        UUID id = openRelease(name);
        releaseProducts.setProduct(id, productId);
        releaseRepository.freeze(id, "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        return id;
    }

    private UUID openRelease(String name) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        return release.id();
    }

    private UUID stageId(String name) {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE name = ?", UUID.class, name);
    }

    private ClientDeploymentDraft draft(UUID productId, ClientDeploymentOutcome outcome, Instant installedAt) {
        return new ClientDeploymentDraft(productId, outcome, installedAt, null);
    }

    private List<ClientDeployment> record(UUID releaseId, ClientDeploymentDraft draft, Client... targets) {
        List<UUID> ids = List.of(targets).stream().map(Client::id).toList();
        return deployments.appendUnderLock(releaseId, h -> h.record(draft, ids, "acc-1", Instant.now()));
    }

    private ClientDeployment correct(UUID releaseId, UUID originalId, ClientDeploymentOutcome outcome, String reason) {
        return deployments.appendUnderLock(releaseId, h -> List.of(
                h.correct(originalId, outcome, Instant.now().minus(1, ChronoUnit.MINUTES), null, reason, "acc-2",
                        Instant.now()))).getFirst();
    }

    private int installCount() {
        return jdbc.queryForObject("SELECT count(*) FROM client_deployment", Integer.class);
    }

    private String promotionSnapshot() {
        return jdbc.queryForObject("""
                SELECT string_agg(id::text || stage_id::text || outcome::text || deployed_at::text || recorded_at::text
                                  || coalesce(supersedes_id::text, ''), ',' ORDER BY id) FROM promotion
                """, String.class);
    }

    private static Instant ago(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    @Test
    void aBulkRecordInsertsEveryRow() {
        List<ClientDeployment> rows = record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, null), a, b);

        assertThat(rows).hasSize(2);
        assertThat(deployments.load(r).entries()).extracting(ClientDeployment::clientId)
                .containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void aRejectedBulkInsertsNothing() {
        assertThatThrownBy(() -> record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, null), a, c, d))
                .isInstanceOfSatisfying(ClientDeploymentRejectedException.class, e -> {
                    assertThat(e.reason()).isEqualTo(ClientDeploymentRejection.INSTALL_CLIENTS_REJECTED);
                    assertThat(e.clients()).extracting(RejectedClient::clientId).containsExactlyInAnyOrder(c.id(), d.id());
                });

        assertThat(installCount()).isZero();
    }

    @Test
    void anOpenReleaseCannotBeInstalledAndNothingIsInserted() {
        UUID open = openRelease("Open");
        releaseProducts.setProduct(open, p1.id());

        assertThatThrownBy(() -> record(open, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, null), a))
                .isInstanceOfSatisfying(ClientDeploymentRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(ClientDeploymentRejection.INSTALL_RELEASE_NOT_FROZEN));
        assertThat(installCount()).isZero();
    }

    @Test
    void correctingThenVoidingLeavesNeitherOriginalNorCorrectionEffective() {
        ClientDeployment original = record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, ago(2)), a).getFirst();

        ClientDeployment correction = correct(r, original.id(), ClientDeploymentOutcome.FAILED, "wrong outcome");
        correct(r, correction.id(), ClientDeploymentOutcome.VOIDED, "not an install at all");

        ClientDeploymentHistory history = deployments.load(r);
        assertThat(history.entries()).hasSize(3);
        assertThat(history.isEffective(original)).isFalse();
        assertThat(history.isEffective(correction)).isFalse();
        assertThat(installCount()).isEqualTo(3);
        assertThatThrownBy(() -> correct(r, original.id(), ClientDeploymentOutcome.SUCCESS, "again"))
                .isInstanceOfSatisfying(ClientDeploymentRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(ClientDeploymentRejection.INSTALL_ALREADY_SUPERSEDED));
    }

    @Test
    void recordingAndCorrectingInstallsLeavesPromotionRowsByteIdentical() {
        String before = promotionSnapshot();
        assertThat(before).isNotBlank();

        ClientDeployment original = record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, ago(2)), a, b).getFirst();
        correct(r, original.id(), ClientDeploymentOutcome.FAILED, "wrong outcome");

        assertThat(promotionSnapshot()).isEqualTo(before);
    }

    @Test
    void currentRunsFollowTheSingleRuleAndHistoryKeepsEveryRow() {
        record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, ago(3)), a);
        record(r2, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, ago(2)), a);
        assertThat(deployments.currentRunsOfClient(a.id())).singleElement().satisfies(run -> {
            assertThat(run.productId()).isEqualTo(p1.id());
            assertThat(run.releaseId()).isEqualTo(r2);
            assertThat(run.releaseName()).isEqualTo("R2");
            assertThat(run.releaseVersion()).isEqualTo("1.0");
        });

        record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, ago(5)), a);
        assertThat(deployments.currentRunsOfClient(a.id())).extracting(CurrentRun::releaseId).containsExactly(r2);

        record(r2, draft(p1.id(), ClientDeploymentOutcome.ROLLED_BACK, ago(1)), a);
        assertThat(deployments.currentRunsOfClient(a.id())).extracting(CurrentRun::releaseId).containsExactly(r2);

        List<ClientInstall> history = deployments.historyOfClient(a.id());
        assertThat(history).hasSize(4);
        assertThat(history).extracting(i -> i.entry().installedAt())
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(history.getFirst().releaseName()).isEqualTo("R2");
    }

    @Test
    void historyOfClientIncludesSupersededAndVoidedRows() {
        ClientDeployment original = record(r, draft(p1.id(), ClientDeploymentOutcome.SUCCESS, ago(2)), a).getFirst();
        correct(r, original.id(), ClientDeploymentOutcome.VOIDED, "mistake");

        assertThat(deployments.historyOfClient(a.id())).hasSize(2)
                .extracting(i -> i.entry().outcome())
                .containsExactlyInAnyOrder(ClientDeploymentOutcome.SUCCESS, ClientDeploymentOutcome.VOIDED);
        assertThat(deployments.currentRunsOfClient(a.id())).isEmpty();
    }

    @Test
    void anInstallAndRemovalOfItsProductAreSerialisedWithoutAnFkError() throws Exception {
        UUID onP2 = frozenRelease("RP2", p2.id());
        promotionService.record(onP2, new PromotionDraft(stageId("DEV"), PromotionOutcome.SUCCESS, null, null), "acc-1");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> install = pool.submit(() -> attempt(start,
                    () -> record(onP2, draft(p2.id(), ClientDeploymentOutcome.SUCCESS, null), a)));
            Future<Throwable> removal = pool.submit(() -> attempt(start,
                    () -> releaseProducts.setProduct(onP2, p1.id())));
            start.countDown();

            Throwable installFailure = install.get();
            Throwable removalFailure = removal.get();

            assertThat(installFailure instanceof DataIntegrityViolationException).isFalse();
            assertThat(removalFailure instanceof DataIntegrityViolationException).isFalse();
            if (installCount() == 1) {
                assertThat(installFailure).isNull();
                assertThat(removalFailure).isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.PRODUCT_HAS_INSTALLS_ON_RELEASE));
            } else {
                assertThat(installCount()).isZero();
                assertThat(removalFailure).isNull();
                assertThat(installFailure).isInstanceOfSatisfying(ClientDeploymentRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(ClientDeploymentRejection.INSTALL_PRODUCT_NOT_ON_RELEASE));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Throwable attempt(CountDownLatch start, Runnable action) throws InterruptedException {
        start.await();
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
