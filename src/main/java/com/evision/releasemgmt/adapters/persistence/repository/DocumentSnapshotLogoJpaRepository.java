package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.DocumentSnapshotLogoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentSnapshotLogoJpaRepository extends JpaRepository<DocumentSnapshotLogoEntity, UUID> {
}
