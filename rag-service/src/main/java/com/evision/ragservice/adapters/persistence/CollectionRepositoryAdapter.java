package com.evision.ragservice.adapters.persistence;

import com.evision.ragservice.domain.CollectionStatus;
import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.ports.CollectionStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CollectionRepositoryAdapter implements CollectionStore {

    private final JdbcClient jdbc;

    CollectionRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DocumentCollection> find(UUID clientId, String name) {
        return jdbc.sql("""
                        SELECT id, client_id, name, embedding_model, created_at FROM collection
                        WHERE client_id = :clientId AND name = :name
                        """)
                .param("clientId", clientId).param("name", name)
                .query(CollectionRepositoryAdapter::mapCollection).optional();
    }

    @Override
    @Transactional
    public Optional<DocumentCollection> create(UUID clientId, String name, String embeddingModel) {
        return jdbc.sql("""
                        INSERT INTO collection (client_id, name, embedding_model)
                        VALUES (:clientId, :name, :model)
                        ON CONFLICT (client_id, name) DO NOTHING
                        RETURNING id, client_id, name, embedding_model, created_at
                        """)
                .param("clientId", clientId).param("name", name).param("model", embeddingModel)
                .query(CollectionRepositoryAdapter::mapCollection).optional();
    }

    @Override
    public List<CollectionStatus> listStatus(UUID clientId) {
        return jdbc.sql("""
                        SELECT c.name, c.embedding_model,
                               count(d.id) FILTER (WHERE d.status = 'PENDING') AS pending,
                               count(d.id) FILTER (WHERE d.status = 'INDEXED') AS indexed,
                               count(d.id) FILTER (WHERE d.status = 'FAILED')  AS failed,
                               max(d.indexed_at) AS last_indexed_at
                        FROM collection c LEFT JOIN document d ON d.collection_id = c.id
                        WHERE c.client_id = :clientId
                        GROUP BY c.id, c.name, c.embedding_model ORDER BY c.name
                        """)
                .param("clientId", clientId)
                .query(CollectionRepositoryAdapter::mapStatus).list();
    }

    @Override
    public Optional<CollectionStatus> findStatus(UUID clientId, String name) {
        return jdbc.sql("""
                        SELECT c.name, c.embedding_model,
                               count(d.id) FILTER (WHERE d.status = 'PENDING') AS pending,
                               count(d.id) FILTER (WHERE d.status = 'INDEXED') AS indexed,
                               count(d.id) FILTER (WHERE d.status = 'FAILED')  AS failed,
                               max(d.indexed_at) AS last_indexed_at
                        FROM collection c LEFT JOIN document d ON d.collection_id = c.id
                        WHERE c.client_id = :clientId AND c.name = :name
                        GROUP BY c.id, c.name, c.embedding_model
                        """)
                .param("clientId", clientId).param("name", name)
                .query(CollectionRepositoryAdapter::mapStatus).optional();
    }

    private static DocumentCollection mapCollection(ResultSet rs, int row) throws SQLException {
        return new DocumentCollection(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class),
                rs.getString("name"), rs.getString("embedding_model"),
                ClientRepositoryAdapter.instant(rs, "created_at"));
    }

    private static CollectionStatus mapStatus(ResultSet rs, int row) throws SQLException {
        return new CollectionStatus(rs.getString("name"), rs.getString("embedding_model"),
                rs.getLong("pending"), rs.getLong("indexed"), rs.getLong("failed"),
                ClientRepositoryAdapter.instant(rs, "last_indexed_at"));
    }
}
