package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull TemplateDocumentType documentType) {
}
