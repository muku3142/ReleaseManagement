package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.adapters.persistence.converter.JsonStringListConverter;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "template")
public class TemplateEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "document_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TemplateDocumentType documentType;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "cover_enabled", nullable = false)
    private boolean coverEnabled;

    @Column(name = "cover_title", nullable = false)
    private String coverTitle;

    @Column(name = "cover_subtitle", nullable = false)
    private String coverSubtitle;

    @Column(name = "header_text", nullable = false)
    private String headerText;

    @Column(name = "footer_text", nullable = false)
    private String footerText;

    @Column(name = "page_numbers_enabled", nullable = false)
    private boolean pageNumbersEnabled;

    @Column(name = "warnings", nullable = false)
    @Convert(converter = JsonStringListConverter.class)
    private List<String> warnings;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    public TemplateEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TemplateDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(TemplateDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isCoverEnabled() {
        return coverEnabled;
    }

    public void setCoverEnabled(boolean coverEnabled) {
        this.coverEnabled = coverEnabled;
    }

    public String getCoverTitle() {
        return coverTitle;
    }

    public void setCoverTitle(String coverTitle) {
        this.coverTitle = coverTitle;
    }

    public String getCoverSubtitle() {
        return coverSubtitle;
    }

    public void setCoverSubtitle(String coverSubtitle) {
        this.coverSubtitle = coverSubtitle;
    }

    public String getHeaderText() {
        return headerText;
    }

    public void setHeaderText(String headerText) {
        this.headerText = headerText;
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    public boolean isPageNumbersEnabled() {
        return pageNumbersEnabled;
    }

    public void setPageNumbersEnabled(boolean pageNumbersEnabled) {
        this.pageNumbersEnabled = pageNumbersEnabled;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
