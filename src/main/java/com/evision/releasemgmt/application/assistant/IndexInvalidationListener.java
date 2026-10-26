package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.assistant.ReleaseIndexInvalidated;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class IndexInvalidationListener {

    private final IndexSyncer syncer;
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // The queued flag is cleared only once a task owns the turn, so events arriving while a sync runs collapse into one follow-up.
    private final ReentrantLock turn = new ReentrantLock();

    public IndexInvalidationListener(IndexSyncer syncer) {
        this.syncer = syncer;
    }

    // Must stay enqueue-only: AFTER_COMMIT handlers run on the committing request thread.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onInvalidated(ReleaseIndexInvalidated event) {
        if (queued.add(event.releaseId())) {
            executor.execute(() -> {
                turn.lock();
                try {
                    queued.remove(event.releaseId());
                    syncer.sync(event.releaseId());
                } finally {
                    turn.unlock();
                }
            });
        }
    }
}
