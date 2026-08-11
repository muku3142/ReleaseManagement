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
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.view.SampleReleaseView;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import jakarta.xml.bind.JAXBElement;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private DocumentSnapshot generate(String body, ChromeFields chrome) {
        Release release = openRelease();
        Template template = activeTemplate(body, chrome);

        when(releaseRepository.findById(release.id())).thenReturn(Optional.of(release));
        when(templateRepository.findActive(template.documentType())).thenReturn(Optional.of(template));
        when(assembler.assemble(any(), any())).thenReturn(SampleReleaseView.instance());
        when(templateRepository.findLogo(template.id())).thenReturn(Optional.empty());
        when(snapshotRepository.nextRevision(release.id(), template.documentType())).thenReturn(1);
        // generate() hands the caller back exactly what create() returns, so the export half of
        // this test consumes the very snapshot generation produced - no re-fetch, no fixture.
        when(snapshotRepository.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepository.findLogo(any())).thenReturn(Optional.empty());

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
}
