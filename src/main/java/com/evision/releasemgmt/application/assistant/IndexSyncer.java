package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.domain.assistant.DocRef;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.assistant.RagServiceException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.RagIndexPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// Converges the rag-service index to the database for one release. Failures are logged as metadata only and
// dropped: the next sync or the periodic sweep heals them (D-12-04, D-12-12). Deliberately not @Transactional -
// slow network work must not hold a DB connection.
@Service
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class IndexSyncer {

    private static final Logger log = LoggerFactory.getLogger(IndexSyncer.class);
    private static final int MODEL_CONFLICT = 409;
    private static final String UNKNOWN_STATUS = "n/a";

    private final ReleaseRepository releases;
    private final DocumentSnapshotRepository snapshots;
    private final RagIndexPort index;
    // Held across read and push so an older read cannot overwrite a newer push.
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, Set<DocRef>> recorded = new ConcurrentHashMap<>();

    public IndexSyncer(ReleaseRepository releases, DocumentSnapshotRepository snapshots, RagIndexPort index) {
        this.releases = releases;
        this.snapshots = snapshots;
        this.index = index;
    }

    public void sync(UUID releaseId) {
        lock.lock();
        try {
            Optional<Release> release = releases.findById(releaseId);
            List<IndexDocument> desired = release.map(this::desiredDocuments).orElse(List.of());
            Set<DocRef> desiredRefs = new HashSet<>();
            desired.forEach(document -> desiredRefs.add(document.ref()));

            Set<DocRef> known = recorded.computeIfAbsent(releaseId, id -> ConcurrentHashMap.newKeySet());
            known.addAll(desiredRefs);
            desired.forEach(this::push);
            deleteUndesired(releaseId, known, desiredRefs);
        } finally {
            lock.unlock();
        }
    }

    public void ensureCollections() {
        for (IndexCollection collection : IndexCollection.values()) {
            try {
                index.ensureCollection(collection);
            } catch (RuntimeException e) {
                int status = statusOf(e);
                if (status == MODEL_CONFLICT) {
                    log.error("rag index collection model conflict collection={} status={}", collection, status);
                } else {
                    log.warn("rag index ensure collection failed collection={} status={}", collection,
                            status == 0 ? UNKNOWN_STATUS : status);
                }
            }
        }
    }

    public Set<UUID> recordedReleaseIds() {
        return new LinkedHashSet<>(recorded.keySet());
    }

    private List<IndexDocument> desiredDocuments(Release release) {
        List<DocumentSnapshot> latest = new ArrayList<>();
        for (TemplateDocumentType type : TemplateDocumentType.values()) {
            snapshots.findLatest(release.id(), type).ifPresent(latest::add);
        }
        return IndexDocumentMapper.map(release, latest);
    }

    private void push(IndexDocument document) {
        try {
            index.put(document);
        } catch (RuntimeException e) {
            log.warn("rag index put failed collection={} id={} status={}", document.collection(), document.id(),
                    statusLabel(e));
        }
    }

    private void deleteUndesired(UUID releaseId, Set<DocRef> known, Set<DocRef> desiredRefs) {
        for (DocRef ref : new ArrayList<>(known)) {
            if (desiredRefs.contains(ref)) {
                continue;
            }
            try {
                index.delete(ref);
                known.remove(ref);
            } catch (RuntimeException e) {
                log.warn("rag index delete failed collection={} id={} status={}", ref.collection(), ref.id(),
                        statusLabel(e));
            }
        }
        if (known.isEmpty()) {
            recorded.remove(releaseId);
        }
    }

    private static int statusOf(RuntimeException e) {
        return e instanceof RagServiceException rag ? rag.status() : 0;
    }

    private static Object statusLabel(RuntimeException e) {
        int status = statusOf(e);
        return status == 0 ? UNKNOWN_STATUS : status;
    }
}
