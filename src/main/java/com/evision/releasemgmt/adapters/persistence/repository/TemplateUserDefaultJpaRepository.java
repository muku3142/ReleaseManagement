package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.TemplateUserDefaultEntity;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateUserDefaultJpaRepository
        extends JpaRepository<TemplateUserDefaultEntity, TemplateUserDefaultEntity.Key> {

    List<TemplateUserDefaultEntity> findByAccountId(String accountId);

    Optional<TemplateUserDefaultEntity> findByAccountIdAndDocumentType(String accountId,
                                                                      TemplateDocumentType documentType);

    // D-05.4-04: the row's document_type is read from the template row inside the same statement, so it can
    // never disagree with the template. ON CONFLICT makes a double-click on "Set as my default" safe.
    @Modifying
    @Query(value = """
            INSERT INTO template_user_default (account_id, document_type, template_id)
            SELECT :accountId, t.document_type, t.id FROM template t WHERE t.id = :templateId
            ON CONFLICT (account_id, document_type) DO UPDATE SET template_id = EXCLUDED.template_id
            """, nativeQuery = true)
    int upsertFromTemplate(@Param("accountId") String accountId, @Param("templateId") UUID templateId);

    @Modifying
    @Query("DELETE FROM TemplateUserDefaultEntity d WHERE d.accountId = :accountId AND d.templateId = :templateId")
    int deleteByAccountIdAndTemplateId(@Param("accountId") String accountId, @Param("templateId") UUID templateId);
}
