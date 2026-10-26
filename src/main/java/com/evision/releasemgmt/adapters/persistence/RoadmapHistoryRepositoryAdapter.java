package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.RoadmapInputHistoryEntity;
import com.evision.releasemgmt.adapters.persistence.repository.RoadmapInputHistoryJpaRepository;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.ports.RoadmapHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class RoadmapHistoryRepositoryAdapter implements RoadmapHistoryRepository {

    private final RoadmapInputHistoryJpaRepository jpa;

    public RoadmapHistoryRepositoryAdapter(RoadmapInputHistoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InputChange> historyOf(UUID itemId) {
        return jpa.findByItemIdOrderByChangedAtDescIdDesc(itemId).stream()
                .map(RoadmapInputHistoryEntity::toDomain).toList();
    }
}
