package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.application.assistant.DocumentSections.Part;
import com.evision.releasemgmt.domain.assistant.TransformTooLargeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSectionsTest {

    private static final String RELEASE_NOTE = """
            # Release 4.3 — 4.3

            ## Release Summary

            Version 4.3 delivers improvements.

            ## Bug

            ### POSTGRES | Null values in setup (CNV-2865)

            ## Story

            ### gSRP002: Cancellation requests 192/199 structure (CNV-2904)

            ### Description
            When sending an MT192 cancellation request, field `:79:` contains:
            ```
            :79:/AGNT/
            # not a heading inside a code block
            ```
            the system does not update `GPI_STATUS`.

            - First bullet
            """;

    @Test
    void joiningEveryPartGivesBackTheOriginalDocument() {
        assertThat(join(DocumentSections.split(RELEASE_NOTE, 10_000))).isEqualTo(RELEASE_NOTE);
    }

    @Test
    void headingLinesAreCopiedVerbatimAndNeverSentToTheModel() {
        List<Part> parts = DocumentSections.split(RELEASE_NOTE, 10_000);

        assertThat(parts).filteredOn(part -> part.text().startsWith("#")).allMatch(part -> !part.transform());
        assertThat(parts).filteredOn(Part::transform).noneMatch(part -> part.text().startsWith("#"));
        assertThat(parts).contains(new Part("### gSRP002: Cancellation requests 192/199 structure (CNV-2904)\n", false));
    }

    @Test
    void aHashLineInsideACodeBlockIsNotASectionBoundary() {
        List<Part> parts = DocumentSections.split(RELEASE_NOTE, 10_000);

        assertThat(parts).filteredOn(Part::transform)
                .anySatisfy(part -> assertThat(part.text()).contains("# not a heading inside a code block")
                        .startsWith("When sending").endsWith("- First bullet"));
    }

    @Test
    void aHeadingWithNoBodyProducesNoModelCall() {
        List<Part> parts = DocumentSections.split(RELEASE_NOTE, 10_000);

        assertThat(DocumentSections.transformCount(parts)).isEqualTo(2);
        assertThat(parts).filteredOn(Part::transform).extracting(Part::text)
                .containsExactly("Version 4.3 delivers improvements.", parts.stream().filter(Part::transform)
                        .skip(1).findFirst().orElseThrow().text());
    }

    @Test
    void sectionTextExcludesTheBlankLinesAroundIt() {
        List<Part> parts = DocumentSections.split("## A\n\n  Body line\n\n\n## B\n", 10_000);

        assertThat(parts).containsExactly(
                new Part("## A\n", false), new Part("\n", false), new Part("  Body line", true),
                new Part("\n\n\n", false), new Part("## B\n", false));
    }

    @Test
    void aBodyOverTheLimitIsSplitAtParagraphsAndStillJoinsBack() {
        String body = "## Long\n\nFirst paragraph is here.\n\nSecond paragraph is here.\n\nThird paragraph.\n";

        List<Part> parts = DocumentSections.split(body, 30);

        assertThat(join(parts)).isEqualTo(body);
        assertThat(parts).filteredOn(Part::transform).extracting(Part::text)
                .containsExactly("First paragraph is here.", "Second paragraph is here.", "Third paragraph.");
        assertThat(parts).filteredOn(Part::transform).allMatch(part -> part.text().length() <= 30);
    }

    @Test
    void smallParagraphsArePackedTogetherUpToTheLimit() {
        String body = "One.\n\nTwo.\n\nThree.\n";

        List<Part> parts = DocumentSections.split(body, 12);

        assertThat(join(parts)).isEqualTo(body);
        assertThat(parts).filteredOn(Part::transform).extracting(Part::text).containsExactly("One.\n\nTwo.", "Three.");
    }

    @Test
    void aSingleParagraphOverTheLimitIsTooLarge() {
        String body = "## Big\n\n" + "x".repeat(50) + "\n";

        assertThatThrownBy(() -> DocumentSections.split(body, 40))
                .isInstanceOfSatisfying(TransformTooLargeException.class, e -> {
                    assertThat(e.getCharacters()).isEqualTo(50);
                    assertThat(e.getMaxCharacters()).isEqualTo(40);
                });
    }

    @Test
    void aDocumentOfOnlyHeadingsHasNothingToTransform() {
        List<Part> parts = DocumentSections.split("# A\n## B\n", 100);

        assertThat(DocumentSections.transformCount(parts)).isZero();
        assertThat(join(parts)).isEqualTo("# A\n## B\n");
    }

    private static String join(List<Part> parts) {
        return parts.stream().map(Part::text).collect(Collectors.joining());
    }
}
