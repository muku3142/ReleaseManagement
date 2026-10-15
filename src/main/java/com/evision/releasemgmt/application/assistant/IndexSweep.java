package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Backfill at startup and periodically: heals dropped pushes and catches up after a rag-service outage
// (D-12-05). fixedDelay so a slow sweep never overlaps itself. Boot's own scheduler runs it because
// AssistantConfig enables scheduling; this class declares no scheduler or executor bean.
@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class IndexSweep {

    private static final Logger log = LoggerFactory.getLogger(IndexSweep.class);

    private final ReleaseRepository releases;
    private final IndexSyncer syncer;

    public IndexSweep(ReleaseRepository releases, IndexSyncer syncer) {
        this.releases = releases;
        this.syncer = syncer;
    }

    @Scheduled(initialDelayString = "${app.assistant.sweep.initial-delay}",
            fixedDelayString = "${app.assistant.sweep.interval}")
    public void sweep() {
        long start = System.nanoTime();
        try {
            syncer.ensureCollections();
            List<ReleaseSummary> all = releases.search("");
            Set<UUID> live = new HashSet<>();
            for (ReleaseSummary release : all) {
                live.add(release.id());
                syncer.sync(release.id());
            }
            for (UUID recordedId : syncer.recordedReleaseIds()) {
                if (!live.contains(recordedId)) {
                    syncer.sync(recordedId);
                }
            }
            log.info("rag index sweep releases={} durationMs={}", all.size(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        } catch (RuntimeException e) {
            log.warn("rag index sweep failed exceptionType={}", e.getClass().getSimpleName());
        }
    }
}
