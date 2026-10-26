package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.RoadmapInputHistoryEntity;
import com.evision.releasemgmt.adapters.persistence.entity.RoadmapItemEntity;
import com.evision.releasemgmt.adapters.persistence.repository.RoadmapInputHistoryJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.RoadmapItemJpaRepository;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemDetails;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemSource;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import com.evision.releasemgmt.ports.RoadmapRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Component
public class RoadmapRepositoryAdapter implements RoadmapRepository {

    private final RoadmapItemJpaRepository jpa;
    private final RoadmapInputHistoryJpaRepository historyJpa;

    public RoadmapRepositoryAdapter(RoadmapItemJpaRepository jpa, RoadmapInputHistoryJpaRepository historyJpa) {
        this.jpa = jpa;
        this.historyJpa = historyJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapItem> findAll() {
        return jpa.findAll().stream().map(RoadmapItemEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoadmapItem> findById(UUID itemId) {
        return jpa.findById(itemId).map(RoadmapItemEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, UUID> findIdsByJiraKey(Collection<String> jiraKeys) {
        Map<String, UUID> ids = new HashMap<>();
        if (jiraKeys.isEmpty()) {
            return ids;
        }
        jpa.findByJiraKeyIn(jiraKeys).stream().map(RoadmapItemEntity::toDomain)
                .forEach(item -> ids.put(item.jiraKey(), item.id()));
        return ids;
    }

    @Override
    @Transactional
    public RoadmapItem add(RoadmapItem item) {
        try {
            // saveAndFlush forces the unique indexes to fire here rather than at commit, where they would escape this catch.
            return jpa.saveAndFlush(RoadmapItemEntity.from(item)).toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_JIRA_KEY_DUPLICATE);
        }
    }

    @Override
    @Transactional
    public RoadmapItem setInputsUnderLock(UUID itemId, Function<RoadmapItem, RiceInputs> decide, String actor,
                                          Instant at) {
        RoadmapItemEntity entity = jpa.findByIdForUpdate(itemId).orElseThrow(RoadmapItemNotFoundException::new);
        RoadmapItem current = entity.toDomain();
        RiceInputs next = decide.apply(current);
        List<RiceField> changed = next.changedFields(current.inputs());
        if (changed.isEmpty()) {
            return current;
        }
        entity.applyInputs(next);
        jpa.save(entity);
        for (RiceField field : changed) {
            historyJpa.save(RoadmapInputHistoryEntity.from(new InputChange(
                    itemId, field, current.inputs().valueOf(field), next.valueOf(field), at, actor)));
        }
        return entity.toDomain();
    }

    @Override
    @Transactional
    public RoadmapItem updateDetails(UUID itemId, RoadmapItemDetails details) {
        requireExists(itemId);
        jpa.updateDetails(itemId, details.status(), details.productId(), details.targetQuarter(), details.owner(),
                details.notes());
        if (details.title() != null) {
            jpa.updateManualTitle(itemId, details.title(), RoadmapItemSource.MANUAL);
        }
        return jpa.findById(itemId).orElseThrow(RoadmapItemNotFoundException::new).toDomain();
    }

    @Override
    @Transactional
    public void setArchivedAt(UUID itemId, Instant archivedAt) {
        if (jpa.setArchivedAt(itemId, archivedAt) == 0) {
            throw new RoadmapItemNotFoundException();
        }
    }

    @Override
    @Transactional
    public void applyJiraSnapshot(UUID itemId, JiraSnapshot snapshot) {
        int updated = jpa.applyJiraSnapshot(itemId, snapshot.title(), snapshot.jiraKey(), snapshot.jiraStatus(),
                snapshot.jiraUpdatedAt(), snapshot.syncedAt());
        if (updated == 0) {
            throw new RoadmapItemNotFoundException();
        }
    }

    @Override
    @Transactional
    public void markJiraUnavailable(UUID itemId, Instant since) {
        // 0 rows is legitimate for an already-flagged item, so absence is checked separately.
        if (jpa.markJiraUnavailable(itemId, since) == 0) {
            requireExists(itemId);
        }
    }

    private void requireExists(UUID itemId) {
        if (!jpa.existsById(itemId)) {
            throw new RoadmapItemNotFoundException();
        }
    }
}
