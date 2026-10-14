package com.evision.ragservice.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.domain.MetadataFilter;
import com.evision.ragservice.domain.MetadataFilter.Clause;
import com.evision.ragservice.domain.SearchHit;
import com.evision.ragservice.ports.ChunkSearch;
import java.util.List;
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
class ChunkSearchPersistenceIT {

    private static final String MODEL = "nomic-embed-text";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ChunkSearch search;

    private UUID clientId;
    private UUID collectionId;

    @BeforeEach
    void seedCollection() {
        jdbc.sql("delete from chunk").update();
        jdbc.sql("delete from document").update();
        jdbc.sql("delete from collection").update();
        jdbc.sql("delete from client_app").update();
        clientId = UUID.randomUUID();
        jdbc.sql("insert into client_app (id, name, key_hash) values (:id, 'it-client', decode('00ff', 'hex'))")
                .param("id", clientId).update();
        collectionId = newCollection("docs");
    }

    private UUID newCollection(String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into collection (id, client_id, name, embedding_model) values (:id, :client, :name, :model)")
                .param("id", id).param("client", clientId).param("name", name).param("model", MODEL).update();
        return id;
    }

    private static float[] unit(int axis) {
        float[] v = new float[768];
        v[axis] = 1f;
        return v;
    }

    private static float[] mix() {
        float[] v = new float[768];
        v[0] = 0.7071f;
        v[1] = 0.7071f;
        return v;
    }

