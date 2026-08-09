package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.TemplateEntity;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateJpaRepository extends JpaRepository<TemplateEntity, UUID> {

    List<TemplateEntity> findAllByOrderByDocumentTypeAscNameAsc();

    List<TemplateEntity> findByDocumentTypeOrderByNameAsc(TemplateDocumentType documentType);

    Optional<TemplateEntity> findByDocumentTypeAndActiveTrue(TemplateDocumentType documentType);
}
