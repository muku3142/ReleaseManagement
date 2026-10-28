package com.evision.releasemgmt.config;

import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantPropertiesTest {

    private static final String SECRET = "canary-secret-123";
    private static final AssistantProperties.DocumentAssist ASSIST = assist(4096, 3.0, 152, "rb");
    private static final String SCOPED = "The question is about release \"{release}\". {question}";
    private static final String TOOL_ERROR = "I couldn't look that up: {error}.";
    private static final Map<String, String> TOOLS = Map.of(
            "dashboard", "Dashboard figures (counts, median lead times) and ladder stages with release counts. scope: active or all.",
            "deployments", "Deployments: promotion history of `release`, else recent ones by `stage` over `days` (default 30).",
            "documents", "Document readiness: per type for `release`, else releases not up to date.",
            "releases", "Releases: list, or with `release` its status, dates, ticket counts and current stage.",
            "tickets", "Ticket facts and churn: by `key` across releases, or by `release` (outliers first).");
    private static final List<String> UNSCOPED = List.of(
            "What shipped to Production last week?",
            "Which releases are not ready to ship?",
            "Which documents are out of date?");
    private static final List<String> RELEASE = List.of(
            "Is {release} ready for Production?",
            "What changed in {release}?",
            "Which tickets in {release} have no linked code?");

    private static AssistantProperties props(boolean enabled, String baseUrl, String apiKey) {
        return build(enabled, baseUrl, apiKey, new AssistantProperties.Prompts(SCOPED, TOOLS, TOOL_ERROR),
                new AssistantProperties.Starters(UNSCOPED, RELEASE), 20, Duration.ofMinutes(5));
    }

    private static AssistantProperties valid(AssistantProperties.Prompts prompts, AssistantProperties.Starters starters,
                                             int historyMaxTurns) {
        return build(true, "http://rag-service:8081", SECRET, prompts, starters, historyMaxTurns, Duration.ofMinutes(5));
    }

    private static AssistantProperties build(boolean enabled, String baseUrl, String apiKey,
                                             AssistantProperties.Prompts prompts, AssistantProperties.Starters starters,
                                             int historyMaxTurns, Duration streamDeadline) {
        AssistantProperties.Timeouts timeouts = new AssistantProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new AssistantProperties(enabled, baseUrl, apiKey, "nomic-embed-text",
                new AssistantProperties.Sweep(Duration.ofSeconds(10), Duration.ofMinutes(30)),
                timeouts, timeouts, 4, 6000, prompts, starters, historyMaxTurns, streamDeadline, ASSIST);
    }

    private static AssistantProperties withAssist(AssistantProperties.DocumentAssist assist) {
        AssistantProperties.Timeouts timeouts = new AssistantProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new AssistantProperties(true, "http://rag-service:8081", SECRET, "nomic-embed-text",
                new AssistantProperties.Sweep(Duration.ofSeconds(10), Duration.ofMinutes(30)), timeouts, timeouts, 4, 6000,
                new AssistantProperties.Prompts(SCOPED, TOOLS, TOOL_ERROR), new AssistantProperties.Starters(UNSCOPED, RELEASE), 20,
                Duration.ofMinutes(5), assist);
    }

    private static AssistantProperties.DocumentAssist assist(int numCtx, double cpt, int systemTokens, String cleanUpRunbook) {
        AssistantProperties.ByType cleanUp = new AssistantProperties.ByType("rn", cleanUpRunbook, "ap");
        AssistantProperties.ByType summarize = new AssistantProperties.ByType("rn", "rb", "ap");
        return new AssistantProperties.DocumentAssist(numCtx, cpt, systemTokens, 2000,
                new AssistantProperties.Instructions(cleanUp, summarize), java.time.Duration.ofMinutes(20));
    }

    @Test
    void instructionIsLookedUpByModeAndType() {
        AssistantProperties.DocumentAssist assist = new AssistantProperties.DocumentAssist(4096, 3.0, 152, 2000,
                new AssistantProperties.Instructions(new AssistantProperties.ByType("cr", "cb", "ca"),
                        new AssistantProperties.ByType("sr", "sb", "sa")), java.time.Duration.ofMinutes(20));
        assertThat(assist.instruction(TransformMode.CLEAN_UP, TemplateDocumentType.RUNBOOK)).isEqualTo("cb");
        assertThat(assist.instruction(TransformMode.SUMMARIZE, TemplateDocumentType.APPROVAL)).isEqualTo("sa");
        assertThatThrownBy(() -> assist.instruction(TransformMode.CUSTOM, TemplateDocumentType.RUNBOOK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", ""})
    void blankInstructionFailsNamingTheKey(String blank) {
        assertThatThrownBy(() -> withAssist(assist(4096, 3.0, 152, blank)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.document-assist.instructions.clean-up.runbook");
    }

    @Test
    void instructionLongerThanTheLimitFailsAndTheLimitConstructs() {
        assertThatThrownBy(() -> withAssist(assist(4096, 3.0, 152, "x".repeat(2001))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instructions.clean-up.runbook");
        assertThatCode(() -> withAssist(assist(4096, 3.0, 152, "x".repeat(2000)))).doesNotThrowAnyException();
    }

    @Test
    void nonPositiveMirrorsFailWhenEnabled() {
        assertThatThrownBy(() -> withAssist(assist(0, 3.0, 152, "x"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> withAssist(assist(4096, 0, 152, "x"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> withAssist(assist(4096, 3.0, 0, "x"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledWithInvalidOrMissingDocumentAssistConstructs() {
        AssistantProperties.Timeouts timeouts = new AssistantProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(5));
        AssistantProperties missing = new AssistantProperties(false, "", "", "m", null, timeouts, timeouts, 4, 6000,
                null, null, 20, null, null);
        assertThat(missing.documentAssist().instruction(TransformMode.CLEAN_UP, TemplateDocumentType.RUNBOOK)).isNull();
        assertThatCode(() -> new AssistantProperties(false, "", "", "m", null, timeouts, timeouts, 4, 6000, null, null, 20,
                null, assist(0, 0, 0, " "))).doesNotThrowAnyException();
    }

    @Test
    void toStringPrintsNoInstructionText() {
        assertThat(props(true, "http://rag-service:8081", SECRET).toString())
                .contains("documentAssist=numCtx=4096,instructions=6")
                .doesNotContain("Clean up this");
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/main/resources/application.yml", "src/test/resources/application.yml"})
    void shippedInstructionsFollowTheTransformRules(String yml) throws IOException {
        AssistantProperties.DocumentAssist bound = bind(yml).documentAssist();
        assertThat(bound.numCtx()).isEqualTo(4096);
        assertThat(bound.charsPerToken()).isEqualTo(3.0);
        assertThat(bound.systemTokens()).isEqualTo(152);
        assertThat(bound.instructionMaxChars()).isEqualTo(2000);
        for (TransformMode mode : List.of(TransformMode.CLEAN_UP, TransformMode.SUMMARIZE)) {
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                String text = bound.instruction(mode, type);
                assertThat(text).isNotBlank().hasSizeLessThanOrEqualTo(2000).doesNotContain("<<<", ">>>");
                assertThat(text).containsAnyOf("Output Markdown", "Markdown summary");
                if (mode == TransformMode.CLEAN_UP) {
                    assertThat(text).contains("[[unknown: ...]]");
                }
            }
        }
    }

    @Test
    void bothYmlsShipIdenticalInstructionTexts() throws IOException {
        AssistantProperties.Instructions main = bind("src/main/resources/application.yml").documentAssist().instructions();
        AssistantProperties.Instructions test = bind("src/test/resources/application.yml").documentAssist().instructions();
        assertThat(test).isEqualTo(main);
    }

    private static AssistantProperties.Prompts prompts(String scoped, Map<String, String> tools) {
        return new AssistantProperties.Prompts(scoped, tools, TOOL_ERROR);
    }

    private static AssistantProperties.Starters starters(List<String> unscoped, List<String> release) {
        return new AssistantProperties.Starters(unscoped, release);
    }

    @Test
    void enabledWithBlankBaseUrlFailsNamingBothKeys() {
        assertThatThrownBy(() -> props(true, " ", SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.base-url")
                .hasMessageContaining("app.assistant.api-key")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void enabledWithBlankApiKeyFailsNamingBothKeys() {
        assertThatThrownBy(() -> props(true, "http://rag-service:8081", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.base-url")
                .hasMessageContaining("app.assistant.api-key");
    }

    @Test
    void disabledWithBlankApiKeyConstructs() {
        assertThatCode(() -> props(false, "", "")).doesNotThrowAnyException();
    }

    @Test
    void toStringNeverContainsTheApiKey() {
        assertThat(props(true, "http://rag-service:8081", SECRET).toString())
                .doesNotContain(SECRET)
                .contains("***");
    }

    @Test
    void toStringShowsCountsNotPromptOrStarterText() {
        assertThat(props(true, "http://rag-service:8081", SECRET).toString())
                .contains("historyMaxTurns=20", "streamDeadline=PT5M", "toolDescriptions=5", "starters=3/3")
                .doesNotContain("Production")
                .doesNotContain("The question is about");
    }

    @ParameterizedTest
    @ValueSource(strings = {"The question is about {release}.", "No placeholders", "Question: {question}"})
    void scopedQuestionMissingARequiredPlaceholderFails(String template) {
        assertThatThrownBy(() -> valid(prompts(template, TOOLS), starters(UNSCOPED, RELEASE), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.prompts.scoped-question");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"No placeholder", ""})
    void toolErrorAnswerMissingThePlaceholderFails(String template) {
        AssistantProperties.Prompts prompts = new AssistantProperties.Prompts(SCOPED, TOOLS, template);
        assertThatThrownBy(() -> valid(prompts, starters(UNSCOPED, RELEASE), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.assistant.prompts.tool-error-answer must contain {error}");
    }

    @Test
    void blankOrOversizedToolDescriptionFailsNamingTheTool() {
        assertThatThrownBy(() -> valid(prompts(SCOPED, Map.of("releases", " ")), starters(UNSCOPED, RELEASE), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tool-descriptions.releases");
        assertThatThrownBy(() -> valid(prompts(SCOPED, Map.of("tickets", "x".repeat(301))), starters(UNSCOPED, RELEASE), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tool-descriptions.tickets");
    }

    @Test
    void toolDescriptionAtTheLimitConstructs() {
        assertThatCode(() -> valid(prompts(SCOPED, Map.of("tickets", "x".repeat(300))), starters(UNSCOPED, RELEASE), 20))
                .doesNotThrowAnyException();
    }

    @Test
    void tooManyOrTooLongStartersFail() {
        assertThatThrownBy(() -> valid(prompts(SCOPED, TOOLS), starters(List.of("a", "b", "c", "d", "e", "f", "g"), RELEASE), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.starters.unscoped");
        assertThatThrownBy(() -> valid(prompts(SCOPED, TOOLS), starters(UNSCOPED, List.of("x".repeat(201))), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.starters.release");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void historyMaxTurnsOutsideOneToTwentyFails(int turns) {
        assertThatThrownBy(() -> valid(prompts(SCOPED, TOOLS), starters(UNSCOPED, RELEASE), turns))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.history-max-turns");
    }

    @Test
    void nonPositiveStreamDeadlineFails() {
        assertThatThrownBy(() -> build(true, "http://rag-service:8081", SECRET, prompts(SCOPED, TOOLS),
                starters(UNSCOPED, RELEASE), 20, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.assistant.stream-deadline");
    }

    @Test
    void disabledWithInvalidPromptsAndStartersStillConstructs() {
        assertThatCode(() -> build(false, "", "", prompts("nothing", Map.of("x", "")),
                starters(List.of("a", "b", "c", "d", "e", "f", "g"), RELEASE), 0, null))
                .doesNotThrowAnyException();
    }

    @Test
    void nullPromptsAndStartersNormaliseToEmpty() {
        AssistantProperties properties = build(false, "", "", null, null, 20, Duration.ofMinutes(5));
        assertThat(properties.prompts().toolDescriptions()).isEmpty();
        assertThat(properties.starters().unscoped()).isEmpty();
        assertThat(properties.starters().release()).isEmpty();
        assertThat(new AssistantProperties.Prompts(SCOPED, null, TOOL_ERROR).toolDescriptions()).isEmpty();
        assertThat(new AssistantProperties.Starters(null, null).release()).isEmpty();
    }

    @Test
    void mainYmlBindsTheDocumentedDefaults() throws IOException {
        assertDefaults(bind("src/main/resources/application.yml"));
    }

    @Test
    void testYmlBindsTheSameDefaults() throws IOException {
        assertDefaults(bind("src/test/resources/application.yml"));
    }

    private static void assertDefaults(AssistantProperties bound) {
        assertThat(bound.prompts().scopedQuestion()).isEqualTo(SCOPED);
        assertThat(bound.prompts().toolDescriptions()).isEqualTo(TOOLS);
        assertThat(bound.starters().unscoped()).isEqualTo(UNSCOPED);
        assertThat(bound.starters().release()).isEqualTo(RELEASE);
        assertThat(bound.historyMaxTurns()).isEqualTo(20);
        assertThat(bound.streamDeadline()).isEqualTo(Duration.ofMinutes(5));
    }

    private static AssistantProperties bind(String yml) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("yml", new FileSystemResource(yml))) {
            environment.getPropertySources().addLast(source);
        }
        environment.getPropertySources().addLast(new MapPropertySource("empty", Map.of()));
        return Binder.get(environment)
                .bind("app.assistant", AssistantProperties.class)
                .get();
    }
}
