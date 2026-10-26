package com.evision.releasemgmt.config;

import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties("app.assistant")
public record AssistantProperties(boolean enabled, String baseUrl, String apiKey, String embeddingModel,
                                  Sweep sweep, Timeouts index, Timeouts chat, int maxRounds, int toolResultMaxChars,
                                  Prompts prompts, Starters starters, int historyMaxTurns, Duration streamDeadline,
                                  DocumentAssist documentAssist) {

    // Matches the AssistantTool description contract.
    static final int TOOL_DESCRIPTION_MAX_CHARS = 300;
    static final int STARTERS_MAX = 6;
    static final int STARTER_MAX_CHARS = 200;
    // rag-service rag.chat.history-max-turns rejects more than 20 turns with 400.
    static final int HISTORY_MAX_TURNS_LIMIT = 20;

    public record Sweep(Duration initialDelay, Duration interval) {
    }

    public record Timeouts(Duration connectTimeout, Duration readTimeout) {
    }

    public record Prompts(String scopedQuestion, Map<String, String> toolDescriptions, String toolErrorAnswer) {
        public Prompts {
            toolDescriptions = toolDescriptions == null ? Map.of() : Map.copyOf(toolDescriptions);
        }
    }

    public record Starters(List<String> unscoped, List<String> release) {
        public Starters {
            unscoped = unscoped == null ? List.of() : List.copyOf(unscoped);
            release = release == null ? List.of() : List.copyOf(release);
        }
    }

    public record ByType(String releaseNotes, String runbook, String approval) {
        String forType(TemplateDocumentType type) {
            return switch (type) {
                case RELEASE_NOTES -> releaseNotes;
                case RUNBOOK -> runbook;
                case APPROVAL -> approval;
            };
        }
    }

    public record Instructions(ByType cleanUp, ByType summarize) {
        public Instructions {
            cleanUp = cleanUp == null ? new ByType(null, null, null) : cleanUp;
            summarize = summarize == null ? new ByType(null, null, null) : summarize;
        }
    }

    // numCtx, charsPerToken, systemTokens and instructionMaxChars mirror rag-service settings it does not expose.
    // streamDeadline bounds a whole sectioned transform; each section is bounded by rag-service's own deadline.
    public record DocumentAssist(int numCtx, double charsPerToken, int systemTokens, int instructionMaxChars,
                                 Instructions instructions, Duration streamDeadline) {
        public DocumentAssist {
            instructions = instructions == null ? new Instructions(null, null) : instructions;
        }

        public String instruction(TransformMode mode, TemplateDocumentType type) {
            return switch (mode) {
                case CLEAN_UP -> instructions.cleanUp().forType(type);
                case SUMMARIZE -> instructions.summarize().forType(type);
                case CUSTOM -> throw new IllegalArgumentException("Custom instructions come from the user");
            };
        }
    }

    public AssistantProperties {
        prompts = prompts == null ? new Prompts(null, null, null) : prompts;
        starters = starters == null ? new Starters(null, null) : starters;
        documentAssist = documentAssist == null ? new DocumentAssist(0, 0, 0, 0, null, null) : documentAssist;
        if (enabled) {
            if (isBlank(baseUrl) || isBlank(apiKey)) {
                throw new IllegalStateException(
                        "app.assistant.enabled=true requires app.assistant.base-url and app.assistant.api-key");
            }
            validatePrompts(prompts);
            validateStarters("unscoped", starters.unscoped());
            validateStarters("release", starters.release());
            if (historyMaxTurns < 1 || historyMaxTurns > HISTORY_MAX_TURNS_LIMIT) {
                throw new IllegalStateException(
                        "app.assistant.history-max-turns must be between 1 and " + HISTORY_MAX_TURNS_LIMIT);
            }
            if (streamDeadline == null || streamDeadline.isZero() || streamDeadline.isNegative()) {
                throw new IllegalStateException("app.assistant.stream-deadline must be a positive duration");
            }
            validateDocumentAssist(documentAssist);
        }
    }

    private static void validateDocumentAssist(DocumentAssist assist) {
        if (assist.numCtx() <= 0 || assist.charsPerToken() <= 0 || assist.systemTokens() <= 0
                || assist.instructionMaxChars() <= 0) {
            throw new IllegalStateException("app.assistant.document-assist num-ctx, chars-per-token, system-tokens"
                    + " and instruction-max-chars must be positive");
        }
        Duration deadline = assist.streamDeadline();
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalStateException("app.assistant.document-assist.stream-deadline must be a positive duration");
        }
        for (TransformMode mode : List.of(TransformMode.CLEAN_UP, TransformMode.SUMMARIZE)) {
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                String text = assist.instruction(mode, type);
                if (isBlank(text) || text.length() > assist.instructionMaxChars()) {
                    throw new IllegalStateException("app.assistant.document-assist.instructions."
                            + (mode == TransformMode.CLEAN_UP ? "clean-up." : "summarize.")
                            + type.name().toLowerCase(Locale.ROOT).replace('_', '-')
                            + " must be 1 to " + assist.instructionMaxChars() + " characters");
                }
            }
        }
    }

    private static void validatePrompts(Prompts prompts) {
        String template = prompts.scopedQuestion();
        if (template == null || !template.contains("{release}")) {
            throw new IllegalStateException("app.assistant.prompts.scoped-question must contain {release}");
        }
        if (!template.contains("{question}")) {
            throw new IllegalStateException("app.assistant.prompts.scoped-question must contain {question}");
        }
        if (prompts.toolErrorAnswer() == null || !prompts.toolErrorAnswer().contains("{error}")) {
            throw new IllegalStateException("app.assistant.prompts.tool-error-answer must contain {error}");
        }
        prompts.toolDescriptions().forEach((tool, description) -> {
            if (isBlank(description) || description.length() > TOOL_DESCRIPTION_MAX_CHARS) {
                throw new IllegalStateException("app.assistant.prompts.tool-descriptions." + tool
                        + " must be 1 to " + TOOL_DESCRIPTION_MAX_CHARS + " characters");
            }
        });
    }

    private static void validateStarters(String kind, List<String> starters) {
        if (starters.size() > STARTERS_MAX) {
            throw new IllegalStateException(
                    "app.assistant.starters." + kind + " allows at most " + STARTERS_MAX + " entries");
        }
        if (starters.stream().anyMatch(s -> isBlank(s) || s.length() > STARTER_MAX_CHARS)) {
            throw new IllegalStateException("app.assistant.starters." + kind
                    + " entries must be 1 to " + STARTER_MAX_CHARS + " characters");
        }
    }

    // The key is a bearer credential for rag-service; it must never reach logs or actuator output.
    @Override
    public String toString() {
        return "AssistantProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
                + ", apiKey=" + (isBlank(apiKey) ? "<unset>" : "***")
                + ", embeddingModel=" + embeddingModel + ", sweep=" + sweep + ", index=" + index
                + ", chat=" + chat + ", maxRounds=" + maxRounds + ", toolResultMaxChars=" + toolResultMaxChars
                + ", historyMaxTurns=" + historyMaxTurns + ", streamDeadline=" + streamDeadline
                + ", toolDescriptions=" + prompts.toolDescriptions().size()
                + ", starters=" + starters.unscoped().size() + "/" + starters.release().size()
                + ", documentAssist=numCtx=" + documentAssist.numCtx() + ",instructions=6]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
