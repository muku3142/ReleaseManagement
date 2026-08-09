package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.TemplateLogoEntity;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateLogoJpaRepository extends JpaRepository<TemplateLogoEntity, UUID> {

    // Never selects `bytes` - the whole point of this projection is that a template read (findById,
    // findAll) can learn logo metadata without pulling image bytes off disk (D-04-16, T-04-09).
    @Query("SELECT new com.evision.releasemgmt.domain.template.LogoMetadata(l.contentType, "
            + "l.filename, l.byteSize, l.width, l.height, l.uploadedAt) "
            + "FROM TemplateLogoEntity l WHERE l.templateId = :id")
    Optional<LogoMetadata> findMetadata(@Param("id") UUID id);

    @Query("SELECT l.templateId FROM TemplateLogoEntity l")
    List<UUID> findAllTemplateIdsWithLogo();
}
