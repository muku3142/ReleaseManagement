package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.PromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PromotionJpaRepository extends JpaRepository<PromotionEntity, UUID> {

    List<PromotionEntity> findByReleaseIdOrderByRecordedAtAsc(UUID releaseId);

    @Query("SELECT DISTINCT p.stageId FROM PromotionEntity p")
    Set<UUID> findReferencedStageIds();
}
