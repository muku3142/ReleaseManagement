package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveAiRevisionRequest(
        @NotNull UUID releaseId,
        @NotNull TemplateDocumentType documentType,
        @Positive int sourceRevision,
        @NotNull TransformMode mode,
        // rag.transform.instruction-max-chars
        @Size(max = 2000) String instruction,
        @NotBlank @Size(max = SaveAiRevisionRequest.MARKDOWN_MAX_CHARS) String markdown) {

    // Storage sanity cap, not a model limit.
    public static final int MARKDOWN_MAX_CHARS = 200_000;

    @Override
    public String toString() {
        return "SaveAiRevisionRequest[releaseId=" + releaseId + ", documentType=" + documentType + ", mode=" + mode
                + ", sourceRevision=" + sourceRevision + ", markdownChars=" + markdown.length() + "]";
    }
}
