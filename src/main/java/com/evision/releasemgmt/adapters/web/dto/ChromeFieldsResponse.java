package com.evision.releasemgmt.adapters.web.dto;

public record ChromeFieldsResponse(boolean coverEnabled, String coverTitle, String coverSubtitle,
                                   String headerText, String footerText, boolean pageNumbersEnabled) {
}
