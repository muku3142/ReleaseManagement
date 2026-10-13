package com.evision.ragservice.adapters.persistence;

import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.ClaimedDocument;
import com.evision.ragservice.ports.IndexQueue;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class IndexQueueRepositoryAdapter implements IndexQueue {

    private final JdbcClient jdbc;

    IndexQueueRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<ClaimedDocument> claim(int batchSize, Duration lease) {
        return jdbc.sql("""
                WITH due AS (
                    SELECT id FROM document
                    WHERE status = 'PENDING' AND (lease_until IS NULL OR lease_until < now())
                    ORDER BY updated_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED)
                UPDATE document d SET lease_until = now() + make_interval(secs => :leaseSeconds)
                FROM due, collection c
                WHERE d.id = due.id AND c.id = d.collection_id
                RETURNING d.id, d.collection_id, c.embedding_model, d.title, d.body, d.content_hash
                """)
                .param("batchSize", batchSize)
                .param("leaseSeconds", (double) lease.toSeconds())
                .query((rs, row) -> new ClaimedDocument(
                        rs.getObject("id", UUID.class),
                        rs.getObject("collection_id", UUID.class),
                        rs.getString("embedding_model"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getBytes("content_hash")))
                .list();
    }

    @Override
    @Transactional
    public void release(Collection<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE document SET lease_until = NULL WHERE id IN (:ids)")
                .param("ids", documentIds)
                .update();
    }

    @Override
    @Transactional
    public boolean complete(UUID documentId, byte[] contentHash, String embeddingModel, List<ChunkEmbedding> chunks) {
        int updated = jdbc.sql("""
                UPDATE document
                SET status = 'INDEXED', indexed_at = now(), lease_until = NULL, status_reason = NULL
                WHERE id = :id AND content_hash = :hash
                """)
                .param("id", documentId)
                .param("hash", contentHash)
                .update();
        if (updated == 0) {
            return false;
        }
        jdbc.sql("DELETE FROM chunk WHERE document_id = :id").param("id", documentId).update();
        for (ChunkEmbedding chunk : chunks) {
            jdbc.sql("""
                    INSERT INTO chunk (document_id, collection_id, ordinal, content, embedding, embedding_model)
                    SELECT :id, collection_id, :ordinal, :content, CAST(:embedding AS vector), :model
                    FROM document WHERE id = :id
                    """)
                    .param("id", documentId)
                    .param("ordinal", chunk.ordinal())
                    .param("content", chunk.content())
                    .param("embedding", VectorLiteral.of(chunk.embedding()))
                    .param("model", embeddingModel)
                    .update();
        }
        return true;
    }

    @Override
    @Transactional
    public void fail(UUID documentId, byte[] contentHash, String reason) {
        jdbc.sql("""
                UPDATE document SET status = 'FAILED', status_reason = :reason, lease_until = NULL
                WHERE id = :id AND content_hash = :hash
                """)
                .param("id", documentId)
                .param("hash", contentHash)
                .param("reason", reason)
                .update();
    }
}
