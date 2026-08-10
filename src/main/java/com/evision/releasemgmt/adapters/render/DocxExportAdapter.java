package com.evision.releasemgmt.adapters.render;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.ports.DocxExportPort;
import com.vladsch.flexmark.util.ast.Document;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Body;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTSimpleField;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.FooterReference;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.HeaderReference;
import org.docx4j.wml.HpsMeasure;
import org.docx4j.wml.Jc;
import org.docx4j.wml.JcEnumeration;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Text;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

// Confines docx4j to adapters.render (ArchUnit-enforced, 05-01), exactly like jmustache/flexmark.
// Pattern 1: a sibling port to RenderEnginePort - export consumes an already-resolved snapshot,
// not a live template + view, so it does not go through RenderService again (Pitfall 5).
@Component
public class DocxExportAdapter implements DocxExportPort {

    // T-05-29 / Don't Hand-Roll: docx4j 17.0.2's NumberingDefinitionsPart has no builder API for
    // "give me exactly numId 1 = bullet, numId 2 = decimal" - unmarshalDefaultNumbering() loads
    // docx4j's own large built-in multi-level numbering resource with an unpredictable numId
    // mapping, not a safe fit for the fixed ids DocxAstWalker emits. This is the one permitted
    // raw-XML string in the whole export path: a fixed, project-authored constant with no user
    // data ever concatenated into it.
    private static final String NUMBERING_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:abstractNum w:abstractNumId="0">
                <w:lvl w:ilvl="0">
                  <w:start w:val="1"/>
                  <w:numFmt w:val="bullet"/>
                  <w:lvlText w:val="&#8226;"/>
                  <w:lvlJc w:val="left"/>
                  <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>
                </w:lvl>
              </w:abstractNum>
              <w:abstractNum w:abstractNumId="1">
                <w:lvl w:ilvl="0">
                  <w:start w:val="1"/>
                  <w:numFmt w:val="decimal"/>
                  <w:lvlText w:val="%1."/>
                  <w:lvlJc w:val="left"/>
                  <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>
                </w:lvl>
              </w:abstractNum>
              <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
              <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
            </w:numbering>
            """;

    // 1 pixel at 96 DPI, in EMUs (914400 EMU/inch / 96 px/inch) - the standard conversion used to
    // size an inline image from its own decoded pixel dimensions.
    private static final long EMU_PER_PIXEL = 9525L;

    private final ObjectFactory factory = Context.getWmlObjectFactory();
    private final MustacheFlexmarkRenderAdapter renderAdapter;

    public DocxExportAdapter(MustacheFlexmarkRenderAdapter renderAdapter) {
        this.renderAdapter = renderAdapter;
    }

    @Override
    public byte[] toDocx(String resolvedMarkdown, ChromeFields chrome, LogoImage logo) {
        try {
            // Pattern 2: a fresh package per call. WordprocessingMLPackage IS the document being
            // built - sharing one across concurrent requests would corrupt output (T-05-33).
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            MainDocumentPart mainDocumentPart = pkg.getMainDocumentPart();

            addNumberingPart(mainDocumentPart);

            if (chrome.coverEnabled()) {
                addCoverPage(pkg, mainDocumentPart, chrome, logo);
            }

            // The SHARED AST (D-04-04) - no local Parser, no re-entering RenderService.
            Document ast = renderAdapter.parseMarkdown(resolvedMarkdown);
            new DocxAstWalker().appendBody(ast, mainDocumentPart);

            addChromeParts(mainDocumentPart, chrome);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pkg.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("DOCX export failed", e));
        }
    }

    private void addNumberingPart(MainDocumentPart mainDocumentPart) throws Exception {
        NumberingDefinitionsPart numberingPart = new NumberingDefinitionsPart();
        numberingPart.unmarshal(
                new ByteArrayInputStream(NUMBERING_XML.getBytes(StandardCharsets.UTF_8)));
        mainDocumentPart.addTargetPart(numberingPart);
    }

    private void addCoverPage(WordprocessingMLPackage pkg, MainDocumentPart mainDocumentPart,
                              ChromeFields chrome, LogoImage logo) throws Exception {
        if (logo != null) {
            mainDocumentPart.getContent().add(logoParagraph(pkg, logo));
        }
        mainDocumentPart.getContent().add(centeredTextParagraph(chrome.coverTitle(), 56));
        mainDocumentPart.getContent().add(centeredTextParagraph(chrome.coverSubtitle(), 32));
        mainDocumentPart.getContent().add(pageBreakParagraph());
    }

    private P logoParagraph(WordprocessingMLPackage pkg, LogoImage logo) throws Exception {
        // T-05-31: width/height come from the stored, content-sniffed LogoMetadata, never from a
        // filename - docx4j itself also determines the embedded content type from the image
        // bytes' own signature, not from any filename string.
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, logo.bytes());
        long widthEmu = (long) logo.metadata().width() * EMU_PER_PIXEL;
        long heightEmu = (long) logo.metadata().height() * EMU_PER_PIXEL;
        Inline inline = imagePart.createImageInline("logo", "Logo", 0, 1, widthEmu, heightEmu, false);
        Drawing drawing = new Drawing();
        drawing.getAnchorOrInline().add(inline);
        R run = factory.createR();
        run.getContent().add(drawing);
        P p = centeredParagraph();
        p.getContent().add(run);
        return p;
    }

    private P centeredParagraph() {
        P p = factory.createP();
        p.setPPr(centeredPPr());
        return p;
    }

    private P centeredTextParagraph(String text, int halfPoints) {
        P p = factory.createP();
        p.setPPr(centeredPPr());
        if (text != null && !text.isBlank()) {
            R run = factory.createR();
            RPr rPr = factory.createRPr();
            HpsMeasure size = factory.createHpsMeasure();
            size.setVal(BigInteger.valueOf(halfPoints));
            rPr.setSz(size);
            run.setRPr(rPr);
            run.getContent().add(plainText(text));
            p.getContent().add(run);
        }
        return p;
    }

    private PPr centeredPPr() {
        PPr ppr = factory.createPPr();
        Jc jc = factory.createJc();
        jc.setVal(JcEnumeration.CENTER);
        ppr.setJc(jc);
        return ppr;
    }

    private P pageBreakParagraph() {
        P p = factory.createP();
        R run = factory.createR();
        org.docx4j.wml.Br pageBreak = factory.createBr();
        pageBreak.setType(org.docx4j.wml.STBrType.PAGE);
        run.getContent().add(pageBreak);
        p.getContent().add(run);
        return p;
    }

    private void addChromeParts(MainDocumentPart mainDocumentPart, ChromeFields chrome) throws Exception {
        SectPr sectPr = sectPrOf(mainDocumentPart);

        if (chrome.coverEnabled()) {
            sectPr.setTitlePg(new BooleanDefaultTrue());
        }

        if (chrome.headerText() != null && !chrome.headerText().isBlank()) {
            HeaderPart defaultHeader = new HeaderPart();
            defaultHeader.getContent().add(plainParagraph(chrome.headerText()));
            addHeaderReference(mainDocumentPart, sectPr, defaultHeader, HdrFtrRef.DEFAULT);
        }
        if (chrome.coverEnabled()) {
            HeaderPart firstHeader = new HeaderPart();
            addHeaderReference(mainDocumentPart, sectPr, firstHeader, HdrFtrRef.FIRST);
        }

        FooterPart defaultFooter = new FooterPart();
        defaultFooter.getContent().add(footerParagraph(chrome));
        addFooterReference(mainDocumentPart, sectPr, defaultFooter, HdrFtrRef.DEFAULT);
        if (chrome.coverEnabled()) {
            FooterPart firstFooter = new FooterPart();
            addFooterReference(mainDocumentPart, sectPr, firstFooter, HdrFtrRef.FIRST);
        }
    }

    private SectPr sectPrOf(MainDocumentPart mainDocumentPart) {
        Body body = mainDocumentPart.getJaxbElement().getBody();
        SectPr sectPr = body.getSectPr();
        if (sectPr == null) {
            sectPr = factory.createSectPr();
            body.setSectPr(sectPr);
        }
        return sectPr;
    }

    private void addHeaderReference(MainDocumentPart mainDocumentPart, SectPr sectPr, Part part,
                                    HdrFtrRef refType) throws Exception {
        Relationship rel = mainDocumentPart.addTargetPart(part);
        HeaderReference ref = new HeaderReference();
        ref.setType(refType);
        ref.setId(rel.getId());
        sectPr.getEGHdrFtrReferences().add(ref);
    }

    private void addFooterReference(MainDocumentPart mainDocumentPart, SectPr sectPr, Part part,
                                    HdrFtrRef refType) throws Exception {
        Relationship rel = mainDocumentPart.addTargetPart(part);
        FooterReference ref = new FooterReference();
        ref.setType(refType);
        ref.setId(rel.getId());
        sectPr.getEGHdrFtrReferences().add(ref);
    }

    private P plainParagraph(String text) {
        P p = factory.createP();
        R run = factory.createR();
        run.getContent().add(plainText(text));
        p.getContent().add(run);
        return p;
    }

    private P footerParagraph(ChromeFields chrome) {
        P p = factory.createP();
        if (chrome.footerText() != null && !chrome.footerText().isBlank()) {
            R run = factory.createR();
            run.getContent().add(plainText(chrome.footerText()));
            p.getContent().add(run);
        }
        if (chrome.pageNumbersEnabled()) {
            // Don't Hand-Roll: CTSimpleField is docx4j's supported wrapper over the raw
            // fldSimple/fldChar XML - fldSimple is a paragraph-content-model element wrapping a
            // display-value run, not a run-level child, so the JAXBElement goes straight onto the
            // paragraph's content list.
            p.getContent().add(literalRun(" • Page "));
            p.getContent().add(pageField(" PAGE \\* MERGEFORMAT "));
            p.getContent().add(literalRun(" of "));
            p.getContent().add(pageField(" NUMPAGES \\* MERGEFORMAT "));
        }
        return p;
    }

    private R literalRun(String text) {
        R run = factory.createR();
        run.getContent().add(plainText(text));
        return run;
    }

    private Object pageField(String instr) {
        CTSimpleField field = factory.createCTSimpleField();
        field.setInstr(instr);
        return factory.createPFldSimple(field);
    }

    private Text plainText(String value) {
        Text text = factory.createText();
        text.setValue(value);
        text.setSpace("preserve");
        return text;
    }
}
