package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.BriefingTaskSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingTaskSettingJpaRepository extends JpaRepository<BriefingTaskSettingEntity, String> {
}
