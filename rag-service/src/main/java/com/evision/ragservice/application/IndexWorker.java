package com.evision.ragservice.application;

import com.evision.ragservice.domain.ChunkEmbedding;
import com.evision.ragservice.domain.Chunker;
import com.evision.ragservice.domain.ClaimedDocument;
import com.evision.ragservice.domain.EmbeddingPurpose;
import com.evision.ragservice.domain.EmbeddingRejectedException;
import com.evision.ragservice.domain.EmbeddingUnavailableException;
import com.evision.ragservice.ports.EmbeddingPort;
import com.evision.ragservice.ports.IndexQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Deliberately not transactional: embedding is a slow network call and must never hold a DB connection
@Component
public class IndexWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexWorker.class);

    private final IndexQueue queue;
    private final EmbeddingPort embeddings;
    private final Chunker chunker;
    private final int batchSize;
    private final Duration lease;
    private final Duration backoffMin;
    private final Duration backoffMax;

    private volatile Duration backoff;
    private volatile Instant notBefore = Instant.EPOCH;

    public IndexWorker(IndexQueue queue, EmbeddingPort embeddings,
                       @Value("${rag.chunking.max-chars}") int maxChars,
                       @Value("${rag.chunking.overlap-chars}") int overlapChars,
                       @Value("${rag.worker.batch-size}") int batchSize,
                       @Value("${rag.worker.lease}") Duration lease,
                       @Value("${rag.worker.backoff-min}") Duration backoffMin,
                       @Value("${rag.worker.backoff-max}") Duration backoffMax) {
        this.queue = queue;
        this.embeddings = embeddings;
        this.chunker = new Chunker(maxChars, overlapChars);
        this.batchSize = batchSize;
        this.lease = lease;
        this.backoffMin = backoffMin;
        this.backoffMax = backoffMax;
    }

    @Scheduled(initialDelayString = "${rag.worker.initial-delay}", fixedDelayString = "${rag.worker.poll-interval}")
    public void tick() {
        if (Instant.now().isBefore(notBefore)) {
            return;
        }
        runOnce();
    }

    public int runOnce() {
        long started = System.nanoTime();
        List<ClaimedDocument> claimed = queue.claim(batchSize, lease);
        int indexed = 0;
        int failed = 0;
        for (int i = 0; i < claimed.size(); i++) {
            ClaimedDocument doc = claimed.get(i);
            List<String> passages = chunker.chunk(doc.title(), doc.text());
            if (passages.isEmpty()) {
                queue.fail(doc.id(), doc.contentHash(), "Document has no indexable text");
                failed++;
                continue;
            }
            List<String> inputs = passages.stream().map(p -> Chunker.withHeader(doc.title(), p)).toList();
            List<float[]> vectors;
            try {
                vectors = embeddings.embed(doc.embeddingModel(), EmbeddingPurpose.DOCUMENT, inputs);
            } catch (EmbeddingUnavailableException e) {
                List<UUID> remaining = claimed.subList(i, claimed.size()).stream().map(ClaimedDocument::id).toList();
                queue.release(remaining);
                backOff();
                log.warn("Embedding unavailable; {} documents left pending; next attempt in {}",
                        remaining.size(), backoff);
                return indexed;
            } catch (EmbeddingRejectedException e) {
                queue.fail(doc.id(), doc.contentHash(), "Embedding model rejected the content");
                log.info("Document {} failed: content rejected", doc.id());
                failed++;
                recover();
                continue;
            }
            recover();
            List<ChunkEmbedding> chunks = new ArrayList<>(passages.size());
            for (int c = 0; c < passages.size(); c++) {
                chunks.add(new ChunkEmbedding(c, passages.get(c), vectors.get(c)));
            }
            if (queue.complete(doc.id(), doc.contentHash(), doc.embeddingModel(), chunks)) {
                indexed++;
            } else {
                log.debug("Discarded stale embedding for document {}", doc.id());
            }
        }
        if (!claimed.isEmpty()) {
            log.info("Index run: claimed {}, indexed {}, failed {}, {} ms", claimed.size(), indexed, failed,
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
        return indexed;
    }

    Duration currentBackoff() {
        return backoff;
    }

    private void backOff() {
        Duration current = backoff;
        backoff = current == null ? backoffMin : min(current.multipliedBy(2), backoffMax);
        notBefore = Instant.now().plus(backoff);
    }

    private void recover() {
        backoff = null;
        notBefore = Instant.EPOCH;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
