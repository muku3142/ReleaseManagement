package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFilenameTest {

    private static final Pattern SAFE_FILENAME = Pattern.compile("^[A-Za-z0-9 ._-]+$");
    private static final Pattern SAFE_ATTACHMENT_HEADER =
            Pattern.compile("^attachment; filename=\"[A-Za-z0-9 ._-]+\"$");

    @Test
    void plainInputsProduceTheExpectedFilename() {
        String filename = ExportFilename.sanitize("Orchstro", "4.2.0", TemplateDocumentType.RELEASE_NOTES, 2, "pdf");

        assertThat(filename).isEqualTo("Orchstro 4.2.0 Release Notes r2.pdf");
    }

    @Test
    void aQuoteInTheNameProducesNoQuoteInTheFilename() {
        String filename = ExportFilename.sanitize("A\"B", "1.0", TemplateDocumentType.RELEASE_NOTES, 1, "pdf");

        assertThat(filename).doesNotContain("\"");
    }

    @Test
    void crOrLfInTheNameProducesNeitherAndNoSecondHeaderLine() {
        String filename = ExportFilename.sanitize("A\r\nX-Injected: 1", "1.0", TemplateDocumentType.RELEASE_NOTES,
                1, "pdf");

        assertThat(filename).doesNotContain("\r").doesNotContain("\n");
        assertThat(filename.lines().count()).isEqualTo(1);
    }

    @Test
    void pathTraversalSegmentsAreStrippedOfSlashesAndDotDot() {
        String filename = ExportFilename.sanitize("../../etc/passwd", "1.0", TemplateDocumentType.RELEASE_NOTES,
                1, "pdf");

        assertThat(filename).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
    }

    @Test
    void nonAsciiInputProducesAnAsciiOnlyFilename() {
        String filename = ExportFilename.sanitize("résumé 🎉", "1.0", TemplateDocumentType.RELEASE_NOTES,
                1, "pdf");

        assertThat(filename).matches(s -> s.chars().allMatch(c -> c < 0x80));
    }

    @Test
    void aNameAndVersionThatSanitiseToNothingStillProduceANonEmptySafeFilename() {
        // The document-type label and revision suffix are always non-empty and allowlist-safe, so
        // a hostile name/version alone cannot drive the whole candidate to empty here - only their
        // own contribution vanishes. The fallback-to-"document" branch below is exercised by a
        // scenario where truncation removes everything, not by a hostile name/version alone.
        String filename = ExportFilename.sanitize("\"\"\"", "\"\"\"", TemplateDocumentType.RELEASE_NOTES, 1, "pdf");

        assertThat(filename).isEqualTo("Release Notes r1.pdf");
    }

    @Test
    void aCandidateEmptiedByTruncationFallsBackToDocument() {
        String longExtension = "p".repeat(119);
        String filename = ExportFilename.sanitize("Orchstro", "4.2.0", TemplateDocumentType.RELEASE_NOTES, 1,
                longExtension);

        assertThat(filename).isEqualTo("document." + longExtension);
    }

    @Test
    void resultIsCappedAt120CharactersIncludingExtension() {
        String longName = "a".repeat(300);
        String filename = ExportFilename.sanitize(longName, "1.0", TemplateDocumentType.RELEASE_NOTES, 1, "pdf");

        assertThat(filename.length()).isLessThanOrEqualTo(120);
    }

    @Test
    void resultNeverContainsConsecutiveUnderscoresOrLeadingTrailingUnderscoreOrSpace() {
        String filename = ExportFilename.sanitize("!!!Weird///Name???", "1.0", TemplateDocumentType.RELEASE_NOTES,
                1, "pdf");

        assertThat(filename).doesNotContain("__");
        String base = filename.substring(0, filename.lastIndexOf('.'));
        assertThat(base).doesNotStartWith("_").doesNotStartWith(" ");
        assertThat(base).doesNotEndWith("_").doesNotEndWith(" ");
    }

    @Test
    void everyCharacterOutsideTheAllowlistIsReplaced() {
        for (int codePoint = 0; codePoint <= 0x7F; codePoint++) {
            char c = (char) codePoint;
            boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '.' || c == '_' || c == '-';
            if (allowed) {
                continue;
            }
            String filename = ExportFilename.sanitize("x" + c + "x", "1.0", TemplateDocumentType.RELEASE_NOTES,
                    1, "pdf");
            String base = filename.substring(0, filename.lastIndexOf('.'));
            assertThat(SAFE_FILENAME.matcher(base).matches())
                    .withFailMessage("codepoint 0x%02X leaked into filename: %s", codePoint, filename)
                    .isTrue();
        }
    }

    @Test
    void noOutputContainsAHeaderTerminator() {
        List<String> hostileNames = List.of("a\"b", "a\rb", "a\nb", "a\r\nX-Injected: 1", "a;b", "a\\b", "a/b",
                "..", ".", "", "a".repeat(300));

        for (String name : hostileNames) {
            String filename = ExportFilename.sanitize(name, "1.0", TemplateDocumentType.RELEASE_NOTES, 1, "pdf");
            String header = ExportFilename.attachment(filename);
            assertThat(SAFE_ATTACHMENT_HEADER.matcher(header).matches())
                    .withFailMessage("hostile name %s produced unsafe header: %s", name, header)
                    .isTrue();
        }
    }
}
