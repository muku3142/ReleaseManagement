package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.RoadmapInputHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoadmapInputHistoryJpaRepository extends JpaRepository<RoadmapInputHistoryEntity, UUID> {

    List<RoadmapInputHistoryEntity> findByItemIdOrderByChangedAtDescIdDesc(UUID itemId);
}
