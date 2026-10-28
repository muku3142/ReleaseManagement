package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.BriefingSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingSettingsJpaRepository extends JpaRepository<BriefingSettingsEntity, Short> {
}
