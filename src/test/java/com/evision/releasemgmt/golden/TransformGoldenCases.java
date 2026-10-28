package com.evision.releasemgmt.golden;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** Loader for the assistant transform fixture under {@code golden/assistant}. */
public final class TransformGoldenCases {

    private static final String ROOT = "/golden/assistant/";

    public enum DocumentType { RELEASE_NOTES, RUNBOOK, APPROVAL }

    public enum Mode { CLEAN_UP, SUMMARIZE, CUSTOM }

    public record Binding(String key, List<String> with) {
        public Binding {
            with = with == null ? List.of() : with;
        }
    }

    public record PointOfNoReturn(String warning, String beforeStep) {}

    public record Expect(
            List<String> keys,
            List<String> versions,
            List<String> environments,
            List<String> dates,
            List<Binding> bindings,
            List<String> steps,
            List<String> rollback,
            List<String> anchors,
            List<String> headings,
            String lastSection,
            List<String> forbidden,
            PointOfNoReturn pointOfNoReturn,
            boolean tooLarge) {
        public Expect {
            keys = keys == null ? List.of() : keys;
            versions = versions == null ? List.of() : versions;
            environments = environments == null ? List.of() : environments;
            dates = dates == null ? List.of() : dates;
            bindings = bindings == null ? List.of() : bindings;
            steps = steps == null ? List.of() : steps;
            rollback = rollback == null ? List.of() : rollback;
            anchors = anchors == null ? List.of() : anchors;
            headings = headings == null ? List.of() : headings;
            forbidden = forbidden == null ? List.of() : forbidden;
        }
    }

    public record Case(
            String id,
            DocumentType documentType,
            Mode mode,
            String instruction,
            String doc,
            Expect expect,
            String rubric) {}

    public record Fixture(int version, String labelsApprovedBy, String labeling, List<Case> cases) {}

    private TransformGoldenCases() {}

    public static Fixture load() {
        try (InputStream in = TransformGoldenCases.class.getResourceAsStream(ROOT + "transform-v1.json")) {
            return JsonMapper.builder().build().readValue(in, Fixture.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Document text with LF line endings, whatever the checkout's autocrlf setting. */
    public static String documentText(Case testCase) {
        try (InputStream in = TransformGoldenCases.class.getResourceAsStream(ROOT + testCase.doc())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
