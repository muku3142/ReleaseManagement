package com.evision.releasemgmt.adapters.render;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.ports.PdfExportPort;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

// DOC-05 / DOC-08: renders an already-resolved document snapshot (Markdown + chrome) to PDF.
// This adapter never re-enters RenderService/Mustache - the snapshot is immutable (DOC-07), and
// re-resolving would resolve chrome/body against today's release data instead of the data that
// was true when the document was generated.
@Component
public class PdfExportAdapter implements PdfExportPort {

    private final MustacheFlexmarkRenderAdapter renderAdapter;

    public PdfExportAdapter(MustacheFlexmarkRenderAdapter renderAdapter) {
        this.renderAdapter = renderAdapter;
    }

    @Override
    public byte[] toPdf(String resolvedMarkdown, ChromeFields resolvedChrome, LogoImage logo) {
        // D-04-04: the SAME shared flexmark AST and the SAME HtmlRenderer the authoring preview and
        // save-time validation use. Building a second Parser/HtmlRenderer here would reintroduce
        // exactly the divergence plan 05-01 closed.
        String bodyHtml = renderAdapter.renderHtml(renderAdapter.parseMarkdown(resolvedMarkdown));
        String xhtml = buildXhtml(bodyHtml, resolvedChrome, logo);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // T-05-26: a fresh builder every call - PdfRendererBuilder accumulates per-document
            // state and is not safe to share across concurrent export requests.
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // T-05-23: base URI is null, so a relative src/href in chrome/body text cannot resolve
            // against the local filesystem or network - this is a security control, not an omission.
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private String buildXhtml(String bodyHtml, ChromeFields chrome, LogoImage logo) {
        StringBuilder xhtml = new StringBuilder();
        xhtml.append("<html><head><meta charset=\"UTF-8\"/><style>").append(buildCss(chrome))
                .append("</style></head><body>");
        if (chrome.coverEnabled()) {
            xhtml.append(buildCover(chrome, logo));
        }
        String headerText = chrome.headerText();
        if (headerText != null && !headerText.isBlank()) {
            // Must exist in the flow exactly once for CSS position: running() to pick it up.
            xhtml.append("<div id=\"running-header\">").append(escapeHtml(headerText)).append("</div>");
        }
        xhtml.append("<div class=\"body\">").append(bodyHtml).append("</div>");
        xhtml.append("</body></html>");
        return xhtml.toString();
    }

    private String buildCover(ChromeFields chrome, LogoImage logo) {
        StringBuilder cover = new StringBuilder("<div class=\"cover\">");
        if (logo != null) {
            // Content type comes from stored metadata (content-sniffed at upload, TMPL-09) -
            // never re-derived from the filename (T-05-24).
            String dataUri = "data:" + logo.metadata().contentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(logo.bytes());
            cover.append("<img src=\"").append(dataUri).append("\" alt=\"\"/>");
        }
        cover.append("<h1>").append(escapeHtml(chrome.coverTitle())).append("</h1>");
        cover.append("<p>").append(escapeHtml(chrome.coverSubtitle())).append("</p>");
        cover.append("</div>");
        return cover.toString();
    }

    private String buildCss(ChromeFields chrome) {
        StringBuilder css = new StringBuilder();
        css.append("@page { size: A4; margin: 2.5cm 2cm;");
        css.append(" @top-center { content: element(runningHeader); }");
        css.append(" @bottom-center { content: ").append(footerContent(chrome)).append("; } }");
        if (chrome.coverEnabled()) {
            // Suppress chrome on the cover page only - if there is no cover, page 1 IS body content
            // and must keep its header/footer.
            css.append(" @page :first { @top-center { content: none; } @bottom-center { content: none; } }");
        }
        css.append(" body { font-family: sans-serif; font-size: 11pt; line-height: 1.45; }");
        css.append(" #running-header { position: running(runningHeader); font-size: 9pt; color: #444; }");
        css.append(" .cover { page-break-after: always; text-align: center; padding-top: 6cm; }");
        css.append(" .cover img { max-height: 3cm; }");
        css.append(" .cover h1 { font-size: 24pt; margin: 0.6cm 0 0 0; }");
        css.append(" .cover p { font-size: 13pt; color: #555; }");
        css.append(" h1, h2, h3 { page-break-after: avoid; }");
        css.append(" table { border-collapse: collapse; width: 100%; }");
        css.append(" th, td { border: 1px solid #999; padding: 4pt; text-align: left; vertical-align: top; }");
        css.append(" th { background: #f0f0f0; }");
        css.append(" pre { background: #f4f4f4; padding: 6pt; white-space: pre-wrap; }");
        css.append(" code { font-family: monospace; }");
        css.append(" img { max-width: 100%; }");
        return css.toString();
    }

    // Footer CSS content, built in Java rather than templated by the caller:
    // - footer text set, page numbers on  -> "<escaped footer> \2022 Page " counter(page) " of " counter(pages)
    // - footer text set, page numbers off -> "<escaped footer>"
    // - footer text empty, page numbers on -> "Page " counter(page) " of " counter(pages)
    // - both empty -> none
    private String footerContent(ChromeFields chrome) {
        boolean hasFooterText = chrome.footerText() != null && !chrome.footerText().isBlank();
        boolean pageNumbers = chrome.pageNumbersEnabled();
        if (!hasFooterText && !pageNumbers) {
            return "none";
        }
        if (hasFooterText && pageNumbers) {
            return "\"" + escapeCss(chrome.footerText()) + " \\2022 Page \" counter(page) \" of \" counter(pages)";
        }
        if (hasFooterText) {
            return "\"" + escapeCss(chrome.footerText()) + "\"";
        }
        return "\"Page \" counter(page) \" of \" counter(pages)";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // T-05-22: chrome text placed inside a CSS string literal (the footer) is escaped for CSS, not
    // HTML - backslash and the quote that would otherwise close the string, plus CR/LF/}/< stripped
    // so the remainder of the footer cannot become CSS declarations.
    private String escapeCss(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "")
                .replace("}", "")
                .replace("<", "");
    }
}
