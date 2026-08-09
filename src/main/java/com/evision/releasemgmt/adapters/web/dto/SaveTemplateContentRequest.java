package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 100_000 characters is the boundary bound on template size; TemplateScanner's MAX_TAGS bounds tag
// count and RenderService bounds execution - three cheap caps, none of which duplicates another.
public record SaveTemplateContentRequest(@NotNull @Size(max = 100000) String body,
                                         @NotNull @Valid ChromeFieldsRequest chrome) {
}
