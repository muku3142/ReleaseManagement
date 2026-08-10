package com.evision.releasemgmt.adapters.render;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.util.ast.ContentNode;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblGrid;
import org.docx4j.wml.TblGridCol;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

// Walks the SAME flexmark Document both PdfExportAdapter's HtmlRenderer and this class consume
// (D-04-04) into docx4j 17.0.2 OOXML objects. Deliberately NOT a @Component: it holds no
// dependencies of its own, and DocxExportAdapter constructs one per call alongside its fresh
// WordprocessingMLPackage (Pattern 2 - nothing here is safe to share across concurrent requests
// anyway, since every method mutates a caller-supplied MainDocumentPart/P/Tbl).
//
// The node vocabulary is closed and small (RESEARCH.md's "fixed AST vocabulary" table, re-checked
// against the real starter-*.md / orchstro-release-notes.md bodies - see 05-06-SUMMARY.md's A2
// note). Every branch below maps one vocabulary row; anything NOT in the table - a genuinely
// unknown node type, or a construct the parser doesn't recognise as a special node at all - falls
// through to the same posture MustacheFlexmarkRenderAdapter.neutralizeRawHtml takes for raw HTML:
// degrade to literal, readable, JAXB-escaped text. Never dropped, never re-emitted as markup.
class DocxAstWalker {

    private static final String MONOSPACE_FONT = "Consolas";
    private static final BigInteger NUM_ID_BULLET = BigInteger.valueOf(1);
    private static final BigInteger NUM_ID_DECIMAL = BigInteger.valueOf(2);
    private static final BigInteger LIST_INDENT_TWIPS = BigInteger.valueOf(720);
    // WR-03: DocxExportAdapter.NUMBERING_XML only defines ilvl 0-4 for each abstractNum - a list
    // nested deeper than that has no defined numbering level to reference, so depth is capped here
    // to stay in lockstep with what is actually defined.
    private static final int MAX_LIST_DEPTH = 4;

    private final ObjectFactory factory = Context.getWmlObjectFactory();

    void appendBody(Document ast, MainDocumentPart target) {
        List<Object> content = target.getContent();
        Node node = ast.getFirstChild();
        while (node != null) {
            appendBlock(node, content);
            node = node.getNext();
        }
    }

