package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameTemplateRequest(@NotBlank @Size(max = 200) String name) {
}
