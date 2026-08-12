package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ChromeFieldsRequest(boolean coverEnabled,
                                  @Size(max = 500) String coverTitle,
                                  @Size(max = 500) String coverSubtitle,
                                  @Size(max = 500) String headerText,
                                  @Size(max = 500) String footerText,
                                  boolean pageNumbersEnabled,
                                  // These bounds must stay identical to V6's
                                  // CHECK (logo_render_height_mm BETWEEN 5 AND 120) - the same
                                  // request-layer/DB pairing groupingDimension already uses. Integer
                                  // rather than int on purpose: an absent field on a primitive would
                                  // deserialise to 0 and be rejected, so a cached client build would
                                  // fail every save; @Min/@Max skip null, so an absent value falls
                                  // through to TemplateController's default while an explicit
                                  // out-of-range number is still rejected here.
                                  @Min(5) @Max(120) Integer logoRenderHeightMm) {
}
