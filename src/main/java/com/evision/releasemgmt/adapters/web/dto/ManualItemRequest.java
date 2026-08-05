package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.release.ManualItemCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualItemRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull ManualItemCategory category) {
}
