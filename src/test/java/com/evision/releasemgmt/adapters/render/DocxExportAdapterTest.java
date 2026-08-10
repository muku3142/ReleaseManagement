package com.evision.releasemgmt.adapters.render;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.CTSimpleField;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.FooterReference;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.HeaderReference;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Every test reopens the produced bytes with docx4j and asserts on the object model - asserting
// only that bytes came back would pass with an empty document (plan 05-06, Task 3).
class DocxExportAdapterTest {

    private final DocxExportAdapter adapter = new DocxExportAdapter(new MustacheFlexmarkRenderAdapter());

    @Test
    void producesAValidDocxPackage() throws Exception {
        byte[] bytes = adapter.toDocx("Hello world", ChromeFields.empty(), null);

        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
        assertThat(load(bytes)).isNotNull();
    }

    @Test
    void headingsUseWordHeadingStyles() throws Exception {
        byte[] bytes = adapter.toDocx("## Section Title", ChromeFields.empty(), null);

        List<P> paragraphs = paragraphsOf(mainPart(bytes));
        P heading = paragraphs.stream()
                .filter(p -> p.getPPr() != null && p.getPPr().getPStyle() != null)
                .findFirst().orElseThrow();
        assertThat(heading.getPPr().getPStyle().getVal()).isEqualTo("Heading2");
        assertThat(textOf(heading)).isEqualTo("Section Title");
    }

    @Test
    void boldAndItalicBecomeRunProperties() throws Exception {
        byte[] bytes = adapter.toDocx("**bold** *italic*", ChromeFields.empty(), null);

        List<R> runs = runsOf(mainPart(bytes));
        assertThat(runs).anyMatch(r -> r.getRPr() != null && r.getRPr().getB() != null);
        assertThat(runs).anyMatch(r -> r.getRPr() != null && r.getRPr().getI() != null);
    }

    @Test
    void inlineAndFencedCodeUseAMonospaceFont() throws Exception {
        String markdown = "`inline` text\n\n```\nfenced line\n```";
        byte[] bytes = adapter.toDocx(markdown, ChromeFields.empty(), null);

        List<R> monospaceRuns = runsOf(mainPart(bytes)).stream()
                .filter(r -> r.getRPr() != null && r.getRPr().getRFonts() != null)
                .toList();
        assertThat(monospaceRuns).hasSizeGreaterThanOrEqualTo(2);
        assertThat(monospaceRuns).allMatch(r -> "Consolas".equals(r.getRPr().getRFonts().getAscii()));
    }

    @Test
    void bulletAndOrderedListsCarryNumPr() throws Exception {
        String markdown = "- alpha\n\n1. beta";
        byte[] bytes = adapter.toDocx(markdown, ChromeFields.empty(), null);

        List<Integer> numIds = paragraphsOf(mainPart(bytes)).stream()
                .filter(p -> p.getPPr() != null && p.getPPr().getNumPr() != null)
                .map(p -> p.getPPr().getNumPr().getNumId().getVal().intValue())
                .toList();
        assertThat(numIds).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void markdownTableBecomesARealTbl() throws Exception {
        String markdown = """
                | A | B |
                |---|---|
                | 1 | 2 |
                """;
        byte[] bytes = adapter.toDocx(markdown, ChromeFields.empty(), null);

        org.docx4j.wml.Tbl tbl = flatten(mainPart(bytes)).stream()
                .filter(o -> o instanceof org.docx4j.wml.Tbl)
                .map(o -> (org.docx4j.wml.Tbl) o)
                .findFirst().orElseThrow();
        assertThat(tbl.getContent()).hasSize(2);
        org.docx4j.wml.Tr headerRow = (org.docx4j.wml.Tr) unwrap(tbl.getContent().get(0));
        org.docx4j.wml.Tr bodyRow = (org.docx4j.wml.Tr) unwrap(tbl.getContent().get(1));
        assertThat(headerRow.getContent()).hasSize(2);
        assertThat(textOfCell((org.docx4j.wml.Tc) unwrap(headerRow.getContent().get(0)))).isEqualTo("A");
        assertThat(textOfCell((org.docx4j.wml.Tc) unwrap(headerRow.getContent().get(1)))).isEqualTo("B");
        assertThat(textOfCell((org.docx4j.wml.Tc) unwrap(bodyRow.getContent().get(0)))).isEqualTo("1");
        assertThat(textOfCell((org.docx4j.wml.Tc) unwrap(bodyRow.getContent().get(1)))).isEqualTo("2");
    }

    @Test
    void unknownNodeDegradesToLiteralText() throws Exception {
        Parser parser = Parser.builder().build();
        Document ast = parser.parse("");
        Node unknown = new Node(BasedSequence.of("[[mystery-construct]]")) {
            @Override
            public BasedSequence[] getSegments() {
                return Node.EMPTY_SEGMENTS;
            }
        };
        ast.appendChild(unknown);

        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        new DocxAstWalker().appendBody(ast, pkg.getMainDocumentPart());
        byte[] bytes = save(pkg);

        String allText = paragraphsOf(mainPart(bytes)).stream().map(this::textOf)
                .reduce("", String::concat);
        assertThat(allText).contains("[[mystery-construct]]");
    }

    @Test
    void rawHtmlIsLiteralTextNotMarkup() throws Exception {
        byte[] bytes = adapter.toDocx("<b>x</b>", ChromeFields.empty(), null);

        String allText = paragraphsOf(mainPart(bytes)).stream().map(this::textOf)
                .reduce("", String::concat);
        assertThat(allText).contains("<b>x</b>");
        assertThat(runsOf(mainPart(bytes))).noneMatch(r -> r.getRPr() != null && r.getRPr().getB() != null);
    }

    @Test
    void coverPageIsPresentAndTitlePgIsSet() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "Release Notes", "Version 1.0", "", "", false);
        byte[] bytes = adapter.toDocx("Body text", chrome, null);

