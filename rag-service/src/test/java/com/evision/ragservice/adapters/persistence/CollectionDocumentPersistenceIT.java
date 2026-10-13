package com.evision.ragservice.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.domain.DocumentStatus;
import com.evision.ragservice.domain.DocumentView;
import com.evision.ragservice.domain.QueueDepth;
import com.evision.ragservice.ports.ClientStore;
import com.evision.ragservice.ports.CollectionStore;
import com.evision.ragservice.ports.DocumentStore;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
class CollectionDocumentPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    private static final byte[] HASH_1 = {1};
    private static final byte[] HASH_2 = {2};
    private static final String VECTOR = IntStream.range(0, 768)
            .mapToObj(i -> "0.001").collect(Collectors.joining(",", "[", "]"));

    @Autowired
    private ClientStore clients;

    @Autowired
    private CollectionStore collections;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private JdbcClient jdbc;

    private UUID clientId;
    private UUID collectionId;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
        clientId = clients.create("alpha", new byte[]{9}).orElseThrow().id();
        collectionId = collections.create(clientId, "docs", "nomic-embed-text").orElseThrow().id();
    }

    @Test
    void firstPushIsPendingAndStoresBodyHashAndMetadata() {
        Map<String, Object> metadata = Map.of("team", "payments", "year", 2026);

        DocumentStatus status = documents.upsert(collectionId, content("d1", "Title", "Body", "http://x", metadata), HASH_1);

        assertThat(status).isEqualTo(DocumentStatus.PENDING);
        assertThat(jdbc.sql("SELECT body FROM document WHERE external_id = 'd1'").query(String.class).single())
                .isEqualTo("Body");
        assertThat(jdbc.sql("SELECT content_hash FROM document WHERE external_id = 'd1'").query(byte[].class).single())
                .isEqualTo(HASH_1);
        DocumentView view = documents.find(collectionId, "d1").orElseThrow();
        assertThat(view.metadata()).containsEntry("team", "payments").containsEntry("year", 2026);
    }

    @Test
    void unchangedContentKeepsStatusAndUpdatesUrlAndMetadata() {
        documents.upsert(collectionId, content("d1", "Title", "Body", "http://old", Map.of("a", "1")), HASH_1);
        jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now()").update();
        OffsetDateTime indexedAt = jdbc.sql("SELECT indexed_at FROM document").query(OffsetDateTime.class).single();

        DocumentStatus status = documents.upsert(collectionId,
                content("d1", "Title", "Body", "http://new", Map.of("a", "2")), HASH_1);

        assertThat(status).isEqualTo(DocumentStatus.INDEXED);
        DocumentView view = documents.find(collectionId, "d1").orElseThrow();
        assertThat(view.url()).isEqualTo("http://new");
        assertThat(view.metadata()).containsEntry("a", "2");
        assertThat(jdbc.sql("SELECT indexed_at FROM document").query(OffsetDateTime.class).single())
                .isEqualTo(indexedAt);
    }

    @Test
    void changedTextResetsToPendingClearsLeaseAndKeepsChunks() {
        documents.upsert(collectionId, content("d1", "Title", "Body", null, null), HASH_1);
        UUID documentId = jdbc.sql("SELECT id FROM document").query(UUID.class).single();
        jdbc.sql("UPDATE document SET status = 'INDEXED', lease_until = now() + interval '5 minutes'").update();
        insertChunk(documentId);

        DocumentStatus status = documents.upsert(collectionId, content("d1", "Title", "New body", null, null), HASH_2);

        assertThat(status).isEqualTo(DocumentStatus.PENDING);
        assertThat(jdbc.sql("SELECT lease_until FROM document").query(OffsetDateTime.class).optional()).isEmpty();
        assertThat(documents.find(collectionId, "d1").orElseThrow().statusReason()).isNull();
        assertThat(chunkCount()).isEqualTo(1);
    }

    @Test
    void failedDocumentRePushedReturnsToPending() {
        documents.upsert(collectionId, content("d1", "Title", "Body", null, null), HASH_1);
        jdbc.sql("UPDATE document SET status = 'FAILED', status_reason = 'embedding_unavailable'").update();

        DocumentStatus status = documents.upsert(collectionId, content("d1", "Title", "Body", null, null), HASH_1);

        assertThat(status).isEqualTo(DocumentStatus.PENDING);
        assertThat(documents.find(collectionId, "d1").orElseThrow().statusReason()).isNull();
    }

    @Test
    void deleteRemovesTheDocumentAndItsChunks() {
        documents.upsert(collectionId, content("d1", "Title", "Body", null, null), HASH_1);
        insertChunk(jdbc.sql("SELECT id FROM document").query(UUID.class).single());

        assertThat(documents.delete(collectionId, "d1")).isTrue();

        assertThat(chunkCount()).isZero();
        assertThat(documents.delete(collectionId, "unknown")).isFalse();
    }

    @Test
    void findNeverCrossesCollections() {
        UUID other = collections.create(clientId, "other", "nomic-embed-text").orElseThrow().id();
        documents.upsert(other, content("shared", "Title", "Body", null, null), HASH_1);

        assertThat(documents.find(collectionId, "shared")).isEmpty();
        assertThat(documents.find(other, "shared")).isPresent();
    }

    @Test
    void requeueAllResetsEveryDocumentAndKeepsChunks() {
        documents.upsert(collectionId, content("d1", "T", "B", null, null), HASH_1);
        documents.upsert(collectionId, content("d2", "T", "B", null, null), HASH_1);
        documents.upsert(collectionId, content("d3", "T", "B", null, null), HASH_1);
        jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE external_id = 'd1'").update();
        jdbc.sql("UPDATE document SET status = 'FAILED', status_reason = 'x' WHERE external_id = 'd2'").update();
        insertChunk(jdbc.sql("SELECT id FROM document WHERE external_id = 'd1'").query(UUID.class).single());

        int requeued = documents.requeueAll(collectionId);

        assertThat(requeued).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM document WHERE status = 'PENDING' AND status_reason IS NULL")
                .query(Long.class).single()).isEqualTo(3);
        assertThat(chunkCount()).isEqualTo(1);
    }

    @Test
    void queueDepthCountsOnlyTheGivenClientsDocuments() {
        UUID otherClient = clients.create("beta", new byte[]{8}).orElseThrow().id();
        UUID otherCollection = collections.create(otherClient, "docs", "nomic-embed-text").orElseThrow().id();
        documents.upsert(collectionId, content("d1", "T", "B", null, null), HASH_1);
        documents.upsert(collectionId, content("d2", "T", "B", null, null), HASH_1);
        jdbc.sql("UPDATE document SET status = 'FAILED' WHERE external_id = 'd2'").update();
        documents.upsert(otherCollection, content("x1", "T", "B", null, null), HASH_1);

        assertThat(documents.queueDepth(clientId)).isEqualTo(new QueueDepth(1, 1));
        assertThat(documents.queueDepth(otherClient)).isEqualTo(new QueueDepth(1, 0));
    }

    private static DocumentContent content(String id, String title, String text, String url, Map<String, Object> metadata) {
        return new DocumentContent(id, title, text, url, metadata);
    }

    private long chunkCount() {
        return jdbc.sql("SELECT count(*) FROM chunk").query(Long.class).single();
    }

    private void insertChunk(UUID documentId) {
        jdbc.sql("""
                INSERT INTO chunk (document_id, collection_id, ordinal, content, embedding, embedding_model)
                VALUES (:document, :collection, 0, 'c', CAST(:v AS vector), 'nomic-embed-text')
                """).param("document", documentId).param("collection", collectionId).param("v", VECTOR).update();
    }
}
