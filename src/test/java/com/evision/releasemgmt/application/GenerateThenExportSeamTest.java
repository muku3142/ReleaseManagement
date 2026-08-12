package com.evision.releasemgmt.application;

// The seam plan 05-04/05-05/05-06 never tested. Each of those plans was internally consistent:
// generation was tested against its own expectation, and both exporters were tested against
// hand-written Markdown fixtures. Nothing fed an exporter what generation actually stores, so a
// 552-test suite stayed green while every exported PDF and DOCX rendered its whole body as literal
// escaped HTML source (05-VERIFICATION.md, gap 2). Nothing in this file may hand-write the content
// under test: every assertion below is made on bytes descended from a real generate() call.

import com.evision.releasemgmt.adapters.render.DocxExportAdapter;
import com.evision.releasemgmt.adapters.render.MustacheFlexmarkRenderAdapter;
import com.evision.releasemgmt.adapters.render.PdfExportAdapter;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.view.SampleReleaseView;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import jakarta.xml.bind.JAXBElement;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerateThenExportSeamTest {

    private static final String BODY = """
            # Release {{release.name}}

            **Highlights** for version {{release.version}}.

            - First item
            - Second item

            | Key | Summary |
            |-----|---------|
            {{#items}}| {{key}} | {{title}} |
            {{/items}}
            """;

    private final ReleaseRepository releaseRepository = mock(ReleaseRepository.class);
    private final TemplateRepository templateRepository = mock(TemplateRepository.class);
    private final DocumentSnapshotRepository snapshotRepository = mock(DocumentSnapshotRepository.class);
    private final ReleaseViewAssembler assembler = mock(ReleaseViewAssembler.class);

    private final RenderService renderService =
            new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(5), 500, 64);
    private final MustacheFlexmarkRenderAdapter renderAdapter = new MustacheFlexmarkRenderAdapter();
    private final DocumentGenerationService generationService = new DocumentGenerationService(
            releaseRepository, templateRepository, snapshotRepository, assembler, renderService);
    private final DocumentExportService exportService = new DocumentExportService(
            snapshotRepository, new PdfExportAdapter(renderAdapter), new DocxExportAdapter(renderAdapter));

    @Test
    void generatedSnapshotBodyIsMarkdownNotHtml() {
        DocumentSnapshot snapshot = generate(BODY, ChromeFields.empty());

        assertThat(snapshot.markdown()).contains("# Release Sample Release");
        assertThat(snapshot.markdown()).doesNotContain("<h1");
        assertThat(snapshot.markdown()).doesNotContain("<table");
    }

    @Test
    void exportedPdfShowsRenderedContentNotEscapedHtmlSource() throws Exception {
        DocumentSnapshot snapshot = generate(BODY, ChromeFields.empty());

        byte[] pdf = exportService.toPdf(snapshot);
        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }

        assertThat(text).contains("Release Sample Release");
        assertThat(text).contains("CNV-2896");
        assertThat(text).doesNotContain("<h1");
        assertThat(text).doesNotContain("&lt;");
        assertThat(text).doesNotContain("|");
    }

    @Test
    void exportedDocxShowsRenderedContentNotEscapedHtmlSource() throws Exception {
        DocumentSnapshot snapshot = generate(BODY, ChromeFields.empty());

        byte[] docx = exportService.toDocx(snapshot);
        MainDocumentPart mainPart = load(docx).getMainDocumentPart();
        List<Object> flattened = flatten(mainPart);
        String allText = flattened.stream().filter(o -> o instanceof Text)
                .map(o -> ((Text) o).getValue())
                .reduce("", String::concat);

        assertThat(allText).contains("Release Sample Release");
        assertThat(allText).contains("CNV-2896");
        assertThat(allText).doesNotContain("<h1");
        assertThat(allText).doesNotContain("&lt;");
        assertThat(flattened).anyMatch(o -> o instanceof Tbl);
    }

    @Test
    void chromeSurvivesTheSeamAsPlainText() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "Version {{release.version}}", "", "", "", false,
                ChromeFields.DEFAULT_LOGO_RENDER_HEIGHT_MM);
        DocumentSnapshot snapshot = generate("Body.", chrome);

        byte[] pdf = exportService.toPdf(snapshot);
        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }

        assertThat(text).contains("Version 2026.08");
        assertThat(text).doesNotContain("<p>");
    }

    // D-05.1-07: D-05-07 declined automated cross-format PARITY in general and that still stands - a
    // human still eyeballs overall visual equivalence. But "the logo is no taller than N mm in both
    // formats" is mechanically checkable, and its absence is precisely what let a 4.5x disagreement
    // between the PDF and the DOCX ship through a green 552-test suite and a passing phase
    // verification. Unit tests on PdfExportAdapter and DocxExportAdapter separately cannot catch this
    // class of break; only one generated snapshot exported both ways can.
    //
    // 15mm, not the 30mm default, on purpose: at 30mm the pre-fix PDF's hard-coded `max-height: 3cm`
    // coincidentally produces the correct answer, so a default-height assertion would pass against
    // broken code. At 15mm both pre-fix renderers are wrong, which also proves the per-template value
    // is genuinely threaded template -> snapshot -> both exporters.
    @Test
    void anOversizedLogoIsClampedToTheTemplatesHeightInBothExportFormats() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "Cover", "", "", "", false, 15);
        DocumentSnapshot snapshot = generate("Body.", chrome, logo(600, 300));

        Inline inline = firstInline(exportService.toDocx(snapshot));
        assertThat(inline.getExtent().getCy()).isEqualTo(540_000L);   // 15mm x 36000 EMU/mm
        assertThat(inline.getExtent().getCx()).isEqualTo(1_080_000L); // 2:1 preserved, not letterboxed

        float[] pdfSizePt = firstImageSizePt(exportService.toPdf(snapshot));
        assertThat(pdfSizePt[1]).isCloseTo(42.52f, within(1.5f)); // 15mm in points
        assertThat(pdfSizePt[0]).isCloseTo(85.04f, within(3.0f)); // 2:1 preserved
    }

    @Test
    void aLogoShorterThanTheConfiguredHeightIsNeverScaledUp() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "Cover", "", "", "", false, 15);
        DocumentSnapshot snapshot = generate("Body.", chrome, logo(20, 10));

        // D-05.1-06: the setting is a MAXIMUM. Untouched native EMUs, not the 540000 cap.
        Inline inline = firstInline(exportService.toDocx(snapshot));
        assertThat(inline.getExtent().getCy()).isEqualTo(95_250L);  // 10px x 9525
        assertThat(inline.getExtent().getCx()).isEqualTo(190_500L); // 20px x 9525

        // Deliberately a range, not an exact value: the natural size depends on openhtmltopdf's
        // px-to-pt conversion, which is not what this asserts. What it asserts is that the logo was
        // NOT stretched up to the 42.52pt cap.
        float[] pdfSizePt = firstImageSizePt(exportService.toPdf(snapshot));
        assertThat(pdfSizePt[1]).isGreaterThan(0f);
        assertThat(pdfSizePt[1]).isLessThan(20f);
    }

    @Test
    void theTemplatesLogoHeightIsCopiedOntoTheSnapshotSoAReExportReproducesIt() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "Cover", "", "", "", false, 15);
        DocumentSnapshot snapshot = generate("Body.", chrome, logo(600, 300));

        // D-05.1-03: the exporters read this, never the live template - which is what makes
        // re-exporting an old revision reproduce the size it was generated with.
        assertThat(snapshot.chrome().logoRenderHeightMm()).isEqualTo(15);
    }

    private DocumentSnapshot generate(String body, ChromeFields chrome) {
        return generate(body, chrome, null);
    }

    private DocumentSnapshot generate(String body, ChromeFields chrome, LogoImage logo) {
        Release release = openRelease();
        Template template = activeTemplate(body, chrome);

        when(releaseRepository.findById(release.id())).thenReturn(Optional.of(release));
        when(templateRepository.findActive(template.documentType())).thenReturn(Optional.of(template));
        when(assembler.assemble(any(), any())).thenReturn(SampleReleaseView.instance());
        when(templateRepository.findLogo(template.id())).thenReturn(Optional.ofNullable(logo));
        when(snapshotRepository.nextRevision(release.id(), template.documentType())).thenReturn(1);
        // generate() hands the caller back exactly what create() returns, so the export half of
        // this test consumes the very snapshot generation produced - no re-fetch, no fixture.
        when(snapshotRepository.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        // The export half reads the logo back through the snapshot port, exactly as
        // DocumentExportService does in production - the same bytes generation was handed.
        when(snapshotRepository.findLogo(any())).thenReturn(Optional.ofNullable(logo));

        return generationService.generate(release.id(), template.documentType(), "actor-1");
    }

    private static Release openRelease() {
        Instant now = Instant.now();
        return new Release(UUID.randomUUID(), "Sample Release", "2026.08", ReleaseStatus.OPEN, "CNV",
                now, "actor-0", null, null, now, null, List.of(), List.of());
    }

    private static Template activeTemplate(String body, ChromeFields chrome) {
        Instant now = Instant.now();
        return new Template(UUID.randomUUID(), "Standard notes", TemplateDocumentType.RELEASE_NOTES, body,
                true, chrome, GroupingDimension.ISSUE_TYPE, null, List.of(), now, "actor-0", now, "actor-0");
    }

    private static WordprocessingMLPackage load(byte[] bytes) throws Exception {
        return (WordprocessingMLPackage) WordprocessingMLPackage.load(new ByteArrayInputStream(bytes));
    }

    private static List<Object> flatten(Object root) {
        List<Object> all = new ArrayList<>();
        flattenInto(root, all);
        return all;
    }

    private static void flattenInto(Object node, List<Object> all) {
        Object o = unwrap(node);
        if (o == null) {
            return;
        }
        all.add(o);
        if (o instanceof ContentAccessor ca) {
            for (Object child : ca.getContent()) {
                flattenInto(child, all);
            }
        }
    }

    private static Object unwrap(Object o) {
        return o instanceof JAXBElement<?> el ? el.getValue() : o;
    }

    private static LogoImage logo(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] bytes = out.toByteArray();
        return new LogoImage(bytes, new LogoMetadata("image/png", "logo.png", bytes.length, width,
                height, Instant.now()));
    }

    // Reads the extent back off REOPENED bytes, matching DocxExportAdapterTest's stated convention:
    // walk the OOXML tree, never trust the writer's own intent. Note the flatten() walker above stops
    // at Drawing - Drawing exposes getAnchorOrInline(), not getContent(), so it is not a
    // ContentAccessor and the walker cannot descend into it. Hence the explicit last hop.
    private static Inline firstInline(byte[] docx) throws Exception {
        Drawing drawing = flatten(load(docx).getMainDocumentPart()).stream()
                .filter(o -> o instanceof Drawing).map(o -> (Drawing) o).findFirst()
                .orElseThrow(() -> new AssertionError("no Drawing in the exported DOCX"));
        return (Inline) unwrap(drawing.getAnchorOrInline().get(0));
    }

    // {renderedWidthPt, renderedHeightPt} of the first image drawn on page 1. PDF maps an image XObject
    // onto the unit square, so the CTM in force at that image's `Do` operator IS its rendered box in
    // points - which is the only signal that reflects what openhtmltopdf's CSS actually computed.
    // PDFBox 2.0.24 has no higher-level API for this, and PdfExportAdapterTest's countImageXObjects
    // proves only that an image is present.
    private static float[] firstImageSizePt(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf)) {
            ImageSizeCollector collector = new ImageSizeCollector();
            collector.processPage(document.getPage(0));
            assertThat(collector.sizes).as("no image drawn on the cover page").isNotEmpty();
            return collector.sizes.get(0);
        }
    }

    private static class ImageSizeCollector extends PDFStreamEngine {

        private final List<float[]> sizes = new ArrayList<>();

        // PDFStreamEngine registers NO operators by default (unlike PDFTextStripper, which wires up
        // dozens in its own constructor) - without these three, "cm"/"q"/"Q" are silently no-ops via
        // the unsupportedOperator() fallthrough, the graphics state's CTM never leaves identity, and
        // getCurrentTransformationMatrix() always reports 1.0 regardless of what the page actually
        // draws. These three are the minimum needed for the CTM this test reads to be real.
        ImageSizeCollector() {
            addOperator(new org.apache.pdfbox.contentstream.operator.state.Concatenate());
            addOperator(new org.apache.pdfbox.contentstream.operator.state.Save());
            addOperator(new org.apache.pdfbox.contentstream.operator.state.Restore());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && operands.get(0) instanceof COSName name
                    && getResources().getXObject(name) instanceof PDImageXObject) {
                Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                sizes.add(new float[]{ctm.getScalingFactorX(), ctm.getScalingFactorY()});
                return;
            }
            // Form XObjects and every other operator must still reach the base engine, or nested
            // content streams (and the graphics state they set) are never processed.
            super.processOperator(operator, operands);
        }
    }
}
