package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.PromotionRejectedException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.UnfreezeRejectedException;
import com.evision.releasemgmt.domain.release.UnfreezeRejection;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
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
class ReleaseUnfreezePersistenceIT {

    private static final int RACE_ROUNDS = 5;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ReleaseRepository releases;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private ClientDeploymentRepository deployments;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ReleaseProductRepository releaseProducts;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID release(String name, boolean freeze) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releases.create(release, List.of());
        if (freeze) {
            releases.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        }
        return release.id();
    }

    private UUID stageId(String name) {
        return jdbc.queryForObject("SELECT id FROM environment_stage WHERE name = ?", UUID.class, name);
    }

    private PromotionDraft promotion(String stage, PromotionOutcome outcome) {
        return new PromotionDraft(stageId(stage), outcome, null, null);
    }

    private ClientDeployment install(UUID releaseId) {
        Product product = products.add("P");
        Client client = clients.add("C");
        clients.link(client.id(), product.id());
        releaseProducts.setProduct(releaseId, product.id());
        ClientDeploymentDraft draft = new ClientDeploymentDraft(product.id(), ClientDeploymentOutcome.SUCCESS, null, null);
        return deployments.appendUnderLock(releaseId, h -> h.record(draft, List.of(client.id()), "acc-1", Instant.now()))
                .getFirst();
    }

    private Instant itemsChangedAt(UUID id) {
        return jdbc.queryForObject("SELECT items_changed_at FROM release WHERE id = ?", Timestamp.class, id).toInstant();
    }

    private static ReleaseItem item(String key) {
        return new ReleaseItem(new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.08"), null,
                "Ship it", List.of(), 0, null, null, null, null, false), Instant.now(), CodeLinks.notAttempted());
    }

    private static void assertRejected(Runnable action, UnfreezeRejection expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(UnfreezeRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(expected));
    }

    @Test
    void unfreezeOfACleanFrozenReleaseOpensItAndClearsTheFreezeStamp() {
        UUID id = release("U1", true);
        Instant changedBefore = itemsChangedAt(id);

        Release unfrozen = releases.unfreeze(id);

        assertThat(unfrozen.status()).isEqualTo(ReleaseStatus.OPEN);
        assertThat(unfrozen.frozenAt()).isNull();
        assertThat(unfrozen.frozenBy()).isNull();
        assertThat(itemsChangedAt(id)).isEqualTo(changedBefore);
        assertThat(releases.unfreezeBlocker(id)).isEmpty();
    }

    @Test
    void afterUnfreezeTicketsCanBeAddedAndASecondFreezeStampsTheNewTime() {
        UUID id = release("U2", true);
        Instant firstFrozenAt = releases.findById(id).orElseThrow().frozenAt();
        releases.unfreeze(id);

        releases.addItems(id, List.of(item("CNV-9")));
        Instant later = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Release refrozen = releases.freeze(id, "rm@acme.com", later);

        assertThat(refrozen.items()).hasSize(1);
        assertThat(refrozen.frozenAt()).isEqualTo(later).isAfter(firstFrozenAt);
    }

    @Test
    void anOpenReleaseCannotBeUnfrozen() {
        UUID id = release("U3", false);

        assertRejected(() -> releases.unfreeze(id), UnfreezeRejection.UNFREEZE_RELEASE_NOT_FROZEN);
    }

    @Test
    void aPromotionBlocksUnfreezeAndLeavesTheReleaseFrozen() {
        UUID id = release("U4", true);
        Instant frozenAt = releases.findById(id).orElseThrow().frozenAt();
        promotionService.record(id, promotion("DEV", PromotionOutcome.SUCCESS), "acc-1");

        assertRejected(() -> releases.unfreeze(id), UnfreezeRejection.UNFREEZE_HAS_PROMOTIONS);

        Release after = releases.findById(id).orElseThrow();
        assertThat(after.status()).isEqualTo(ReleaseStatus.FROZEN);
        assertThat(after.frozenAt()).isEqualTo(frozenAt);
        assertThat(releases.unfreezeBlocker(id)).contains(UnfreezeRejection.UNFREEZE_HAS_PROMOTIONS);
    }

    @Test
    void aVoidedPromotionStillBlocksUnfreeze() {
        UUID id = release("U5", true);
        UUID original = promotionService.record(id, promotion("DEV", PromotionOutcome.SUCCESS), "acc-1")
                .entries().getFirst().id();
        PromotionDraft voided = new PromotionDraft(stageId("DEV"), PromotionOutcome.VOIDED,
                Instant.now().minus(10, ChronoUnit.MINUTES), null);
        promotionService.correct(id, original, voided, "wrong environment", "acc-2");

        assertRejected(() -> releases.unfreeze(id), UnfreezeRejection.UNFREEZE_HAS_PROMOTIONS);
    }

    @Test
    void aFailedPromotionStillBlocksUnfreeze() {
        UUID id = release("U6", true);
        promotionService.record(id, promotion("DEV", PromotionOutcome.FAILED), "acc-1");

        assertRejected(() -> releases.unfreeze(id), UnfreezeRejection.UNFREEZE_HAS_PROMOTIONS);
    }

    @Test
    void aVoidedInstallStillBlocksUnfreeze() {
        UUID id = release("U7", true);
        ClientDeployment original = install(id);
        deployments.appendUnderLock(id, h -> List.of(h.correct(original.id(), ClientDeploymentOutcome.VOIDED,
                Instant.now().minus(1, ChronoUnit.MINUTES), null, "not an install", "acc-2", Instant.now())));

        assertRejected(() -> releases.unfreeze(id), UnfreezeRejection.UNFREEZE_HAS_INSTALLS);
        assertThat(releases.unfreezeBlocker(id)).contains(UnfreezeRejection.UNFREEZE_HAS_INSTALLS);
    }

    @Test
    void promotionsAreReportedBeforeInstalls() {
        UUID id = release("U8", true);
        install(id);
        promotionService.record(id, promotion("DEV", PromotionOutcome.SUCCESS), "acc-1");

        assertRejected(() -> releases.unfreeze(id), UnfreezeRejection.UNFREEZE_HAS_PROMOTIONS);
    }

    @Test
    void anUnknownReleaseIsNotFound() {
        assertThatThrownBy(() -> releases.unfreeze(UUID.randomUUID())).isInstanceOf(ReleaseNotFoundException.class);
    }

    @Test
    void aConcurrentPromotionAndUnfreezeNeverLeaveAnOpenReleaseWithLedgerRows() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            UUID id = release("Race" + round, true);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> unfreeze = pool.submit(() -> {
                    start.await();
                    try {
                        releases.unfreeze(id);
                        return true;
                    } catch (UnfreezeRejectedException e) {
                        return false;
                    }
                });
                Future<Boolean> promote = pool.submit(() -> {
                    start.await();
                    try {
                        promotionService.record(id, promotion("DEV", PromotionOutcome.SUCCESS), "acc-1");
                        return true;
                    } catch (PromotionRejectedException e) {
                        return false;
                    }
                });
                start.countDown();

                assertThat(List.of(unfreeze.get(), promote.get())).containsExactlyInAnyOrder(true, false);
            } finally {
                pool.shutdownNow();
            }
            String status = jdbc.queryForObject("SELECT status FROM release WHERE id = ?", String.class, id);
            int promotions = jdbc.queryForObject("SELECT count(*) FROM promotion WHERE release_id = ?", Integer.class, id);
            assertThat(status.equals("OPEN") && promotions > 0).isFalse();
        }
    }
}
