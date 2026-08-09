package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.Size;

public record ChromeFieldsRequest(boolean coverEnabled,
                                  @Size(max = 500) String coverTitle,
                                  @Size(max = 500) String coverSubtitle,
                                  @Size(max = 500) String headerText,
                                  @Size(max = 500) String footerText,
                                  boolean pageNumbersEnabled) {
}
