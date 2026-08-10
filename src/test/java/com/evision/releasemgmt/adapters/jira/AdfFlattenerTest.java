package com.evision.releasemgmt.adapters.jira;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AdfFlattenerTest {

    private final AdfFlattener flattener = new AdfFlattener();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode json(String value) {
        return mapper.readTree(value);
    }

    @Test
    void nullInputReturnsEmptyString() {
        assertThat(flattener.flatten(null)).isEqualTo("");
    }

    @Test
    void singleParagraphOfPlainText() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                    {"type":"text","text":"Hello world"}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("Hello world\n");
    }

    @Test
    void strongMarkProducesBoldMarkdown() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                    {"type":"text","text":"bold","marks":[{"type":"strong"}]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("**bold**\n");
    }

    @Test
    void emMarkProducesItalicMarkdown() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                    {"type":"text","text":"italic","marks":[{"type":"em"}]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("*italic*\n");
    }

    @Test
    void codeMarkProducesInlineCodeMarkdown() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                    {"type":"text","text":"inline","marks":[{"type":"code"}]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("`inline`\n");
    }

    @Test
    void linkMarkProducesLabelWithHrefInParens() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                    {"type":"text","text":"label","marks":[
                      {"type":"link","attrs":{"href":"https://example.com"}}
                    ]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("label (https://example.com)\n");
    }

    @Test
    void bulletListWithTwoItemsProducesTwoDashLines() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"bulletList","content":[
                    {"type":"listItem","content":[
                      {"type":"paragraph","content":[{"type":"text","text":"first"}]}
                    ]},
                    {"type":"listItem","content":[
                      {"type":"paragraph","content":[{"type":"text","text":"second"}]}
                    ]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("- first\n- second\n");
    }

    @Test
    void nestedBulletListInsideListItemIsIndentedByTwoSpaces() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"bulletList","content":[
                    {"type":"listItem","content":[
                      {"type":"paragraph","content":[{"type":"text","text":"outer"}]},
                      {"type":"bulletList","content":[
                        {"type":"listItem","content":[
                          {"type":"paragraph","content":[{"type":"text","text":"inner"}]}
                        ]}
                      ]}
                    ]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("- outer\n  - inner\n");
    }

    @Test
    void orderedListDoesNotNumberItemsInThisPhase() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"orderedList","content":[
                    {"type":"listItem","content":[
                      {"type":"paragraph","content":[{"type":"text","text":"first"}]}
                    ]},
                    {"type":"listItem","content":[
                      {"type":"paragraph","content":[{"type":"text","text":"second"}]}
                    ]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("- first\n- second\n");
    }

    @Test
    void codeBlockWrapsContentInFences() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"codeBlock","content":[
                    {"type":"text","text":"var x = 1;"}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("```\nvar x = 1;\n```\n");
    }

    @Test
    void headingProducesAtxPrefixOnItsOwnLine() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},"content":[
                    {"type":"text","text":"Description"}
                  ]},
                  {"type":"paragraph","content":[
                    {"type":"text","text":"Body text"}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("## Description\nBody text\n");
    }

    @Test
    void headingWithoutLevelAttrDefaultsToLevelOne() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"heading","content":[
                    {"type":"text","text":"Title"}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("# Title\n");
    }

    @Test
    void headingWithOutOfRangeLevelStaysValidMarkdown() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":9},"content":[
                    {"type":"text","text":"Deep"}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("###### Deep\n");
    }

    @Test
    void unknownNodeTypeDegradesToItsParagraphText() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"panel","content":[
                    {"type":"paragraph","content":[{"type":"text","text":"panel content"}]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("panel content\n");
    }

    @Test
    void unknownMarkTypeKeepsTextAndDropsOnlyTheDecoration() {
        JsonNode doc = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                    {"type":"text","text":"colored","marks":[{"type":"textColor","attrs":{"color":"#ff0000"}}]}
                  ]}
                ]}""");
        assertThat(flattener.flatten(doc)).isEqualTo("colored\n");
    }

    @Test
    void overDeepTreeTruncatesInsteadOfThrowing() {
        ObjectNode innermost = mapper.createObjectNode();
        innermost.put("type", "paragraph");
        innermost.putArray("content").addObject().put("type", "text").put("text", "deep");

        JsonNode current = innermost;
        for (int i = 0; i < 60; i++) {
            ObjectNode bulletList = mapper.createObjectNode();
            bulletList.put("type", "bulletList");
            bulletList.putArray("content").add(current);
            current = bulletList;
        }
        ObjectNode doc = mapper.createObjectNode();
        doc.put("type", "doc");
        doc.putArray("content").add(current);

        JsonNode finalDoc = doc;
        assertThatCode(() -> flattener.flatten(finalDoc)).doesNotThrowAnyException();
        assertThat(flattener.flatten(finalDoc)).contains("[content truncated]");
    }
}
