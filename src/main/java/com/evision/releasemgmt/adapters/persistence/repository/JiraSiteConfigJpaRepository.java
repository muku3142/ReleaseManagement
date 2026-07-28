package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.JiraSiteConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraSiteConfigJpaRepository extends JpaRepository<JiraSiteConfigEntity, Short> {
}
