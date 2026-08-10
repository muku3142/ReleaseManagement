package com.evision.releasemgmt.adapters.render;

import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MustacheFlexmarkRenderAdapterTest {

    private final MustacheFlexmarkRenderAdapter adapter = new MustacheFlexmarkRenderAdapter();

    @Test
    void mustacheThenMarkdownProducesHtml() {
        String html = adapter.render("# {{release.name}}", releaseCtx("Sprint 42"));
        assertThat(html).contains("<h1>Sprint 42</h1>");
    }

    @Test
    void rawHtmlTypedIntoTheBodyIsInert() {
        String html = adapter.render("<script>alert(1)</script>", Map.of());
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<script>");
    }

    @Test
    void rawHtmlArrivingThroughInterpolationIsInert() {
        String html = adapter.render("{{release.name}}", releaseCtx("<img src=x onerror=alert(1)>"));
        assertThat(html).contains("&lt;img");
        assertThat(html).doesNotContain("<img");
    }

    @Test
    void ampersandIsEscapedExactlyOnce() {
        String html = adapter.render("Tom & Jerry {{release.name}}", releaseCtx("A & B"));
        assertThat(html).contains("Tom &amp; Jerry A &amp; B");
        // A double-escape bug (jmustache escaping, then flexmark escaping again) would produce
        // this instead - assert it is absent, not just that the correct form is present.
        assertThat(html).doesNotContain("&amp;amp;");
    }

    @Test
    void markdownVocabularyFromAdfFlattenerSurvivesUnmangled() {
        String html = adapter.render("**bold** and `code`", Map.of());
        assertThat(html).contains("<strong>bold</strong>");
        assertThat(html).contains("<code>code</code>");
    }

    @Test
    void sectionIteratesEveryItem() {
        Map<String, Object> ctx = Map.of("items", List.of(
                Map.of("title", "First"),
                Map.of("title", "Second")));
        String html = adapter.render("{{#items}}{{title}}\n{{/items}}", ctx);
        assertThat(html).contains("First");
        assertThat(html).contains("Second");
    }

    @Test
    void invertedSectionRendersForEmptyCollection() {
        Map<String, Object> ctx = Map.of("commits", List.of());
        String html = adapter.render("{{^commits}}No linked code{{/commits}}", ctx);
        assertThat(html).contains("No linked code");
    }

    @Test
    void emptyStringIsFalsyForInvertedSection() {
        Map<String, Object> ctx = Map.of("resolution", "");
        String html = adapter.render("{{^resolution}}Unresolved{{/resolution}}", ctx);
        assertThat(html).contains("Unresolved");
    }

    @Test
    void parsedAstContainsNoRawHtmlNodes() {
        Document document = adapter.parseMarkdown("<script>alert(1)</script> and <b>bold</b>");
        assertThat(nodesOfType(document, HtmlBlock.class)).isEmpty();
        assertThat(nodesOfType(document, HtmlInline.class)).isEmpty();
    }

    @Test
    void pipeTableRendersAsAnHtmlTable() {
        String source = """
                | Service | Service |
                |---|---|
                | Archival Management API | UC Engine |
                """;
        String html = adapter.render(source, Map.of());
        assertThat(html).contains("<table>");
        assertThat(html).contains("<thead>");
        assertThat(html).contains("<th>Service</th>");
        assertThat(html).contains("<td>UC Engine</td>");
        assertThat(html).doesNotContain("| Service |");
    }

    @Test
    void rawHtmlInsideATableCellIsStillNeutralised() {
        String source = """
                | Service | Notes |
                |---|---|
                | Archival Management API | <script>alert(1)</script> |
                """;
        String html = adapter.render(source, Map.of());
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<script>");
    }

    @Test
    void renderHtmlOnTheSharedAstMatchesRender() {
        String source = """
                # Heading

                - one
                - two

                | Service | Service |
                |---|---|
                | Archival Management API | UC Engine |
                """;
        assertThat(adapter.renderHtml(adapter.parseMarkdown(source))).isEqualTo(adapter.render(source, Map.of()));
    }

    @Test
    void resolveReturnsPlainTextWithNoMarkdownWrapper() {
        String resolved = adapter.resolve("Orchstro {{v}} notes", Map.of("v", "4.2.0"));
        assertThat(resolved).isEqualTo("Orchstro 4.2.0 notes");
    }

    private static Map<String, Object> releaseCtx(String name) {
        return Map.of("release", Map.of("name", name));
    }

    private static List<Node> nodesOfType(Node root, Class<? extends Node> type) {
        List<Node> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static void collect(Node node, Class<? extends Node> type, List<Node> found) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (type.isInstance(child)) {
                found.add(child);
            }
            collect(child, type, found);
        }
    }
}
