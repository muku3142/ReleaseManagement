package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.JiraCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraCredentialJpaRepository extends JpaRepository<JiraCredentialEntity, String> {
}
