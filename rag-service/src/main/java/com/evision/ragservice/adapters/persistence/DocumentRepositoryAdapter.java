package com.evision.ragservice.adapters.persistence;

import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.domain.DocumentStatus;
import com.evision.ragservice.domain.DocumentView;
import com.evision.ragservice.domain.QueueDepth;
import com.evision.ragservice.ports.DocumentStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class DocumentRepositoryAdapter implements DocumentStore {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    DocumentRepositoryAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // Unchanged content keeps status/lease; a content change or a FAILED document goes back to PENDING with the lease
    // cleared. Old chunks stay searchable until the replacement lands.
    @Override
    @Transactional
    public DocumentStatus upsert(UUID collectionId, DocumentContent content, byte[] contentHash) {
        Map<String, Object> metadata = content.metadata() == null ? Map.of() : content.metadata();
        String status = jdbc.sql("""
                        INSERT INTO document (collection_id, external_id, title, url, metadata, body, content_hash, status)
                        VALUES (:collectionId, :externalId, :title, :url, CAST(:metadata AS jsonb), :body, :hash, 'PENDING')
                        ON CONFLICT (collection_id, external_id) DO UPDATE SET
                            url = EXCLUDED.url,
                            metadata = EXCLUDED.metadata,
                            title = EXCLUDED.title,
                            body = EXCLUDED.body,
                            updated_at = now(),
                            status = CASE WHEN document.content_hash = EXCLUDED.content_hash AND document.status <> 'FAILED'
                                          THEN document.status ELSE 'PENDING' END,
                            status_reason = CASE WHEN document.content_hash = EXCLUDED.content_hash AND document.status <> 'FAILED'
                                                 THEN document.status_reason ELSE NULL END,
                            lease_until = CASE WHEN document.content_hash = EXCLUDED.content_hash AND document.status <> 'FAILED'
                                               THEN document.lease_until ELSE NULL END,
                            content_hash = EXCLUDED.content_hash
                        RETURNING status
                        """)
                .param("collectionId", collectionId)
                .param("externalId", content.externalId())
                .param("title", content.title())
                .param("url", content.url())
                .param("metadata", objectMapper.writeValueAsString(metadata))
                .param("body", content.text())
                .param("hash", contentHash)
                .query(String.class).single();
        return DocumentStatus.valueOf(status);
    }

    @Override
    @Transactional
    public boolean delete(UUID collectionId, String externalId) {
        return jdbc.sql("DELETE FROM document WHERE collection_id = :collectionId AND external_id = :externalId")
                .param("collectionId", collectionId).param("externalId", externalId).update() == 1;
    }

    @Override
    public Optional<DocumentView> find(UUID collectionId, String externalId) {
        return jdbc.sql("""
                        SELECT external_id, title, url, metadata, status, status_reason, indexed_at, updated_at
                        FROM document WHERE collection_id = :collectionId AND external_id = :externalId
                        """)
                .param("collectionId", collectionId).param("externalId", externalId)
                .query(this::mapView).optional();
    }

    @Override
    @Transactional
    public int requeueAll(UUID collectionId) {
        return jdbc.sql("""
                        UPDATE document SET status = 'PENDING', status_reason = NULL, lease_until = NULL, updated_at = now()
                        WHERE collection_id = :collectionId
                        """)
                .param("collectionId", collectionId).update();
    }

    @Override
    public QueueDepth queueDepth(UUID clientId) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE d.status = 'PENDING') AS pending,
                               count(*) FILTER (WHERE d.status = 'FAILED') AS failed
                        FROM document d JOIN collection c ON c.id = d.collection_id
                        WHERE c.client_id = :clientId
                        """)
                .param("clientId", clientId)
                .query((rs, row) -> new QueueDepth(rs.getLong("pending"), rs.getLong("failed"))).single();
    }

    private DocumentView mapView(ResultSet rs, int row) throws SQLException {
        return new DocumentView(rs.getString("external_id"), rs.getString("title"), rs.getString("url"),
                objectMapper.readValue(rs.getString("metadata"), METADATA_TYPE),
                DocumentStatus.valueOf(rs.getString("status")), rs.getString("status_reason"),
                ClientRepositoryAdapter.instant(rs, "indexed_at"), ClientRepositoryAdapter.instant(rs, "updated_at"));
    }
}