    private void appendBlock(Node node, List<Object> target) {
        if (node instanceof Heading heading) {
            int level = Math.min(Math.max(heading.getLevel(), 1), 6);
            target.add(buildParagraph(heading, "Heading" + level));
        } else if (node instanceof TableBlock tableBlock) {
            target.add(buildTable(tableBlock));
        } else if (node instanceof BulletList) {
            appendList(node, target, 0, NUM_ID_BULLET);
        } else if (node instanceof OrderedList) {
            appendList(node, target, 0, NUM_ID_DECIMAL);
        } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
            appendCodeLines(node, target);
        } else if (node instanceof ThematicBreak) {
            // No cheap OOXML analog for a horizontal rule; an empty paragraph preserves the visual
            // break instead of silently dropping it.
            target.add(factory.createP());
        } else if (node instanceof BlockQuote blockQuote) {
            appendBlockQuote(blockQuote, target);
        } else if (node instanceof Paragraph) {
            target.add(buildParagraph(node, null));
        } else {
            target.add(buildLiteralParagraph(node.getChars().toString()));
        }
    }

    private void appendBlockQuote(BlockQuote node, List<Object> target) {
        List<Object> nested = new ArrayList<>();
        Node child = node.getFirstChild();
        while (child != null) {
            appendBlock(child, nested);
            child = child.getNext();
        }
        for (Object item : nested) {
            if (item instanceof P p) {
                indentLeft(p);
            }
            target.add(item);
        }
    }

    private void indentLeft(P p) {
        PPr ppr = p.getPPr();
        if (ppr == null) {
            ppr = factory.createPPr();
            p.setPPr(ppr);
        }
        PPrBase.Ind ind = factory.createPPrBaseInd();
        ind.setLeft(LIST_INDENT_TWIPS);
        ppr.setInd(ind);
    }

    private void appendCodeLines(Node node, List<Object> target) {
        List<BasedSequence> lines = ((ContentNode) node).getContentLines();
        if (lines.isEmpty()) {
            target.add(buildMonospaceParagraph(""));
            return;
        }
        for (BasedSequence line : lines) {
            String text = line.toString().replaceAll("[\r\n]+$", "");
            target.add(buildMonospaceParagraph(text));
        }
    }

    private P buildMonospaceParagraph(String text) {
        P p = factory.createP();
        p.getContent().add(newRun(text, false, false, true));
        return p;
    }

    private P buildLiteralParagraph(String text) {
        P p = factory.createP();
        p.getContent().add(newRun(text, false, false, false));
        return p;
    }

    private P buildParagraph(Node blockNode, String pStyleName) {
        P p = factory.createP();
        if (pStyleName != null) {
            PPr ppr = factory.createPPr();
            PPrBase.PStyle style = factory.createPPrBasePStyle();
            style.setVal(pStyleName);
            ppr.setPStyle(style);
            p.setPPr(ppr);
        }
        appendInlineChildren(blockNode, p, false, false, false);
        return p;
    }

    private void appendList(Node listNode, List<Object> target, int depth, BigInteger numId) {
        Node item = listNode.getFirstChild();
        while (item != null) {
            if (item instanceof ListItem listItem) {
                appendListItem(listItem, target, depth, numId);
            }
            item = item.getNext();
        }
    }

    private void appendListItem(ListItem item, List<Object> target, int depth, BigInteger numId) {
        Node child = item.getFirstChild();
        while (child != null) {
            if (child instanceof BulletList) {
                appendList(child, target, depth + 1, NUM_ID_BULLET);
            } else if (child instanceof OrderedList) {
                appendList(child, target, depth + 1, NUM_ID_DECIMAL);
            } else if (child instanceof Paragraph) {
                P p = buildParagraph(child, null);
                applyListFormatting(p, depth, numId);
                target.add(p);
            } else {
                P p = buildLiteralParagraph(child.getChars().toString());
                applyListFormatting(p, depth, numId);
                target.add(p);
            }
            child = child.getNext();
        }
    }

    private void applyListFormatting(P p, int depth, BigInteger numId) {
        PPr ppr = p.getPPr();
        if (ppr == null) {
            ppr = factory.createPPr();
            p.setPPr(ppr);
        }
        PPrBase.NumPr numPr = factory.createPPrBaseNumPr();
        PPrBase.NumPr.Ilvl ilvl = factory.createPPrBaseNumPrIlvl();
        ilvl.setVal(BigInteger.valueOf(Math.min(depth, MAX_LIST_DEPTH)));
        numPr.setIlvl(ilvl);
        PPrBase.NumPr.NumId numIdElement = factory.createPPrBaseNumPrNumId();
        numIdElement.setVal(numId);
        numPr.setNumId(numIdElement);
        ppr.setNumPr(numPr);
    }

    private Tbl buildTable(TableBlock tableBlock) {
        Tbl tbl = factory.createTbl();
        List<Tr> rows = new ArrayList<>();
        int maxCols = 0;
        Node section = tableBlock.getFirstChild();
        while (section != null) {
            if (section instanceof TableHead || section instanceof TableBody) {
                Node row = section.getFirstChild();
                while (row != null) {
                    if (row instanceof TableRow tableRow) {
                        Tr tr = buildTableRow(tableRow);
                        rows.add(tr);
                        maxCols = Math.max(maxCols, tr.getContent().size());
                    }
                    row = row.getNext();
                }
            }
            section = section.getNext();
        }
        tbl.setTblGrid(buildTblGrid(maxCols));
        tbl.getContent().addAll(rows);
        return tbl;
    }

    private TblGrid buildTblGrid(int columnCount) {
        TblGrid grid = factory.createTblGrid();
        for (int i = 0; i < Math.max(columnCount, 1); i++) {
            TblGridCol col = factory.createTblGridCol();
            col.setW(BigInteger.valueOf(2000));
            grid.getGridCol().add(col);
        }
        return grid;
    }

    private Tr buildTableRow(TableRow tableRow) {
        Tr tr = factory.createTr();
        Node cell = tableRow.getFirstChild();
        while (cell != null) {
            if (cell instanceof TableCell tableCell) {
                tr.getContent().add(buildTableCell(tableCell));
            }
            cell = cell.getNext();
        }
        return tr;
    }

    private Tc buildTableCell(TableCell tableCell) {
        Tc tc = factory.createTc();
        P p = factory.createP();
        // Table cells are an inline content model in flexmark (no Paragraph wrapper), so the
        // cell's own children are walked directly as inline nodes into a single P.
        appendInlineChildren(tableCell, p, tableCell.isHeader(), false, false);
        tc.getContent().add(p);
        return tc;
    }

    private void appendInlineChildren(Node parent, P paragraph, boolean bold, boolean italic,
                                      boolean monospace) {
        Node child = parent.getFirstChild();
        while (child != null) {
            appendInline(child, paragraph, bold, italic, monospace);
            child = child.getNext();
        }
    }

    private void appendInline(Node node, P paragraph, boolean bold, boolean italic,
                              boolean monospace) {
        if (node instanceof StrongEmphasis) {
            appendInlineChildren(node, paragraph, true, italic, monospace);
        } else if (node instanceof Emphasis) {
            appendInlineChildren(node, paragraph, bold, true, monospace);
        } else if (node instanceof Code) {
            appendInlineChildren(node, paragraph, bold, italic, true);
        } else if (node instanceof Link) {
            // T-05-30: the link's TEXT only. No navigational element, no external relationship -
            // a stored document is an audit record, not a navigation surface.
            appendInlineChildren(node, paragraph, bold, italic, monospace);
        } else if (node instanceof SoftLineBreak) {
            paragraph.getContent().add(newRun(" ", bold, italic, monospace));
        } else if (node instanceof HardLineBreak) {
            R run = factory.createR();
            run.getContent().add(factory.createBr());
            paragraph.getContent().add(run);
        } else {
            // Text nodes (including HTML neutralised to Text by neutralizeRawHtml, T-05-28) and
            // any genuinely unknown inline node type: literal, JAXB-escaped text, never markup.
            paragraph.getContent().add(newRun(node.getChars().toString(), bold, italic, monospace));
        }
    }

    private R newRun(String value, boolean bold, boolean italic, boolean monospace) {
        R run = factory.createR();
        if (bold || italic || monospace) {
            RPr rPr = factory.createRPr();
            if (bold) {
                rPr.setB(new BooleanDefaultTrue());
            }
            if (italic) {
                rPr.setI(new BooleanDefaultTrue());
            }
            if (monospace) {
                RFonts fonts = factory.createRFonts();
                fonts.setAscii(MONOSPACE_FONT);
                fonts.setHAnsi(MONOSPACE_FONT);
                fonts.setCs(MONOSPACE_FONT);
                rPr.setRFonts(fonts);
            }
            run.setRPr(rPr);
        }
        Text text = factory.createText();
        text.setValue(value);
        text.setSpace("preserve");
        run.getContent().add(text);
        return run;
    }
}
