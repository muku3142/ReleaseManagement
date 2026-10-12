package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.EnvironmentStageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnvironmentStageJpaRepository extends JpaRepository<EnvironmentStageEntity, UUID> {

    List<EnvironmentStageEntity> findAllByOrderByPositionAsc();
}
