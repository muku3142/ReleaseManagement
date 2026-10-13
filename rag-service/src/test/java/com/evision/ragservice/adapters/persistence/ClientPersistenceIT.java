package com.evision.ragservice.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.domain.ClientApp;
import com.evision.ragservice.domain.CollectionStatus;
import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.ports.ClientStore;
import com.evision.ragservice.ports.CollectionStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
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
class ClientPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    private static final byte[] HASH_A = {1, 2, 3};
    private static final byte[] HASH_B = {4, 5, 6};

    @Autowired
    private ClientStore clients;

    @Autowired
    private CollectionStore collections;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
    }

    @Test
    void secondCreateWithTheSameNameReturnsEmpty() {
        assertThat(clients.create("alpha", HASH_A)).isPresent();
        assertThat(clients.create("alpha", HASH_B)).isEmpty();
    }

    @Test
    void onlyTheHashIsStored() {
        ClientApp client = clients.create("alpha", HASH_A).orElseThrow();

        byte[] stored = jdbc.sql("SELECT key_hash FROM client_app WHERE id = :id")
                .param("id", client.id()).query(byte[].class).single();
        Long keyColumns = jdbc.sql("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'client_app' AND column_name LIKE '%key%'
                """).query(Long.class).single();

        assertThat(stored).isEqualTo(HASH_A);
        assertThat(keyColumns).isEqualTo(1);
    }

    @Test
    void revokedKeyNoLongerResolves() {
        ClientApp client = clients.create("alpha", HASH_A).orElseThrow();
        assertThat(clients.findActiveIdByKeyHash(HASH_A)).contains(client.id());

        assertThat(clients.revoke(client.id())).isTrue();

        assertThat(clients.findActiveIdByKeyHash(HASH_A)).isEmpty();
        assertThat(clients.find(client.id()).orElseThrow().revokedAt()).isNotNull();
    }

    @Test
    void revokeOfUnknownClientReturnsFalse() {
        assertThat(clients.revoke(UUID.randomUUID())).isFalse();
    }

    @Test
    void replaceKeyKillsTheOldHashImmediately() {
        ClientApp client = clients.create("alpha", HASH_A).orElseThrow();

        assertThat(clients.replaceKey(client.id(), HASH_B)).isTrue();

        assertThat(clients.findActiveIdByKeyHash(HASH_A)).isEmpty();
        assertThat(clients.findActiveIdByKeyHash(HASH_B)).contains(client.id());
    }

    @Test
    void replaceKeyOnRevokedClientReturnsFalse() {
        ClientApp client = clients.create("alpha", HASH_A).orElseThrow();
        clients.revoke(client.id());

        assertThat(clients.replaceKey(client.id(), HASH_B)).isFalse();
    }

    @Test
    void touchLastUsedWritesAtMostOncePerMinute() {
        ClientApp client = clients.create("alpha", HASH_A).orElseThrow();

        clients.touchLastUsed(client.id());
        OffsetDateTime first = lastUsedAt(client.id());
        clients.touchLastUsed(client.id());
        OffsetDateTime second = lastUsedAt(client.id());

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void listIsOrderedByNameAndCarriesNoKeyMaterial() {
        clients.create("beta", HASH_B);
        clients.create("alpha", HASH_A);

        assertThat(clients.list()).extracting(ClientApp::name).containsExactly("alpha", "beta");
    }

    @Test
    void anotherClientsCollectionIsAbsent() {
        UUID a = clients.create("alpha", HASH_A).orElseThrow().id();
        UUID b = clients.create("beta", HASH_B).orElseThrow().id();
        collections.create(a, "docs", "nomic-embed-text").orElseThrow();

        assertThat(collections.find(a, "docs")).isPresent();
        assertThat(collections.find(b, "docs")).isEmpty();
        assertThat(collections.findStatus(b, "docs")).isEmpty();
        assertThat(collections.listStatus(b)).isEmpty();
    }

    @Test
    void createWithAnExistingNameReturnsEmpty() {
        UUID a = clients.create("alpha", HASH_A).orElseThrow().id();

        assertThat(collections.create(a, "docs", "nomic-embed-text")).isPresent();
        assertThat(collections.create(a, "docs", "nomic-embed-text")).isEmpty();
    }

    @Test
    void findStatusCountsDocumentsByStatusAndReportsLastIndexedTime() {
        UUID a = clients.create("alpha", HASH_A).orElseThrow().id();
        DocumentCollection collection = collections.create(a, "docs", "nomic-embed-text").orElseThrow();
        insertDocument(collection.id(), "d1", "PENDING", false);
        insertDocument(collection.id(), "d2", "INDEXED", true);
        insertDocument(collection.id(), "d3", "FAILED", false);

        Optional<CollectionStatus> status = collections.findStatus(a, "docs");

        assertThat(status).isPresent();
        assertThat(status.get().pending()).isEqualTo(1);
        assertThat(status.get().indexed()).isEqualTo(1);
        assertThat(status.get().failed()).isEqualTo(1);
        assertThat(status.get().lastIndexedAt()).isNotNull();
        assertThat(collections.listStatus(a)).hasSize(1);
    }

    private OffsetDateTime lastUsedAt(UUID id) {
        return jdbc.sql("SELECT last_used_at FROM client_app WHERE id = :id")
                .param("id", id).query(OffsetDateTime.class).single();
    }

    private void insertDocument(UUID collectionId, String externalId, String status, boolean indexed) {
        jdbc.sql("""
                INSERT INTO document (collection_id, external_id, title, body, content_hash, status, indexed_at)
                VALUES (:collection, :external, 't', 'b', decode('00ff', 'hex'), :status,
                        CASE WHEN :indexed THEN now() END)
                """).param("collection", collectionId).param("external", externalId)
                .param("status", status).param("indexed", indexed).update();
    }
}
