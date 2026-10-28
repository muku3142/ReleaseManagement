package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.AiRevisionService;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.assistant.RagServiceException;
import com.evision.releasemgmt.domain.assistant.ReleaseIndexInvalidated;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "app.assistant.enabled=true",
        "app.assistant.api-key=test-key",
        "app.assistant.base-url=http://127.0.0.1:9",
        "app.assistant.sweep.initial-delay=PT1H"})
@Testcontainers
@RecordApplicationEvents
class IndexAfterCommitIT {

    private static final Duration PROMPT = Duration.ofSeconds(1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @MockitoBean
    private RagIndexPort index;

    @MockitoBean
    private RagChatPort chat;

    @Autowired
    private ReleaseRepository releases;

    @Autowired
    private DocumentSnapshotRepository snapshots;

    @Autowired
    private AiRevisionService aiRevisions;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAll() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
    }

    private static ReleaseItem item(String key) {
        return new ReleaseItem(new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.08"), null,
                "Ship it", List.of("Looks good"), 2, null, null, null, null, false),
                Instant.now(), CodeLinks.notAttempted());
    }

    private Release newRelease(String name) {
        return releases.create(Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now()),
                List.of(item("CNV-1")));
    }

    private static ManualItem manual(String title) {
        return ManualItem.create(title, "desc", ManualItemCategory.OTHER, Instant.now());
    }

    private static ArgumentMatcher<IndexDocument> documentOf(ManualItem manual) {
        return document -> document.id().equals("manual:" + manual.id());
    }

    private long invalidations(UUID releaseId) {
        return events.stream(ReleaseIndexInvalidated.class)
                .filter(event -> event.releaseId().equals(releaseId)).count();
    }

    private void assertPublishesOnce(UUID releaseId, Runnable mutation) {
        long before = invalidations(releaseId);
        mutation.run();
        assertThat(invalidations(releaseId) - before).isEqualTo(1);
    }

    @Test
    void everyMutatingAdapterMethodPublishesExactlyOneInvalidationAndReadsPublishNone() {
        Release created = newRelease("Table A");
        UUID id = created.id();
        assertThat(invalidations(id)).isEqualTo(1);

        assertPublishesOnce(id, () -> releases.addItems(id, List.of(item("CNV-2"))));
        assertPublishesOnce(id, () -> releases.removeItem(id, "CNV-2"));
        assertPublishesOnce(id, () -> releases.replaceItemSnapshots(id, List.of(item("CNV-1")), Instant.now()));
        assertPublishesOnce(id, () -> releases.saveEnrichment(id, Map.of("CNV-1", CodeLinks.notAttempted())));
        ManualItem manual = manual("Script");
        assertPublishesOnce(id, () -> releases.addManualItem(id, manual));
        assertPublishesOnce(id, () -> releases.updateManualItem(id,
                new ManualItem(manual.id(), "Edited", "d", ManualItemCategory.OTHER, manual.addedAt())));
        assertPublishesOnce(id, () -> releases.removeManualItem(id, manual.id()));
        assertPublishesOnce(id, () -> snapshots.create(DocumentSnapshot.create(id,
                TemplateDocumentType.RELEASE_NOTES, 1, "# Body", UUID.randomUUID(), "T", "Body",
                new ChromeFields(true, "Cover", "Sub", "Header", "Footer", true, 55), "rm@acme.com",
                Instant.now()), null));

        long beforeReads = invalidations(id);
        releases.findById(id);
        releases.search("Table");
        releases.releasesContaining("CNV-1");
        releases.findTicketKeys(id);
        assertThat(invalidations(id)).isEqualTo(beforeReads);

        assertPublishesOnce(id, () -> releases.freeze(id, "rm@acme.com", Instant.now()));

        Release doomed = newRelease("Table B");
        assertPublishesOnce(doomed.id(), () -> releases.delete(doomed.id()));
    }

    @Test
    void savingAnAiRevisionPublishesExactlyOneInvalidationAfterCommit() {
        Release release = newRelease("Ai revision");
        snapshots.create(DocumentSnapshot.create(release.id(), TemplateDocumentType.RUNBOOK, 1, "# Body",
                UUID.randomUUID(), "T", "Body", ChromeFields.empty(), "rm@acme.com", Instant.now()), null);
        long before = invalidations(release.id());

        DocumentSnapshot saved = aiRevisions.save(release.id(), TemplateDocumentType.RUNBOOK, 1, AiMode.CLEAN_UP,
                "instr", "# Clean", "rm@acme.com");

        assertThat(saved.revision()).isEqualTo(2);
        assertThat(invalidations(release.id()) - before).isEqualTo(1);
        verify(index, timeout(5000).atLeastOnce())
                .put(argThat(document -> document.id().equals("document:" + release.id() + ":RUNBOOK")
                        && document.text().contains("# Clean")));
    }

    @Test
    void pushRunsOnlyAfterCommitOnAThreadWithoutATransaction() throws Exception {
        Release release = newRelease("Commit");
        ManualItem manual = manual("After commit");
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicReference<Thread> pushThread = new AtomicReference<>();
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            pushThread.set(Thread.currentThread());
            return null;
        }).when(index).put(argThat(documentOf(manual)));

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            releases.addManualItem(release.id(), manual);
            verify(index, after(300).never()).put(argThat(documentOf(manual)));
        });

        verify(index, timeout(5000).atLeastOnce()).put(argThat(documentOf(manual)));
        assertThat(transactionActive).isFalse();
        assertThat(pushThread.get()).isNotSameAs(Thread.currentThread());
    }

    @Test
    void rolledBackMutationIsNeverPushed() {
        Release release = newRelease("Rollback");
        ManualItem manual = manual("Rolled back");

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            releases.addManualItem(release.id(), manual);
            status.setRollbackOnly();
        });

        verify(index, after(2000).never()).put(argThat(documentOf(manual)));
    }

    @Test
    void blockedPortNeverSlowsMutations() throws Exception {
        Release release = newRelease("Blocking");
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        doAnswer(invocation -> {
            blocked.countDown();
            gate.await();
            return null;
        }).when(index).put(argThat(document -> document.id().startsWith("manual:")));
        try {
            releases.addManualItem(release.id(), manual("First"));
            assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < 3; i++) {
                ManualItem next = manual("Next " + i);
                Instant start = Instant.now();
                releases.addManualItem(release.id(), next);
                assertThat(Duration.between(start, Instant.now())).isLessThan(PROMPT);
            }
        } finally {
            gate.countDown();
        }
    }

    @Test
    void failingPortLeavesTheMutationUntouched() {
        Release release = newRelease("Failing");
        doThrow(new RagServiceException("put failed", 503)).when(index).put(argThat(document -> true));
        ManualItem manual = manual("Survives");

        Release returned = releases.addManualItem(release.id(), manual);

        assertThat(returned.manualItems()).extracting(ManualItem::id).containsExactly(manual.id());
        verify(index, timeout(5000).atLeastOnce()).put(argThat(documentOf(manual)));
        assertThat(releases.findById(release.id()).orElseThrow().manualItems()).hasSize(1);
    }

    @Test
    void manyEventsForOneReleaseWhileASyncIsRunningCoalesceToAtMostOneFollowUp() throws Exception {
        Release release = newRelease("Coalesce");
        ManualItem manual = manual("Coalesced");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger pushes = new AtomicInteger();
        doAnswer(invocation -> {
            pushes.incrementAndGet();
            started.countDown();
            gate.await();
            return null;
        }).when(index).put(argThat(documentOf(manual)));
        try {
            releases.addManualItem(release.id(), manual);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            TransactionTemplate tx = new TransactionTemplate(txManager);
            for (int i = 0; i < 20; i++) {
                tx.executeWithoutResult(status -> publisher.publishEvent(new ReleaseIndexInvalidated(release.id())));
            }
        } finally {
            gate.countDown();
        }

        verify(index, timeout(5000).atLeast(2)).put(argThat(documentOf(manual)));
        verify(index, after(1000).atMost(2)).put(argThat(documentOf(manual)));
        assertThat(pushes.get()).isEqualTo(2);
    }
}
