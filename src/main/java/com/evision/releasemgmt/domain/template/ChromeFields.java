package com.evision.releasemgmt.domain.template;

// TMPL-08 / D-04-15: structured fields, not a second free-form template. Each string accepts the
// same {{ }} placeholders as the body and resolves against the same view-model, so no new
// expression surface exists. Strings are never null - "" means "not set".
public record ChromeFields(boolean coverEnabled, String coverTitle, String coverSubtitle,
                           String headerText, String footerText, boolean pageNumbersEnabled) {

    public static ChromeFields empty() {
        return new ChromeFields(false, "", "", "", "", false);
    }
}
