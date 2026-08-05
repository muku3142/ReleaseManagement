package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseManualItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseManualItemJpaRepository extends JpaRepository<ReleaseManualItemEntity, UUID> {

    // D-03-04: deterministic order, title ascending.
    List<ReleaseManualItemEntity> findByReleaseIdOrderByTitleAsc(UUID releaseId);

    Optional<ReleaseManualItemEntity> findByReleaseIdAndId(UUID releaseId, UUID id);
}
