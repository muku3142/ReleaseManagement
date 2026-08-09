package com.evision.releasemgmt.adapters.render;

import com.evision.releasemgmt.domain.template.TemplateMalformedException;
import com.evision.releasemgmt.ports.RenderEnginePort;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.vladsch.flexmark.ast.HtmlBlockBase;
import com.vladsch.flexmark.ast.HtmlInlineBase;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MustacheFlexmarkRenderAdapter implements RenderEnginePort {

    // Built once and reused: both the compiler and the parser/renderer are documented as reusable,
    // and this is a keystroke-frequency preview path (D-04-11 context) - rebuilding them per call
    // would waste work on every render.
    private final Mustache.Compiler compiler;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public MustacheFlexmarkRenderAdapter() {
        this.compiler = Mustache.compiler()
                // D-04-05: jmustache's own HTML escaper is OFF. It is the wrong escaper for a
                // Markdown pipeline - it would turn a ticket summary's "&" into a visible "&amp;"
                // before flexmark ever sees it, i.e. double-escaping. The defence lives one layer
                // down, at flexmark (see neutralizeRawHtml below): raw HTML can never survive the
                // Markdown parse regardless of what jmustache does with it.
                .escapeHTML(false)
                // An empty string is falsy, so {{^resolution}}Unresolved{{/resolution}} renders for
                // an unresolved ticket, whose resolution is "" rather than null (D-04-18's shape).
                .emptyStringIsFalse(true);
        // No defaultValue() is configured: TemplateScanner has already rewritten every unknown
        // token to a literal marker before the source reaches this adapter (see RenderService), so
        // a key still missing here is a defect in our own RenderContextMapper, not a template
        // author's mistake - jmustache's default behaviour (throw MustacheException) is correct.

        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    @Override
    public String render(String source, Map<String, Object> context) {
        String resolved = resolveMustache(source, context);
        Document document = parseMarkdown(resolved);
        return htmlRenderer.render(document);
    }

    private String resolveMustache(String source, Map<String, Object> context) {
        try {
            return compiler.compile(source).execute(context);
        } catch (MustacheException ex) {
            throw new TemplateMalformedException("Body", ex.getMessage());
        }
    }

    // Package-private so the adapter test can assert on the AST directly, and so Phase 5 can walk
    // this same single Document to both XHTML->PDF (openhtmltopdf) and OOXML (docx4j) - D-04-04.
    Document parseMarkdown(String markdown) {
        Document document = markdownParser.parse(markdown);
        neutralizeRawHtml(document);
        return document;
    }

    // D-04-05: flexmark 0.64.8 has no Parser option that stops raw inline HTML ('<script>', '<b>'
    // typed directly into a paragraph, or arriving through an interpolated ticket field) from
    // becoming an HtmlInline node - InlineParserImpl.parseHtmlInline() is called unconditionally,
    // with no DataKey guard. So raw HTML is neutralised here, after parsing, rather than prevented
    // at parse time: every HtmlBlock/HtmlCommentBlock (block-level) and HtmlInline/HtmlInlineComment
    // (inline) node is replaced by a Text node carrying the identical raw characters. A Text node is
    // always rendered HTML-escaped (CoreNodeRenderer.render(Text, ...) calls html.text(...)), so the
    // tag becomes literal, readable text, and the AST that Phase 5 walks contains no HTML node left
    // to reintroduce as markup (T-04-13).
    private void neutralizeRawHtml(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            if (child instanceof HtmlBlockBase || child instanceof HtmlInlineBase) {
                child.insertBefore(new Text(child.getChars()));
                child.unlink();
            } else {
                neutralizeRawHtml(child);
            }
            child = next;
        }
    }
}
