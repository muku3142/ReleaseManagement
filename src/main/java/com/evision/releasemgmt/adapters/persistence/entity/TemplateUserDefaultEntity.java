package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "template_user_default")
@IdClass(TemplateUserDefaultEntity.Key.class)
public class TemplateUserDefaultEntity {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Id
    @Column(name = "document_type")
    @Enumerated(EnumType.STRING)
    private TemplateDocumentType documentType;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    public TemplateUserDefaultEntity() {
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public TemplateDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(TemplateDocumentType documentType) {
        this.documentType = documentType;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public static class Key implements Serializable {

        private String accountId;
        private TemplateDocumentType documentType;

        public Key() {
        }

        public Key(String accountId, TemplateDocumentType documentType) {
            this.accountId = accountId;
            this.documentType = documentType;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(accountId, other.accountId)
                    && Objects.equals(documentType, other.documentType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, documentType);
        }
    }
}
