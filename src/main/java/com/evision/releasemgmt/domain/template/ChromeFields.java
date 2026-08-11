package com.evision.releasemgmt.domain.template;

// TMPL-08 / D-04-15: structured fields, not a second free-form template. Each string accepts the
// same {{ }} placeholders as the body and resolves against the same view-model, so no new
// expression surface exists. Strings are never null - "" means "not set".
//
// D-05.1-02: logoRenderHeightMm is the cover logo's MAXIMUM rendered height in millimetres, honoured
// identically by PdfExportAdapter, DocxExportAdapter and the browser's RenderedDocument. It is a
// maximum and never a target (D-05.1-06): a logo shorter than it keeps its natural size and is never
// scaled up. It is NOT an upload limit - LogoValidator's 2000x2000/2MB caps are a separate,
// storage-side decompression-bomb control (TMPL-09).
public record ChromeFields(boolean coverEnabled, String coverTitle, String coverSubtitle,
                           String headerText, String footerText, boolean pageNumbersEnabled,
                           int logoRenderHeightMm) {

    // = the PDF's previous hard-coded `max-height: 3cm`, and V6's column default on both tables, so a
    // brand-new template starts exactly where every migrated row now sits. Deliberately not 0:
    // empty() feeds Template.create, and 0 would both violate V6's CHECK on the first insert and mean
    // an invisible logo the first time coverEnabled is switched on.
    public static final int DEFAULT_LOGO_RENDER_HEIGHT_MM = 30;

    public static ChromeFields empty() {
        return new ChromeFields(false, "", "", "", "", false, DEFAULT_LOGO_RENDER_HEIGHT_MM);
    }
}
