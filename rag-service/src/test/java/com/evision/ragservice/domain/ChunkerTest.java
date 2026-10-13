package com.evision.ragservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private static final int MAX = 1480;
    private static final int OVERLAP = 100;
    private static final String TITLE = "Release 1.2 notes";

    private final Chunker chunker = new Chunker(MAX, OVERLAP);

    private static String words(int chars) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < chars) {
            sb.append("word").append(i++).append(' ');
        }
        return sb.substring(0, chars).strip();
    }

    @Test
    void shortTextIsOnePassageEqualToTrimmedText() {
        assertThat(chunker.chunk(TITLE, "  hello world \n")).containsExactly("hello world");
    }

    @Test
    void sectionsArePackedGreedilyAndNewSectionStartsAtHeading() {
        String text = "## A\n" + words(600) + "\n\n## B\n" + words(600) + "\n\n## C\n" + words(600);

        List<String> passages = chunker.chunk(TITLE, text);

        assertThat(passages).hasSize(2);
        assertThat(passages.get(0)).startsWith("## A").contains("## B");
        assertThat(passages.get(1)).startsWith("## C");
    }

    @Test
    void longParagraphIsSplitWithWordBoundaryOverlap() {
        List<String> passages = chunker.chunk(TITLE, words(5000));

        assertThat(passages.size()).isGreaterThan(3);
        for (int i = 1; i < passages.size(); i++) {
            String previous = passages.get(i - 1);
            String current = passages.get(i);
            String tail = current.substring(0, current.indexOf("\n\n") > 0 ? current.indexOf("\n\n") : 0);
            assertThat(tail).isNotEmpty().hasSizeLessThanOrEqualTo(OVERLAP);
            assertThat(previous).endsWith(tail);
            assertThat(previous.charAt(previous.length() - tail.length() - 1)).isEqualTo(' ');
        }
    }

    @Test
    void everyPassageWithHeaderStaysWithinBudget() {
        List<String> inputs = List.of(
                "short",
                "# T\n" + words(900) + "\n\n## S\n" + words(1400) + "\n\n```\n# not heading\n```\n" + words(700),
                words(5000));

        for (String input : inputs) {
            for (String p : chunker.chunk(TITLE, input)) {
                assertThat(Chunker.withHeader(TITLE, p).length()).isLessThanOrEqualTo(MAX);
            }
        }
    }

    @Test
    void headingInsideFenceDoesNotStartSection() {
        String text = words(700) + "\n\n```\n# fake\n```\n\n" + words(100);
        // one section (no real heading): the whole text fits one passage
        assertThat(chunker.chunk(TITLE, "```\n# fake\n```\nbody")).hasSize(1);
        assertThat(chunker.chunk(TITLE, text)).hasSize(1);
    }

    @Test
    void crlfEqualsLf() {
        String lf = "## A\n" + words(900) + "\n\n## B\n" + words(900);
        assertThat(chunker.chunk(TITLE, lf.replace("\n", "\r\n"))).isEqualTo(chunker.chunk(TITLE, lf));
    }

    @Test
    void blankTextYieldsNoPassages() {
        assertThat(chunker.chunk(TITLE, " \n\t ")).isEmpty();
        assertThat(chunker.chunk(TITLE, "")).isEmpty();
    }

    @Test
    void noPassageIsBlank() {
        assertThat(chunker.chunk(TITLE, "## A\n\n\n\n## B\n" + words(3000))).noneMatch(String::isBlank);
    }

    @Test
    void wordLongerThanBudgetIsHardCut() {
        List<String> passages = chunker.chunk(TITLE, "x".repeat(5000));

        assertThat(passages.size()).isGreaterThan(3);
        assertThat(passages).allMatch(p -> Chunker.withHeader(TITLE, p).length() <= MAX);
    }

    @Test
    void constructorRejectsOverlapThatLeavesNoRoom() {
        assertThatThrownBy(() -> new Chunker(200, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withHeaderPrefixesTitle() {
        assertThat(Chunker.withHeader("T", "p")).isEqualTo("T\n\np");
    }
}
