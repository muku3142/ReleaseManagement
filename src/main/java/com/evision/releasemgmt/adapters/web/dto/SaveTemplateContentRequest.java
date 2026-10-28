package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 100_000 characters is the boundary bound on template size; TemplateScanner's MAX_TAGS bounds tag
// count and RenderService bounds execution - three cheap caps, none of which duplicates another.
// groupingDimension is optional (null/blank means an older client's payload, which the controller
// defaults to ISSUE_TYPE) - the pattern allows null/empty in addition to the valid enum names,
// so anything else fails VALIDATION_FAILED here rather than a raw Enum.valueOf 500 in the service.
public record SaveTemplateContentRequest(@NotNull @Size(max = 100000) String body,
                                         @NotNull @Valid ChromeFieldsRequest chrome,
                                         @Pattern(regexp = "^(ISSUE_TYPE|COMPONENT|BUNDLE)?$") String groupingDimension) {
}