    private UUID seed(UUID collection, String externalId, String metadataJson, float[] vector, String model) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into document (id, collection_id, external_id, title, url, metadata, body, content_hash, status)
                values (:id, :collection, :ext, :title, 'http://x/' || :ext, CAST(:meta AS jsonb), 'Body',
                        decode('0a0b', 'hex'), 'INDEXED')
                """)
                .param("id", id).param("collection", collection).param("ext", externalId)
                .param("title", "T-" + externalId).param("meta", metadataJson).update();
        jdbc.sql("""
                insert into chunk (document_id, collection_id, ordinal, content, embedding, embedding_model)
                values (:doc, :collection, 0, :content, CAST(:v AS vector), :model)
                """)
                .param("doc", id).param("collection", collection).param("content", "text-" + externalId)
                .param("v", VectorLiteral.of(vector)).param("model", model).update();
        return id;
    }

    private List<SearchHit> run(MetadataFilter filter, float[] query, int k) {
        return search.search(List.of(collectionId), MODEL, query, filter, k);
    }

    private static MetadataFilter filter(Clause... clauses) {
        return new MetadataFilter(List.of(clauses));
    }

    @Test
    void ranksByCosineSimilarityWithSourceReferences() {
        seed(collectionId, "e0", "{}", unit(0), MODEL);
        seed(collectionId, "mix", "{}", mix(), MODEL);
        seed(collectionId, "e1", "{}", unit(1), MODEL);

        List<SearchHit> hits = run(MetadataFilter.none(), unit(0), 10);

        assertThat(hits).extracting(SearchHit::documentId).containsExactly("e0", "mix", "e1");
        assertThat(hits.get(0).score()).isCloseTo(1.0, within(1e-4));
        assertThat(hits.get(0).title()).isEqualTo("T-e0");
        assertThat(hits.get(0).url()).isEqualTo("http://x/e0");
        assertThat(hits.get(0).text()).isEqualTo("text-e0");
        assertThat(hits.get(0).chunkIndex()).isZero();
    }

    @Test
    void limitsResultsToK() {
        seed(collectionId, "e0", "{}", unit(0), MODEL);
        seed(collectionId, "mix", "{}", mix(), MODEL);
        seed(collectionId, "e1", "{}", unit(1), MODEL);

        assertThat(run(MetadataFilter.none(), unit(0), 2)).hasSize(2);
    }

    @Test
    void equalityFilterExcludesOtherValuesAndMissingKey() {
        seed(collectionId, "r1", "{\"release_id\":\"R1\"}", unit(0), MODEL);
        seed(collectionId, "r2", "{\"release_id\":\"R2\"}", unit(0), MODEL);
        seed(collectionId, "none", "{}", unit(0), MODEL);

        List<SearchHit> hits = run(filter(new Clause("release_id", List.of("R1"))), unit(0), 10);

        assertThat(hits).extracting(SearchHit::documentId).containsExactly("r1");
        assertThat(hits.get(0).metadata()).containsEntry("release_id", "R1");
    }

    @Test
    void inListAndEqualityAreAnded() {
        seed(collectionId, "a-r1", "{\"project_key\":\"A\",\"release_id\":\"R1\"}", unit(0), MODEL);
        seed(collectionId, "b-r1", "{\"project_key\":\"B\",\"release_id\":\"R1\"}", unit(0), MODEL);
        seed(collectionId, "c-r1", "{\"project_key\":\"C\",\"release_id\":\"R1\"}", unit(0), MODEL);
        seed(collectionId, "a-r2", "{\"project_key\":\"A\",\"release_id\":\"R2\"}", unit(0), MODEL);

        List<SearchHit> inList = run(filter(new Clause("project_key", List.of("A", "B"))), unit(0), 10);
        assertThat(inList).extracting(SearchHit::documentId).containsExactlyInAnyOrder("a-r1", "b-r1", "a-r2");

        List<SearchHit> combined = run(filter(new Clause("project_key", List.of("A", "B")),
                new Clause("release_id", List.of("R1"))), unit(0), 10);
        assertThat(combined).extracting(SearchHit::documentId).containsExactlyInAnyOrder("a-r1", "b-r1");
    }

    @Test
    void numberFilterDoesNotMatchStringOfSameDigits() {
        seed(collectionId, "num", "{\"count\":3}", unit(0), MODEL);
        seed(collectionId, "str", "{\"count\":\"3\"}", unit(0), MODEL);

        List<SearchHit> hits = run(filter(new Clause("count", List.of(3))), unit(0), 10);

        assertThat(hits).extracting(SearchHit::documentId).containsExactly("num");
    }

    @Test
    void otherCollectionsNeverAppear() {
        UUID other = newCollection("other");
        seed(collectionId, "mine", "{}", unit(0), MODEL);
        seed(other, "theirs", "{}", unit(0), MODEL);

        assertThat(run(MetadataFilter.none(), unit(0), 10)).extracting(SearchHit::documentId)
                .containsExactly("mine");
    }

    @Test
    void chunksOfAnotherEmbeddingModelNeverAppear() {
        seed(collectionId, "current", "{}", unit(0), MODEL);
        seed(collectionId, "legacy", "{}", unit(0), "old-model");

        assertThat(run(MetadataFilter.none(), unit(0), 10)).extracting(SearchHit::documentId)
                .containsExactly("current");
    }

    @Test
    void quoteInFilterKeyIsBoundSafely() {
        seed(collectionId, "d", "{\"a\":\"b\"}", unit(0), MODEL);

        List<SearchHit> hits = run(filter(new Clause("a\"b", List.of("x\"; drop table chunk; --"))), unit(0), 10);

        assertThat(hits).isEmpty();
        assertThat(run(MetadataFilter.none(), unit(0), 10)).hasSize(1);
    }

    private UUID foreignCollection() {
        UUID otherClient = UUID.randomUUID();
        jdbc.sql("insert into client_app (id, name, key_hash) values (:id, 'other-client', decode('00ee', 'hex'))")
                .param("id", otherClient).update();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into collection (id, client_id, name, embedding_model) values (:id, :client, 'theirs', :model)")
                .param("id", id).param("client", otherClient).param("model", MODEL).update();
        return id;
    }

    @Test
    void searchesSeveralCollectionsOrderedByScoreAndNeverLeaksAnotherClient() {
        UUID second = newCollection("more-docs");
        UUID foreign = foreignCollection();
        seed(collectionId, "first", "{}", mix(), MODEL);
        seed(second, "second", "{}", unit(0), MODEL);
        seed(foreign, "TENANT-CANARY", "{}", unit(0), MODEL);

        List<SearchHit> hits = search.search(List.of(collectionId, second), MODEL, unit(0), MetadataFilter.none(), 10);

        assertThat(hits).extracting(SearchHit::collectionId).containsExactly(second, collectionId);
        assertThat(hits).extracting(SearchHit::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(hits).extracting(SearchHit::text).noneMatch(t -> t.contains("TENANT-CANARY"));
    }

    @Test
    void metadataFilterAppliesAcrossAllCollections() {
        UUID second = newCollection("more-docs");
        seed(collectionId, "r1", "{\"release_id\":\"R1\"}", unit(0), MODEL);
        seed(second, "r2", "{\"release_id\":\"R2\"}", unit(0), MODEL);
        seed(second, "r1b", "{\"release_id\":\"R1\"}", unit(0), MODEL);

        List<SearchHit> hits = search.search(List.of(collectionId, second), MODEL, unit(0),
                filter(new Clause("release_id", List.of("R1"))), 10);

        assertThat(hits).extracting(SearchHit::documentId).containsExactlyInAnyOrder("r1", "r1b");
    }

    @Test
    void deletedDocumentHasNoHits() {
        UUID id = seed(collectionId, "gone", "{}", unit(0), MODEL);
        jdbc.sql("delete from document where id = :id").param("id", id).update();

        assertThat(run(MetadataFilter.none(), unit(0), 10)).isEmpty();
    }
}
