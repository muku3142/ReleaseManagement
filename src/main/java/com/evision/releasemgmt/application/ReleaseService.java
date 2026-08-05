package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReleaseService {

    private final ReleaseRepository repository;

    public ReleaseService(ReleaseRepository repository) {
        this.repository = repository;
    }

    public Release create(String name, String version, String jiraProjectKey, String actor) {
        Release release = Release.create(name.trim(), version.trim(), blankToNull(jiraProjectKey),
                actor, Instant.now());
        return repository.create(release, List.of());
    }

    public Release get(UUID id) {
        return repository.findById(id).orElseThrow(ReleaseNotFoundException::new);
    }

    public List<ReleaseSummary> search(String query) {
        return repository.search(query == null ? "" : query.trim());
    }

    public Release freeze(UUID id, String actor) {
        // D-03-10: freeze is a one-way door, so freezing an already-frozen release is a
        // conflict, not a no-op - silently succeeding would hide a stale UI.
        get(id).requireOpen();
        return repository.freeze(id, actor, Instant.now());
    }

    public void delete(UUID id) {
        // D-03-11: an OPEN release has no history anyone relies on; a FROZEN one is an audit
        // record and deletion must be refused.
        get(id).requireOpen();
        repository.delete(id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
