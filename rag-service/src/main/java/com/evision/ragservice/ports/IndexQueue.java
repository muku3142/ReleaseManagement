package com.evision.ragservice.ports;

import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.ClaimedDocument;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IndexQueue {

    List<ClaimedDocument> claim(int batchSize, Duration lease);

    void release(Collection<UUID> documentIds);

    /** False when the content hash is stale and the result was discarded. */
    boolean complete(UUID documentId, byte[] contentHash, String embeddingModel, List<ChunkEmbedding> chunks);

    void fail(UUID documentId, byte[] contentHash, String reason);
}
