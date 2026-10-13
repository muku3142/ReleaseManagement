package com.evision.ragservice.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.evision.ragservice.PgVectorImage;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
class SchemaPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private JdbcClient jdbc;

    @Test
    void migrationCreatesAllTables() {
        Long count = jdbc.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name in ('client_app', 'collection', 'document', 'chunk')
                """).query(Long.class).single();

        assertThat(count).isEqualTo(4);
    }

    @Test
    void vectorRoundTripsAsTextLiteral() {
        UUID clientId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String vector = IntStream.range(0, 768)
                .mapToObj(i -> "0.001")
                .collect(Collectors.joining(",", "[", "]"));

        jdbc.sql("insert into client_app (id, name, key_hash) values (:id, 'it-client', decode('00ff', 'hex'))")
                .param("id", clientId).update();
        jdbc.sql("insert into collection (id, client_id, name, embedding_model) values (:id, :client, 'docs', 'nomic-embed-text')")
                .param("id", collectionId).param("client", clientId).update();
        jdbc.sql("""
                insert into document (id, collection_id, external_id, title, body, content_hash, status)
                values (:id, :collection, 'doc-1', 'Title', 'Body', decode('00ff', 'hex'), 'PENDING')
                """).param("id", documentId).param("collection", collectionId).update();
        jdbc.sql("""
                insert into chunk (document_id, collection_id, ordinal, content, embedding, embedding_model)
                values (:document, :collection, 0, 'Body', CAST(:v AS vector), 'nomic-embed-text')
                """).param("document", documentId).param("collection", collectionId).param("v", vector).update();

        Double distance = jdbc.sql("select embedding <=> CAST(:v AS vector) from chunk where document_id = :document")
                .param("v", vector).param("document", documentId)
                .query(Double.class).single();

        assertThat(distance).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void noApproximateIndexExists() {
        Long count = jdbc.sql("select count(*) from pg_indexes where indexdef ilike '%hnsw%' or indexdef ilike '%ivfflat%'")
                .query(Long.class).single();

        assertThat(count).isZero();
    }
}
