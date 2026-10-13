package com.evision.ragservice.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.ClaimedDocument;
import com.evision.ragservice.ports.IndexQueue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class IndexQueuePersistenceIT {

    private static final String MODEL = "nomic-embed-text";
    private static final Duration LEASE = Duration.ofMinutes(10);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private IndexQueue queue;

    private UUID collectionId;

    @BeforeEach
    void seedCollection() {
        jdbc.sql("delete from chunk").update();
        jdbc.sql("delete from document").update();
        jdbc.sql("delete from collection").update();
        jdbc.sql("delete from client_app").update();
        UUID clientId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        jdbc.sql("insert into client_app (id, name, key_hash) values (:id, 'it-client', decode('00ff', 'hex'))")
                .param("id", clientId).update();
        jdbc.sql("insert into collection (id, client_id, name, embedding_model) values (:id, :client, 'docs', :model)")
                .param("id", collectionId).param("client", clientId).param("model", MODEL).update();
    }

    private UUID insertDocument(String externalId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into document (id, collection_id, external_id, title, body, content_hash, status)
                values (:id, :collection, :ext, 'Title', 'Body', decode('0a0b', 'hex'), :status)
                """)
                .param("id", id).param("collection", collectionId).param("ext", externalId).param("status", status)
                .update();
        return id;
    }

    private static List<ChunkEmbedding> chunks(int count) {
        List<ChunkEmbedding> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float[] vector = new float[768];
            vector[i] = 1f;
            chunks.add(new ChunkEmbedding(i, "chunk " + i, vector));
        }
        return chunks;
    }

    private static final byte[] HASH = {0x0a, 0x0b};

    private String statusOf(UUID id) {
        return jdbc.sql("select status from document where id = :id").param("id", id).query(String.class).single();
    }

    private long chunkCount(UUID id) {
        return jdbc.sql("select count(*) from chunk where document_id = :id").param("id", id)
                .query(Long.class).single();
    }

    @Test
    void claimReturnsOnlyPendingUnleasedDocumentsAndSetsLease() {
        UUID pending = insertDocument("p", "PENDING");
        insertDocument("i", "INDEXED");
        insertDocument("f", "FAILED");

        List<ClaimedDocument> claimed = queue.claim(10, LEASE);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(pending);
        assertThat(claimed.get(0).embeddingModel()).isEqualTo(MODEL);
        assertThat(claimed.get(0).collectionId()).isEqualTo(collectionId);
        assertThat(claimed.get(0).contentHash()).isEqualTo(HASH);
        Boolean leaseInFuture = jdbc.sql("select lease_until > now() from document where id = :id")
                .param("id", pending).query(Boolean.class).single();
        assertThat(leaseInFuture).isTrue();
    }

    @Test
    void secondClaimReturnsNothingWhileLeaseIsHeld() {
        insertDocument("p", "PENDING");

        assertThat(queue.claim(10, LEASE)).hasSize(1);
        assertThat(queue.claim(10, LEASE)).isEmpty();
    }

    @Test
    void concurrentClaimsReceiveDisjointDocuments() throws Exception {
        Set<UUID> all = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            all.add(insertDocument("d" + i, "PENDING"));
        }
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<List<ClaimedDocument>>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return queue.claim(5, LEASE);
                }));
            }
            start.countDown();
            Set<UUID> first = ids(futures.get(0).get());
            Set<UUID> second = ids(futures.get(1).get());

            assertThat(first).hasSize(5).doesNotContainAnyElementsOf(second);
            Set<UUID> union = new HashSet<>(first);
            union.addAll(second);
            assertThat(union).isEqualTo(all);
        }
    }

    private static Set<UUID> ids(List<ClaimedDocument> docs) {
        return docs.stream().map(ClaimedDocument::id).collect(Collectors.toSet());
    }

    @Test
    void expiredLeaseIsClaimableAgain() {
        UUID id = insertDocument("p", "PENDING");
        queue.claim(10, LEASE);
        jdbc.sql("update document set lease_until = now() - interval '1 second' where id = :id")
                .param("id", id).update();

        assertThat(queue.claim(10, LEASE)).extracting(ClaimedDocument::id).containsExactly(id);
    }

    @Test
    void releaseClearsLeaseAndKeepsStatus() {
        UUID id = insertDocument("p", "PENDING");
        queue.claim(10, LEASE);

        queue.release(List.of(id));

        Boolean leaseNull = jdbc.sql("select lease_until is null from document where id = :id")
                .param("id", id).query(Boolean.class).single();
        assertThat(leaseNull).isTrue();
        assertThat(statusOf(id)).isEqualTo("PENDING");
        queue.release(List.of());
    }

    @Test
    void completeWithClaimedHashMarksIndexedAndStoresChunks() {
        UUID id = insertDocument("p", "PENDING");
        queue.claim(10, LEASE);

        boolean stored = queue.complete(id, HASH, MODEL, chunks(3));

        assertThat(stored).isTrue();
        assertThat(statusOf(id)).isEqualTo("INDEXED");
        assertThat(chunkCount(id)).isEqualTo(3);
        Long withModel = jdbc.sql("select count(*) from chunk where document_id = :id and embedding_model = :m")
                .param("id", id).param("m", MODEL).query(Long.class).single();
        assertThat(withModel).isEqualTo(3);
        Boolean indexedAtSet = jdbc.sql("select indexed_at is not null and lease_until is null from document where id = :id")
                .param("id", id).query(Boolean.class).single();
        assertThat(indexedAtSet).isTrue();
    }

    @Test
    void secondCompleteReplacesChunks() {
        UUID id = insertDocument("p", "PENDING");
        queue.complete(id, HASH, MODEL, chunks(3));

        queue.complete(id, HASH, MODEL, chunks(2));

        assertThat(chunkCount(id)).isEqualTo(2);
    }

    @Test
    void completeWithStaleHashIsDiscarded() {
        UUID id = insertDocument("p", "PENDING");
        queue.claim(10, LEASE);
        jdbc.sql("update document set content_hash = decode('ffff', 'hex') where id = :id").param("id", id).update();

        boolean stored = queue.complete(id, HASH, MODEL, chunks(2));

        assertThat(stored).isFalse();
        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void completeAfterDeleteReturnsFalse() {
        UUID id = insertDocument("p", "PENDING");
        jdbc.sql("delete from document where id = :id").param("id", id).update();

        assertThat(queue.complete(id, HASH, MODEL, chunks(2))).isFalse();
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void failMarksFailedOnlyForClaimedHash() {
        UUID id = insertDocument("p", "PENDING");

        queue.fail(id, new byte[] {0x01}, "stale");
        assertThat(statusOf(id)).isEqualTo("PENDING");

        queue.fail(id, HASH, "embedding rejected");
        assertThat(statusOf(id)).isEqualTo("FAILED");
        String reason = jdbc.sql("select status_reason from document where id = :id").param("id", id)
                .query(String.class).single();
        assertThat(reason).isEqualTo("embedding rejected");
    }
}