        MainDocumentPart mainPart = mainPart(bytes);
        String allText = paragraphsOf(mainPart).stream().map(this::textOf).reduce("", String::concat);
        assertThat(allText).contains("Release Notes").contains("Version 1.0");
        assertThat(sectPr(mainPart).getTitlePg()).isNotNull();
    }

    @Test
    void headerAndFooterPartsExistWithFirstAndDefaultRefs() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "Title", "Subtitle", "Running Header", "Footer",
                false);
        byte[] bytes = adapter.toDocx("Body", chrome, null);

        MainDocumentPart mainPart = mainPart(bytes);
        SectPr sectPr = sectPr(mainPart);
        List<HeaderReference> headerRefs = sectPr.getEGHdrFtrReferences().stream()
                .filter(r -> r instanceof HeaderReference)
                .map(r -> (HeaderReference) r)
                .toList();
        assertThat(headerRefs).extracting(HeaderReference::getType)
                .containsExactlyInAnyOrder(HdrFtrRef.FIRST, HdrFtrRef.DEFAULT);

        HeaderReference defaultRef = headerRefs.stream()
                .filter(r -> r.getType() == HdrFtrRef.DEFAULT).findFirst().orElseThrow();
        HeaderReference firstRef = headerRefs.stream()
                .filter(r -> r.getType() == HdrFtrRef.FIRST).findFirst().orElseThrow();

        Object defaultHeaderPart = resolvePart(mainPart, defaultRef.getId());
        Object firstHeaderPart = resolvePart(mainPart, firstRef.getId());
        String defaultText = flatten(defaultHeaderPart).stream()
                .filter(o -> o instanceof Text).map(o -> ((Text) o).getValue())
                .reduce("", String::concat);
        String firstText = flatten(firstHeaderPart).stream()
                .filter(o -> o instanceof Text).map(o -> ((Text) o).getValue())
                .reduce("", String::concat);
        assertThat(defaultText).contains("Running Header");
        assertThat(firstText).isEmpty();
    }

    @Test
    void footerContainsPageAndNumpagesFieldCodes() throws Exception {
        ChromeFields chrome = new ChromeFields(false, "", "", "", "Orchstro", true);
        byte[] bytes = adapter.toDocx("Body", chrome, null);

        MainDocumentPart mainPart = mainPart(bytes);
        SectPr sectPr = sectPr(mainPart);
        FooterReference defaultRef = sectPr.getEGHdrFtrReferences().stream()
                .filter(r -> r instanceof FooterReference)
                .map(r -> (FooterReference) r)
                .filter(r -> r.getType() == HdrFtrRef.DEFAULT)
                .findFirst().orElseThrow();
        Object footerPart = resolvePart(mainPart, defaultRef.getId());

        List<String> instructions = flatten(footerPart).stream()
                .filter(o -> o instanceof CTSimpleField)
                .map(o -> ((CTSimpleField) o).getInstr())
                .toList();
        assertThat(instructions).anyMatch(s -> s.contains("PAGE"));
        assertThat(instructions).anyMatch(s -> s.contains("NUMPAGES"));
    }

    @Test
    void pageNumberFieldsAbsentWhenDisabled() throws Exception {
        ChromeFields chrome = new ChromeFields(false, "", "", "", "Orchstro", false);
        byte[] bytes = adapter.toDocx("Body", chrome, null);

        MainDocumentPart mainPart = mainPart(bytes);
        SectPr sectPr = sectPr(mainPart);
        FooterReference defaultRef = sectPr.getEGHdrFtrReferences().stream()
                .filter(r -> r instanceof FooterReference)
                .map(r -> (FooterReference) r)
                .filter(r -> r.getType() == HdrFtrRef.DEFAULT)
                .findFirst().orElseThrow();
        Object footerPart = resolvePart(mainPart, defaultRef.getId());

        boolean anyField = flatten(footerPart).stream().anyMatch(o -> o instanceof CTSimpleField)
                || flatten(mainPart).stream().anyMatch(o -> o instanceof CTSimpleField);
        assertThat(anyField).isFalse();
    }

    @Test
    void logoBecomesAnImagePartWhenPresent() throws Exception {
        ChromeFields chrome = new ChromeFields(true, "T", "S", "", "", false);
        byte[] logoBytes = png(4, 4);
        LogoImage logo = new LogoImage(logoBytes,
                new LogoMetadata("image/png", "logo.png", logoBytes.length, 4, 4, Instant.now()));

        byte[] withLogo = adapter.toDocx("Body", chrome, logo);
        WordprocessingMLPackage pkgWithLogo = load(withLogo);
        long imagePartsWithLogo = pkgWithLogo.getParts().getParts().values().stream()
                .filter(p -> p instanceof BinaryPartAbstractImage).count();
        assertThat(imagePartsWithLogo).isGreaterThanOrEqualTo(1);

        byte[] withoutLogo = adapter.toDocx("Body", chrome, null);
        WordprocessingMLPackage pkgWithoutLogo = load(withoutLogo);
        long imagePartsWithoutLogo = pkgWithoutLogo.getParts().getParts().values().stream()
                .filter(p -> p instanceof BinaryPartAbstractImage).count();
        assertThat(imagePartsWithoutLogo).isEqualTo(0);
    }

    @Test
    void emptyMarkdownStillProducesAValidPackage() throws Exception {
        byte[] bytes = adapter.toDocx("", ChromeFields.empty(), null);

        assertThat(load(bytes)).isNotNull();
    }

    // --- test helpers: reopen bytes, walk the OOXML tree, never trust the writer's own intent ---

    private static WordprocessingMLPackage load(byte[] bytes) throws Exception {
        return (WordprocessingMLPackage) WordprocessingMLPackage.load(new ByteArrayInputStream(bytes));
    }

    private static byte[] save(WordprocessingMLPackage pkg) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }

    private static MainDocumentPart mainPart(byte[] bytes) throws Exception {
        return load(bytes).getMainDocumentPart();
    }

    private static SectPr sectPr(MainDocumentPart mainPart) {
        return mainPart.getJaxbElement().getBody().getSectPr();
    }

    private static Object resolvePart(MainDocumentPart mainPart, String relationshipId) {
        RelationshipsPart relationshipsPart = mainPart.getRelationshipsPart();
        Relationship rel = relationshipsPart.getRelationshipByID(relationshipId);
        return relationshipsPart.getPart(rel);
    }

    private static List<P> paragraphsOf(Object root) {
        return flatten(root).stream().filter(o -> o instanceof P).map(o -> (P) o).toList();
    }

    private static List<R> runsOf(Object root) {
        return flatten(root).stream().filter(o -> o instanceof R).map(o -> (R) o).toList();
    }

    private String textOf(Object root) {
        return flatten(root).stream().filter(o -> o instanceof Text)
                .map(o -> ((Text) o).getValue())
                .reduce("", String::concat);
    }

    private static String textOfCell(org.docx4j.wml.Tc cell) {
        return flatten(cell).stream().filter(o -> o instanceof Text)
                .map(o -> ((Text) o).getValue())
                .reduce("", String::concat);
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

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
