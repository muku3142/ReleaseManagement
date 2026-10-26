package com.evision.releasemgmt.application.assistant;

import com.evision.releasemgmt.application.AiRevisionService;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.config.AssistantProperties.DocumentAssist;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.InvalidAssistRequestException;
import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.domain.assistant.TransformTooLargeException;
import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.RevisionSupersededException;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagTransformPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document assist between the web layer and the model. The model input is always the stored latest revision and the
 * instruction is the configured text (Clean up, Summarize) or the user's text (Custom); nothing the client sends as
 * document text reaches the model. Logs metadata only (SAFE-03).
 */
@Service
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class AssistantTransformService {

    private static final Logger log = LoggerFactory.getLogger(AssistantTransformService.class);
    private static final String NUL = "\u0000";

    // sections is null for a single-request transform (Summarize); otherwise maxChars bounds each section.
    public record Prepared(UUID releaseId, TemplateDocumentType documentType, int sourceRevision, TransformMode mode,
                           String markdown, String instruction, int documentChars, int maxChars,
                           List<DocumentSections.Part> sections) {

        // SAFE-03: markdown and instruction are release or user content, so print sizes only.
        @Override
        public String toString() {
            return "Prepared[releaseId=" + releaseId + ", documentType=" + documentType + ", mode=" + mode
                    + ", sourceRevision=" + sourceRevision + ", documentChars=" + documentChars
                    + ", maxChars=" + maxChars + ", sections=" + sectionCount() + "]";
        }

        int sectionCount() {
            return sections == null ? 1 : (int) DocumentSections.transformCount(sections);
        }
    }

    public record Limits(int numCtx, double charsPerToken, int systemTokens, int templateSlack,
                         int instructionMaxChars, Map<TransformMode, Map<TemplateDocumentType, Integer>> presetMaxChars) {
    }

    private final DocumentSnapshotRepository snapshots;
    private final AiRevisionService aiRevisions;
    private final RagTransformPort transformPort;
    private final DocumentAssist config;
    private final TransformSizeEstimator estimator;

    public AssistantTransformService(DocumentSnapshotRepository snapshots, AiRevisionService aiRevisions,
                                     RagTransformPort transformPort, AssistantProperties properties) {
        this.snapshots = snapshots;
        this.aiRevisions = aiRevisions;
        this.transformPort = transformPort;
        this.config = properties.documentAssist();
        this.estimator = new TransformSizeEstimator(config);
    }

    public Prepared prepare(UUID releaseId, TemplateDocumentType type, int sourceRevision, TransformMode mode,
                            String customInstruction) {
        DocumentSnapshot latest = snapshots.findLatest(releaseId, type).orElseThrow(DocumentNotFoundException::new);
        if (latest.revision() != sourceRevision) {
            throw new RevisionSupersededException();
        }
        String instruction = resolveInstruction(type, mode, customInstruction);
        String markdown = latest.markdown();
        int maxChars = estimator.maxChars(instruction.length());
        if (mode == TransformMode.SUMMARIZE) {
            // A summary needs the whole document at once, so it is never sectioned.
            if (markdown.length() > maxChars) {
                throw tooLargePreflight(releaseId, type, mode, instruction, markdown.length(), maxChars,
                        new TransformTooLargeException(markdown.length(), maxChars));
            }
            return new Prepared(releaseId, type, sourceRevision, mode, markdown, instruction, markdown.length(),
                    maxChars, null);
        }
        try {
            return new Prepared(releaseId, type, sourceRevision, mode, markdown, instruction, markdown.length(),
                    maxChars, DocumentSections.split(markdown, maxChars));
        } catch (TransformTooLargeException e) {
            throw tooLargePreflight(releaseId, type, mode, instruction, markdown.length(), maxChars, e);
        }
    }

    private TransformTooLargeException tooLargePreflight(UUID releaseId, TemplateDocumentType type, TransformMode mode,
                                                         String instruction, int documentChars, int maxChars,
                                                         TransformTooLargeException e) {
        logTransform(releaseId, type, mode, instruction.length(), documentChars, maxChars, 0,
                "TOO_LARGE_PREFLIGHT", null, null, null, 0);
        return e;
    }

    public TransformOutcome run(Prepared prepared, RagChatPort.ChatListener listener) {
        long started = System.nanoTime();
        TransformOutcome outcome;
        try {
            outcome = prepared.sections() == null
                    ? transformPort.transform(prepared.markdown(), prepared.instruction(), listener)
                    : runSections(prepared, listener);
        } catch (ClientGoneException e) {
            logRun(prepared, "CLIENT_GONE", null, null, null, started);
            throw e;
        }
        if (outcome instanceof TransformOutcome.TooLarge tooLarge && tooLarge.maxChars() == null) {
            // rag answered 413 after a passing pre-flight (estimator drift); the UI still needs both numbers
            outcome = new TransformOutcome.TooLarge(prepared.maxChars());
        }
        switch (outcome) {
            case TransformOutcome.Done done -> logRun(prepared,
                    done.truncated() || !"stop".equals(done.finishReason()) ? "TRUNCATED" : "OK",
                    done.finishReason(), done.promptTokens(), done.completionTokens(), started);
            case TransformOutcome.TooLarge ignored -> logRun(prepared, "TOO_LARGE_SERVER", null, null, null, started);
            case TransformOutcome.Failed failed ->
                    logRun(prepared, failed.reason().name(), null, null, null, started);
        }
        return outcome;
    }

    // Sections run one at a time (rag-service allows one generation per client) and stream into the same listener,
    // so the browser receives the whole document in order. The first section that does not finish ends the run; if a
    // section already finished, the original text of the stopped section and the rest is sent so the browser holds
    // the whole document.
    private TransformOutcome runSections(Prepared prepared, RagChatPort.ChatListener listener) {
        List<DocumentSections.Part> parts = prepared.sections();
        int sections = prepared.sectionCount();
        int section = 0;
        String finishReason = "stop";
        boolean truncated = false;
        int promptTokens = 0;
        int completionTokens = 0;
        int sent = 0;
        int completed = 0;
        for (int i = 0; i < parts.size(); i++) {
            DocumentSections.Part part = parts.get(i);
            int keep = sent;
            try {
                if (!part.transform()) {
                    listener.token(part.text());
                    sent += part.text().length();
                    continue;
                }
                section++;
                if (sections > 1) {
                    listener.sectionStarted(section, sections);
                }
                TrimmingListener trimming = new TrimmingListener(listener);
                TransformOutcome outcome = transformPort.transform(part.text(), prepared.instruction(), trimming);
                if (!(outcome instanceof TransformOutcome.Done done)) {
                    fillRemainder(listener, parts, i, keep, completed);
                    return outcome;
                }
                sent += trimming.emitted;
                completed++;
                if (!"stop".equals(done.finishReason())) {
                    finishReason = done.finishReason();
                }
                truncated |= done.truncated();
                promptTokens += done.promptTokens() == null ? 0 : done.promptTokens();
                completionTokens += done.completionTokens() == null ? 0 : done.completionTokens();
            } catch (ClientGoneException e) {
                throw e;
            } catch (RuntimeException e) {
                fillRemainder(listener, parts, i, keep, completed);
                throw e;
            }
        }
        return new TransformOutcome.Done(finishReason, truncated, promptTokens, completionTokens);
    }

    // With no finished section the fill would only reproduce the original document, so nothing is sent.
    private static void fillRemainder(RagChatPort.ChatListener listener, List<DocumentSections.Part> parts,
                                      int stoppedAt, int keep, int completed) {
        if (completed == 0) {
            return;
        }
        StringBuilder rest = new StringBuilder();
        for (DocumentSections.Part part : parts.subList(stoppedAt, parts.size())) {
            rest.append(part.text());
        }
        listener.remainder(keep, rest.toString());
    }

    /**
     * Drops the blank lines a model puts around its answer, so each section keeps the document's own spacing.
     * Trailing whitespace is held back until more text arrives and discarded at the end of the section.
     */
    private static final class TrimmingListener implements RagChatPort.ChatListener {

        private final RagChatPort.ChatListener delegate;
        private boolean started;
        private String pending = "";
        private int emitted;

        TrimmingListener(RagChatPort.ChatListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void token(String text) {
            String combined = pending + (started ? text : text.stripLeading());
            if (combined.isEmpty()) {
                return;
            }
            started = true;
            int end = combined.stripTrailing().length();
            pending = combined.substring(end);
            if (end > 0) {
                delegate.token(combined.substring(0, end));
                emitted += end;
            }
        }

        @Override
        public void roundDiscarded() {
            delegate.roundDiscarded();
        }

        @Override
        public void heartbeat() {
            delegate.heartbeat();
        }
    }

    // The truncation Save block is UI-only: the request carries no finishReason, and the single trusted user can
    // edit the buffer freely anyway.
    public DocumentSnapshot saveAiRevision(UUID releaseId, TemplateDocumentType type, int sourceRevision,
                                           TransformMode mode, String customInstruction, String markdown,
                                           String actor) {
        if (markdown == null || markdown.contains(NUL)) {
            throw new InvalidAssistRequestException("The text contains an invalid character.");
        }
        String instruction = resolveInstruction(type, mode, customInstruction);
        AiMode aiMode = switch (mode) {
            case CLEAN_UP -> AiMode.CLEAN_UP;
            case SUMMARIZE -> AiMode.SUMMARIZE;
            case CUSTOM -> AiMode.CUSTOM;
        };
        DocumentSnapshot saved = aiRevisions.save(releaseId, type, sourceRevision, aiMode, instruction, markdown, actor);
        log.info("assistant ai-revision releaseId={} documentType={} mode={} sourceRevision={} revision={}"
                        + " editedChars={} outcome=SAVED",
                releaseId, type, mode, sourceRevision, saved.revision(), markdown.length());
        return saved;
    }

    public Limits limits() {
        Map<TransformMode, Map<TemplateDocumentType, Integer>> preset = new EnumMap<>(TransformMode.class);
        for (TransformMode mode : List.of(TransformMode.CLEAN_UP, TransformMode.SUMMARIZE)) {
            Map<TemplateDocumentType, Integer> byType = new EnumMap<>(TemplateDocumentType.class);
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                byType.put(type, estimator.maxChars(config.instruction(mode, type).length()));
            }
            preset.put(mode, byType);
        }
        return new Limits(config.numCtx(), config.charsPerToken(), config.systemTokens(),
                TransformSizeEstimator.TEMPLATE_SLACK, config.instructionMaxChars(), preset);
    }

    private String resolveInstruction(TemplateDocumentType type, TransformMode mode, String customInstruction) {
        if (mode != TransformMode.CUSTOM) {
            return config.instruction(mode, type);
        }
        if (customInstruction == null || customInstruction.isBlank()) {
            throw new InvalidAssistRequestException("A custom instruction is required.");
        }
        if (customInstruction.length() > config.instructionMaxChars()) {
            throw new InvalidAssistRequestException("The instruction is too long.");
        }
        if (customInstruction.contains(NUL)) {
            throw new InvalidAssistRequestException("The text contains an invalid character.");
        }
        return customInstruction;
    }

    private void logRun(Prepared prepared, String outcome, String finishReason, Integer promptTokens,
                        Integer completionTokens, long startedNanos) {
        logTransform(prepared.releaseId(), prepared.documentType(), prepared.mode(), prepared.instruction().length(),
                prepared.documentChars(), prepared.maxChars(), prepared.sectionCount(), outcome, finishReason, promptTokens, completionTokens,
                (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private void logTransform(UUID releaseId, TemplateDocumentType type, TransformMode mode, int instructionChars,
                              int documentChars, int limitChars, int sections, String outcome, String finishReason,
                              Integer promptTokens, Integer completionTokens, long totalMs) {
        log.info("assistant transform releaseId={} documentType={} mode={} instructionSource={} instructionChars={}"
                        + " documentChars={} limitChars={} sections={} outcome={} finishReason={} promptTokens={}"
                        + " completionTokens={} totalMs={}",
                releaseId, type, mode, mode == TransformMode.CUSTOM ? "CUSTOM" : "CONFIG", instructionChars,
                documentChars, limitChars, sections, outcome, finishReason, promptTokens, completionTokens, totalMs);
    }
}
