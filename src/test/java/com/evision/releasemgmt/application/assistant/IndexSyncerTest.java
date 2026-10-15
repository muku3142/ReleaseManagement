package com.evision.releasemgmt.application.assistant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.domain.assistant.DocRef;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.assistant.RagServiceException;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexSyncerTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final UUID RELEASE_ID = UUID.randomUUID();

    private final ReleaseRepository releases = mock(ReleaseRepository.class);
    private final DocumentSnapshotRepository snapshots = mock(DocumentSnapshotRepository.class);
    private final FakeIndex index = new FakeIndex();
    private final IndexSyncer syncer = new IndexSyncer(releases, snapshots, index);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger syncerLogger = (Logger) LoggerFactory.getLogger(IndexSyncer.class);

    @BeforeEach
    void setUp() {
        when(snapshots.findLatest(any(), any())).thenReturn(Optional.empty());
        logs.start();
        syncerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        syncerLogger.detachAppender(logs);
    }

    @Test
    void firstSyncPutsEveryDocumentAndRecordsIt() {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1", "PAY-2")));

        syncer.sync(RELEASE_ID);

        assertThat(index.puts).hasSize(2);
        assertThat(syncer.recordedReleaseIds()).containsExactly(RELEASE_ID);
    }

    @Test
    void removedTicketIsDeletedOnNextSync() {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1", "PAY-2")));
        syncer.sync(RELEASE_ID);
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1")));
        index.puts.clear();

        syncer.sync(RELEASE_ID);

        assertThat(index.puts).hasSize(1);
        assertThat(index.deletes).containsExactly(ticketRef("PAY-2"));
    }

    @Test
    void failedDeleteStaysRecordedAndIsRetried() {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1", "PAY-2")));
        syncer.sync(RELEASE_ID);
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1")));
        index.failDeletes = true;
        syncer.sync(RELEASE_ID);
        index.failDeletes = false;
        index.deletes.clear();

        syncer.sync(RELEASE_ID);

        assertThat(index.deletes).containsExactly(ticketRef("PAY-2"));
        index.deletes.clear();
        syncer.sync(RELEASE_ID);
        assertThat(index.deletes).isEmpty();
    }

    @Test
    void failedPutDoesNotStopTheRestAndIsStillRecorded() {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1", "PAY-2")));
        index.failPutFor = ticketRef("PAY-1");

        syncer.sync(RELEASE_ID);

        assertThat(index.puts).extracting(IndexDocument::ref).containsExactly(ticketRef("PAY-2"));
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.empty());
        syncer.sync(RELEASE_ID);
        assertThat(index.deletes).containsExactlyInAnyOrder(ticketRef("PAY-1"), ticketRef("PAY-2"));
    }

    @Test
    void deletedReleaseHasEveryRecordedRefDeletedAndIsForgotten() {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1", "PAY-2")));
        syncer.sync(RELEASE_ID);
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.empty());

        syncer.sync(RELEASE_ID);

        assertThat(index.deletes).containsExactlyInAnyOrder(ticketRef("PAY-1"), ticketRef("PAY-2"));
        assertThat(syncer.recordedReleaseIds()).isEmpty();
    }

    @Test
    void logsCarryMetadataOnly() {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1")));
        index.failPutFor = ticketRef("PAY-1");

        syncer.sync(RELEASE_ID);

        assertThat(logs.list).hasSize(1);
        String line = logs.list.get(0).getFormattedMessage();
        assertThat(line).contains("TICKETS", ticketRef("PAY-1").id(), "503")
                .doesNotContain("Secret summary", "Secret description");
    }

    @Test
    void concurrentSyncsNeverInterleave() throws Exception {
        when(releases.findById(RELEASE_ID)).thenReturn(Optional.of(release("PAY-1", "PAY-2", "PAY-3")));
        index.detectOverlap = true;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(() -> {
                await(start);
                syncer.sync(RELEASE_ID);
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(index.putThreads).hasSize(12);
        for (int block = 0; block < 12; block += 3) {
            assertThat(index.putThreads.subList(block, block + 3)).containsOnly(index.putThreads.get(block));
        }
    }

    @Test
    void ensureCollectionsNeverThrows() {
        index.failEnsure = true;

        syncer.ensureCollections();

        assertThat(index.ensured).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static DocRef ticketRef(String key) {
        return new DocRef(IndexCollection.TICKETS, "ticket:" + RELEASE_ID + ":" + key);
    }

    private static Release release(String... keys) {
        List<ReleaseItem> items = new ArrayList<>();
        for (String key : keys) {
            items.add(new ReleaseItem(new TicketDetail(key, "Secret summary", "Bug", "Done", null, "Ann", null,
                    List.of(), null, "Secret description", List.of(), 0, "1", NOW, StatusCategory.DONE,
                    new BigDecimal("5.0"), true), NOW, CodeLinks.notAttempted()));
        }
        return new Release(RELEASE_ID, "Spring", "1.0", ReleaseStatus.OPEN, "PAY", NOW, "u", null, null, null, null,
                items, List.of());
    }

    private static final class FakeIndex implements RagIndexPort {
        final List<IndexDocument> puts = new CopyOnWriteArrayList<>();
        final List<DocRef> deletes = new CopyOnWriteArrayList<>();
        final List<IndexCollection> ensured = new CopyOnWriteArrayList<>();
        final List<Long> putThreads = new CopyOnWriteArrayList<>();
        volatile boolean failDeletes;
        volatile boolean failEnsure;
        volatile boolean detectOverlap;
        volatile DocRef failPutFor;

        @Override
        public void ensureCollection(IndexCollection collection) {
            if (failEnsure) {
                throw new RagServiceException("down", 503);
            }
            ensured.add(collection);
        }

        @Override
        public void put(IndexDocument document) {
            if (detectOverlap) {
                Thread.yield();
                putThreads.add(Thread.currentThread().threadId());
            }
            if (document.ref().equals(failPutFor)) {
                throw new RagServiceException("down", 503);
            }
            puts.add(document);
        }

        @Override
        public void delete(DocRef ref) {
            if (failDeletes) {
                throw new RagServiceException("down", 503);
            }
            deletes.add(ref);
        }
    }
}
