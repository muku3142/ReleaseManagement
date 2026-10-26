package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_snapshot")
public class DocumentSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "document_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TemplateDocumentType documentType;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "markdown", nullable = false)
    private String markdown;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "template_body", nullable = false)
    private String templateBody;

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

    @Column(name = "logo_render_height_mm", nullable = false)
    private int logoRenderHeightMm;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "ai_mode")
    @Enumerated(EnumType.STRING)
    private AiMode aiMode;

    @Column(name = "ai_instruction")
    private String aiInstruction;

    @Column(name = "ai_source_revision")
    private Integer aiSourceRevision;

    @Column(name = "basis_generated_at")
    private Instant basisGeneratedAt;

    public DocumentSnapshotEntity() {
    }

    public AiMode getAiMode() {
        return aiMode;
    }

    public void setAiMode(AiMode aiMode) {
        this.aiMode = aiMode;
    }

    public String getAiInstruction() {
        return aiInstruction;
    }

    public void setAiInstruction(String aiInstruction) {
        this.aiInstruction = aiInstruction;
    }

    public Integer getAiSourceRevision() {
        return aiSourceRevision;
    }

    public void setAiSourceRevision(Integer aiSourceRevision) {
        this.aiSourceRevision = aiSourceRevision;
    }

    public Instant getBasisGeneratedAt() {
        return basisGeneratedAt;
    }

    public void setBasisGeneratedAt(Instant basisGeneratedAt) {
        this.basisGeneratedAt = basisGeneratedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(UUID releaseId) {
        this.releaseId = releaseId;
    }

    public TemplateDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(TemplateDocumentType documentType) {
        this.documentType = documentType;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateBody() {
        return templateBody;
    }

    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody;
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

    public int getLogoRenderHeightMm() {
        return logoRenderHeightMm;
    }

    public void setLogoRenderHeightMm(int logoRenderHeightMm) {
        this.logoRenderHeightMm = logoRenderHeightMm;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }
}
