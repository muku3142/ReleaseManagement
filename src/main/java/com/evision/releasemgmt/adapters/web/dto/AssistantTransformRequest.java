package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssistantTransformRequest(
        @NotNull UUID releaseId,
        @NotNull TemplateDocumentType documentType,
        @Positive int sourceRevision,
        @NotNull TransformMode mode,
        // rag.transform.instruction-max-chars
        @Size(max = 2000) String instruction) {

    @Override
    public String toString() {
        return "AssistantTransformRequest[releaseId=" + releaseId + ", documentType=" + documentType + ", mode=" + mode
                + ", sourceRevision=" + sourceRevision + ", instructionChars="
                + (instruction == null ? 0 : instruction.length()) + "]";
    }
}
