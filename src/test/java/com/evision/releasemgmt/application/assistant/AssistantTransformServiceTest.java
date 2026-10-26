package com.evision.releasemgmt.application.assistant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.application.AiRevisionService;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.domain.assistant.ClientGoneException;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.InvalidAssistRequestException;
import com.evision.releasemgmt.domain.assistant.TransformMode;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.domain.assistant.TransformTooLargeException;
import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.RevisionSupersededException;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagTransformPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantTransformServiceTest {

    private static final TemplateDocumentType RUNBOOK = TemplateDocumentType.RUNBOOK;
    private static final String CLEAN_UP_RUNBOOK = "Clean up the runbook.";
    private static final String SUMMARIZE_RUNBOOK = "Summarize the runbook.";
    private static final String DOC_SENTINEL = "DOCSENTINEL-7731";
    private static final String INSTRUCTION_SENTINEL = "INSTRSENTINEL-4412";
    private static final String RESULT_SENTINEL = "RESULTSENTINEL-9020";

    private final DocumentSnapshotRepository snapshots = mock(DocumentSnapshotRepository.class);
    private final AiRevisionService aiRevisions = mock(AiRevisionService.class);
    private final List<String> transformedTexts = new ArrayList<>();
    private final List<String> transformedInstructions = new ArrayList<>();
    private boolean clientGone;
    private TransformOutcome scripted = new TransformOutcome.Done("stop", false, 100, 50);
    // When set, the fake model streams these tokens for each request instead of the sentinel.
    private java.util.function.Function<String, List<String>> reply;
    private int failOnCall = -1;
    // Streamed by the fake model just before it fails, like a section cut off mid-answer.
    private String partialBeforeFailure;
    private final RagTransformPort port = (text, instruction, listener) -> {
        transformedTexts.add(text);
        transformedInstructions.add(instruction);
        if (clientGone) {
            throw new ClientGoneException();
        }
        if (transformedTexts.size() == failOnCall) {
            if (partialBeforeFailure != null) {
                listener.token(partialBeforeFailure);
            }
            return new TransformOutcome.Failed(FailureReason.DEADLINE, null);
        }
        if (reply == null) {
            listener.token(RESULT_SENTINEL);
        } else {
            reply.apply(text).forEach(listener::token);
        }
        return scripted;
    };
    private final AssistantProperties properties = new AssistantProperties(true, "http://rag", "key", "model",
            null, null, null, 4, 8000,
            new AssistantProperties.Prompts("About \"{release}\". {question}", null, "I could not: {error}"), null, 20, java.time.Duration.ofMinutes(5),
            new AssistantProperties.DocumentAssist(8192, 3.0, 152, 2000, new AssistantProperties.Instructions(
                    new AssistantProperties.ByType("clean rn", CLEAN_UP_RUNBOOK, "clean ap"),
                    new AssistantProperties.ByType("sum rn", SUMMARIZE_RUNBOOK, "sum ap")), java.time.Duration.ofMinutes(20)));
    private final AssistantTransformService service =
            new AssistantTransformService(snapshots, aiRevisions, port, properties);
    private final UUID releaseId = UUID.randomUUID();

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger serviceLogger;

    @BeforeEach
    void captureLogs() {
        serviceLogger = (Logger) LoggerFactory.getLogger(AssistantTransformService.class);
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        serviceLogger.detachAppender(logs);
    }

    @Test
    void prepareResolvesTheStoredMarkdownAndTheConfiguredCleanUpInstruction() {
        stored(3, "# Rev three");

        AssistantTransformService.Prepared prepared =
                service.prepare(releaseId, RUNBOOK, 3, TransformMode.CLEAN_UP, "ignored client text");

        assertThat(prepared.markdown()).isEqualTo("# Rev three");
        assertThat(prepared.instruction()).isEqualTo(CLEAN_UP_RUNBOOK);
        assertThat(prepared.mode()).isEqualTo(TransformMode.CLEAN_UP);
        assertThat(prepared.documentChars()).isEqualTo("# Rev three".length());
        assertThat(prepared.maxChars())
                .isEqualTo(new TransformSizeEstimator(properties.documentAssist()).maxChars(CLEAN_UP_RUNBOOK.length()));
    }

    @Test
    void prepareUsesTheConfiguredSummarizeTextAndIgnoresClientText() {
        stored(1, "# Doc");

        assertThat(service.prepare(releaseId, RUNBOOK, 1, TransformMode.SUMMARIZE, "client text").instruction())
                .isEqualTo(SUMMARIZE_RUNBOOK);
    }

    @Test
    void prepareUsesTheUsersTextForCustom() {
        stored(1, "# Doc");

        assertThat(service.prepare(releaseId, RUNBOOK, 1, TransformMode.CUSTOM, "Shorter please").instruction())
                .isEqualTo("Shorter please");
    }

    @Test
    void prepareRejectsMissingBlankOverlongAndNulCustomInstructions() {
        stored(1, "# Doc");

        for (String bad : new String[] {null, "  ", "x".repeat(2001), "a\u0000b"}) {
            assertThatThrownBy(() -> service.prepare(releaseId, RUNBOOK, 1, TransformMode.CUSTOM, bad))
                    .isInstanceOf(InvalidAssistRequestException.class);
        }
    }

    @Test
    void prepareRefusesASupersededSourceRevisionAndAMissingDocument() {
        stored(4, "# Doc");
        assertThatThrownBy(() -> service.prepare(releaseId, RUNBOOK, 3, TransformMode.CLEAN_UP, null))
                .isInstanceOf(RevisionSupersededException.class);

        when(snapshots.findLatest(releaseId, TemplateDocumentType.APPROVAL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.prepare(releaseId, TemplateDocumentType.APPROVAL, 1, TransformMode.CLEAN_UP, null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void prepareRefusesATooLongDocumentBeforeAnyModelCall() {
        String huge = "x".repeat(100_000);
        stored(1, huge);
        int max = new TransformSizeEstimator(properties.documentAssist()).maxChars(CLEAN_UP_RUNBOOK.length());

        assertThatThrownBy(() -> service.prepare(releaseId, RUNBOOK, 1, TransformMode.CLEAN_UP, null))
                .isInstanceOfSatisfying(TransformTooLargeException.class, e -> {
                    assertThat(e.getCharacters()).isEqualTo(100_000);
                    assertThat(e.getMaxCharacters()).isEqualTo(max);
                });
        assertThat(transformedTexts).isEmpty();
        assertThat(lastLog()).contains("outcome=TOO_LARGE_PREFLIGHT");
    }

    @Test
    void runPassesDoneThroughAndLogsOneMetadataLine() {
        stored(1, "Doc text");
        AssistantTransformService.Prepared prepared = service.prepare(releaseId, RUNBOOK, 1, TransformMode.CLEAN_UP, null);
        logs.list.clear();

        TransformOutcome outcome = service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(outcome).isEqualTo(scripted);
        assertThat(transformedTexts).containsExactly("Doc text");
        assertThat(transformedInstructions).containsExactly(CLEAN_UP_RUNBOOK);
        assertThat(logs.list).hasSize(1);
        assertThat(lastLog()).contains("outcome=OK", "finishReason=stop", "promptTokens=100", "completionTokens=50",
                "instructionSource=CONFIG", "mode=CLEAN_UP");
    }

    @Test
    void runLogsTruncatedWhenTheFinishReasonIsNotStop() {
        scripted = new TransformOutcome.Done("length", false, 100, 50);
        AssistantTransformService.Prepared prepared = preparedDoc("Doc text");

        service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(lastLog()).contains("outcome=TRUNCATED", "finishReason=length");
    }

    @Test
    void runFillsInThePreflightLimitWhenRagAnswersTooLargeWithoutOne() {
        scripted = new TransformOutcome.TooLarge(null);
        AssistantTransformService.Prepared prepared = preparedDoc("Doc text");

        TransformOutcome outcome = service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(outcome).isEqualTo(new TransformOutcome.TooLarge(prepared.maxChars()));
        assertThat(lastLog()).contains("outcome=TOO_LARGE_SERVER");
    }

    @Test
    void runPassesFailuresThroughAndLogsTheReason() {
        scripted = new TransformOutcome.Failed(FailureReason.BUSY, 7);
        AssistantTransformService.Prepared prepared = preparedDoc("Doc text");

        assertThat(service.run(prepared, RagChatPort.ChatListener.NONE)).isEqualTo(scripted);
        assertThat(lastLog()).contains("outcome=BUSY");
    }

    @Test
    void runLogsClientGoneAndRethrows() {
        clientGone = true;
        AssistantTransformService.Prepared prepared = preparedDoc("Doc text");

        assertThatThrownBy(() -> service.run(prepared, RagChatPort.ChatListener.NONE))
                .isInstanceOf(ClientGoneException.class);
        assertThat(lastLog()).contains("outcome=CLIENT_GONE");
    }

    @Test
    void transformLogsCarryNoDocumentInstructionOrResultText() {
        stored(1, DOC_SENTINEL);
        AssistantTransformService.Prepared prepared =
                service.prepare(releaseId, RUNBOOK, 1, TransformMode.CUSTOM, INSTRUCTION_SENTINEL);

        service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(prepared.toString()).doesNotContain(DOC_SENTINEL, INSTRUCTION_SENTINEL);
        assertNoSentinelLogged();
    }

    @Test
    void cleanUpSendsEachSectionBodySeparatelyAndCopiesHeadingsVerbatim() {
        reply = text -> List.of("\n", text.toUpperCase(), "\n\n");
        AssistantTransformService.Prepared prepared = preparedDoc("## A (CNV-1)\n\nAlpha body\n\n## B\nBeta body\n");
        Recorder recorder = new Recorder();

        TransformOutcome outcome = service.run(prepared, recorder);

        assertThat(transformedTexts).containsExactly("Alpha body", "Beta body");
        assertThat(transformedInstructions).containsOnly(CLEAN_UP_RUNBOOK);
        assertThat(recorder.text()).isEqualTo("## A (CNV-1)\n\nALPHA BODY\n\n## B\nBETA BODY\n");
        assertThat(recorder.progress).containsExactly("1/2", "2/2");
        assertThat(outcome).isEqualTo(new TransformOutcome.Done("stop", false, 200, 100));
        assertThat(lastLog()).contains("sections=2", "outcome=OK");
    }

    @Test
    void whitespaceInsideASectionAnswerIsKeptAndOnlyTheEdgesAreDropped() {
        reply = text -> List.of("  \n", "Line one", "\n", "\n", "Line two", "\n\n");
        AssistantTransformService.Prepared prepared = preparedDoc("## A\nBody\n");
        Recorder recorder = new Recorder();

        service.run(prepared, recorder);

        assertThat(recorder.text()).isEqualTo("## A\nLine one\n\nLine two\n");
    }

    @Test
    void aFailedSectionEndsTheRunWithoutSendingTheRest() {
        failOnCall = 2;
        AssistantTransformService.Prepared prepared = preparedDoc("## A\nOne\n## B\nTwo\n## C\nThree\n");

        TransformOutcome outcome = service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(outcome).isEqualTo(new TransformOutcome.Failed(FailureReason.DEADLINE, null));
        assertThat(transformedTexts).containsExactly("One", "Two");
        assertThat(lastLog()).contains("sections=3", "outcome=DEADLINE");
    }

    @Test
    void aFailedSectionAfterAFinishedOneGetsTheOriginalRestSoTheBufferIsTheWholeDocument() {
        failOnCall = 2;
        partialBeforeFailure = "TW";
        AssistantTransformService.Prepared prepared = preparedDoc("## A\nOne\n## B\nTwo\n## C\nThree\n");
        Recorder recorder = new Recorder();

        TransformOutcome outcome = service.run(prepared, recorder);

        assertThat(outcome).isEqualTo(new TransformOutcome.Failed(FailureReason.DEADLINE, null));
        assertThat(recorder.remainders).hasSize(1);
        assertThat(recorder.text()).contains("TW");
        assertThat(recorder.applyRemainder())
                .isEqualTo("## A\n" + RESULT_SENTINEL + "\n## B\nTwo\n## C\nThree\n");
        assertThat(transformedTexts).containsExactly("One", "Two");
    }

    @Test
    void anExceptionWhileASectionStreamsFillsTheRestAndIsRethrown() {
        AssistantTransformService.Prepared prepared = preparedDoc("## A\nOne\n## B\nTwo\n## C\nThree\n");
        Recorder recorder = new Recorder();
        recorder.failOnToken = 5;

        assertThatThrownBy(() -> service.run(prepared, recorder)).isInstanceOf(IllegalStateException.class);

        assertThat(recorder.remainders).hasSize(1);
        assertThat(recorder.applyRemainder())
                .isEqualTo("## A\n" + RESULT_SENTINEL + "\n## B\nTwo\n## C\nThree\n");
    }

    @Test
    void aFailureInTheFirstSectionSendsNoRemainder() {
        failOnCall = 1;
        Recorder recorder = new Recorder();

        service.run(preparedDoc("## A\nOne\n## B\nTwo\n"), recorder);

        assertThat(recorder.remainders).isEmpty();
    }

    @Test
    void aClientThatWentAwayGetsNoRemainder() {
        Recorder recorder = new Recorder();
        recorder.goneOnToken = 5;

        assertThatThrownBy(() -> service.run(preparedDoc("## A\nOne\n## B\nTwo\n"), recorder))
                .isInstanceOf(ClientGoneException.class);

        assertThat(recorder.remainders).isEmpty();
    }

    @Test
    void aStoppedSummarizeSendsNoRemainder() {
        failOnCall = 1;
        stored(1, "## A\nOne\n## B\nTwo\n");
        Recorder recorder = new Recorder();

        service.run(service.prepare(releaseId, RUNBOOK, 1, TransformMode.SUMMARIZE, null), recorder);

        assertThat(recorder.remainders).isEmpty();
    }

    @Test
    void aTruncatedSectionMarksTheWholeRunTruncated() {
        scripted = new TransformOutcome.Done("length", true, 10, 5);
        AssistantTransformService.Prepared prepared = preparedDoc("## A\nOne\n## B\nTwo\n");

        TransformOutcome outcome = service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(outcome).isEqualTo(new TransformOutcome.Done("length", true, 20, 10));
        assertThat(transformedTexts).containsExactly("One", "Two");
    }

    @Test
    void cleanUpAcceptsADocumentLargerThanOneRequestWhenEverySectionFits() {
        int max = new TransformSizeEstimator(properties.documentAssist()).maxChars(CLEAN_UP_RUNBOOK.length());
        String section = "## Ticket\n\n" + "word ".repeat(max / 10) + "\n\n";
        String document = section.repeat(5);
        stored(1, document);

        AssistantTransformService.Prepared prepared =
                service.prepare(releaseId, RUNBOOK, 1, TransformMode.CLEAN_UP, null);

        assertThat(document.length()).isGreaterThan(max);
        assertThat(prepared.sectionCount()).isEqualTo(5);
    }

    @Test
    void summarizeStaysOneRequestOverTheWholeDocument() {
        String markdown = "## A\nOne\n## B\nTwo\n";
        stored(1, markdown);
        AssistantTransformService.Prepared prepared =
                service.prepare(releaseId, RUNBOOK, 1, TransformMode.SUMMARIZE, null);

        service.run(prepared, RagChatPort.ChatListener.NONE);

        assertThat(prepared.sections()).isNull();
        assertThat(transformedTexts).containsExactly(markdown);
    }

    @Test
    void summarizeStillRefusesADocumentOverTheSingleRequestLimit() {
        stored(1, "## A\n" + "word ".repeat(20_000));

        assertThatThrownBy(() -> service.prepare(releaseId, RUNBOOK, 1, TransformMode.SUMMARIZE, null))
                .isInstanceOf(TransformTooLargeException.class);
        assertThat(transformedTexts).isEmpty();
    }

    private static final class Recorder implements RagChatPort.ChatListener {

        private final StringBuilder text = new StringBuilder();
        private final List<String> progress = new ArrayList<>();
        private final List<String> remainders = new ArrayList<>();
        private int keep;
        private int tokens;
        private int failOnToken = -1;
        private int goneOnToken = -1;

        @Override
        public void token(String token) {
            tokens++;
            if (tokens == failOnToken) {
                throw new IllegalStateException("stream deadline stand-in");
            }
            if (tokens == goneOnToken) {
                throw new ClientGoneException();
            }
            text.append(token);
        }

        @Override
        public void remainder(int keep, String rest) {
            this.keep = keep;
            remainders.add(rest);
        }

        String applyRemainder() {
            return text.substring(0, keep) + remainders.get(0);
        }

        @Override
        public void roundDiscarded() {
        }

        @Override
        public void sectionStarted(int section, int sections) {
            progress.add(section + "/" + sections);
        }

        String text() {
            return text.toString();
        }
    }

    @Test
    void saveRecordsTheConfiguredCleanUpTextNotTheClients() {
        DocumentSnapshot saved = savedAs(5);
        when(aiRevisions.save(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(saved);

        DocumentSnapshot result = service.saveAiRevision(releaseId, TemplateDocumentType.APPROVAL, 2,
                TransformMode.CLEAN_UP, "client text", "# Edited", "acct");

        assertThat(result).isSameAs(saved);
        verify(aiRevisions).save(releaseId, TemplateDocumentType.APPROVAL, 2, AiMode.CLEAN_UP, "clean ap",
                "# Edited", "acct");
        assertThat(lastLog()).contains("outcome=SAVED", "revision=5", "editedChars=8");
    }

    @Test
    void saveRecordsTheUsersTextForCustom() {
        when(aiRevisions.save(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(savedAs(3));

        service.saveAiRevision(releaseId, RUNBOOK, 2, TransformMode.CUSTOM, "Rewrite for executives", "# E", "acct");

        verify(aiRevisions).save(releaseId, RUNBOOK, 2, AiMode.CUSTOM, "Rewrite for executives", "# E", "acct");
    }

    @Test
    void saveRejectsABlankCustomInstructionWithoutSaving() {
        assertThatThrownBy(() -> service.saveAiRevision(releaseId, RUNBOOK, 2, TransformMode.CUSTOM, " ", "# E", "a"))
                .isInstanceOf(InvalidAssistRequestException.class);
        verify(aiRevisions, never()).save(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void saveSummarizeStoresTheConfiguredInstruction() {
        when(aiRevisions.save(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(savedAs(3));

        service.saveAiRevision(releaseId, RUNBOOK, 2, TransformMode.SUMMARIZE, "ignored", "# E", "a");

        verify(aiRevisions).save(releaseId, RUNBOOK, 2, AiMode.SUMMARIZE, SUMMARIZE_RUNBOOK, "# E", "a");
        assertThat(lastLog()).contains("outcome=SAVED");
    }

    @Test
    void saveRejectsNulInTheMarkdown() {
        assertThatThrownBy(() -> service.saveAiRevision(releaseId, RUNBOOK, 2, TransformMode.CLEAN_UP, null, "a\u0000b", "a"))
                .isInstanceOf(InvalidAssistRequestException.class);
        verify(aiRevisions, never()).save(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void savePropagatesASupersededRevision() {
        when(aiRevisions.save(eq(releaseId), eq(RUNBOOK), eq(2), any(), any(), any(), any()))
                .thenThrow(new RevisionSupersededException());

        assertThatThrownBy(() -> service.saveAiRevision(releaseId, RUNBOOK, 2, TransformMode.CLEAN_UP, null, "# E", "a"))
                .isInstanceOf(RevisionSupersededException.class);
    }

    @Test
    void saveLogsCarryNoMarkdownOrInstructionText() {
        when(aiRevisions.save(any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(savedAs(3));

        service.saveAiRevision(releaseId, RUNBOOK, 2, TransformMode.CUSTOM, INSTRUCTION_SENTINEL, DOC_SENTINEL, "a");

        assertNoSentinelLogged();
    }

    @Test
    void limitsExposeTheConfiguredNumbersAndPerPresetMaxima() {
        AssistantTransformService.Limits limits = service.limits();
        TransformSizeEstimator estimator = new TransformSizeEstimator(properties.documentAssist());

        assertThat(limits.numCtx()).isEqualTo(8192);
        assertThat(limits.instructionMaxChars()).isEqualTo(2000);
        assertThat(limits.templateSlack()).isEqualTo(TransformSizeEstimator.TEMPLATE_SLACK);
        assertThat(limits.presetMaxChars()).containsOnlyKeys(TransformMode.CLEAN_UP, TransformMode.SUMMARIZE);
        assertThat(limits.presetMaxChars().get(TransformMode.CLEAN_UP).get(RUNBOOK))
                .isEqualTo(estimator.maxChars(CLEAN_UP_RUNBOOK.length()));
        assertThat(limits.presetMaxChars().get(TransformMode.SUMMARIZE).get(RUNBOOK))
                .isEqualTo(estimator.maxChars(SUMMARIZE_RUNBOOK.length()));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private AssistantTransformService.Prepared preparedDoc(String markdown) {
        stored(1, markdown);
        AssistantTransformService.Prepared prepared = service.prepare(releaseId, RUNBOOK, 1, TransformMode.CLEAN_UP, null);
        logs.list.clear();
        return prepared;
    }

    private void stored(int revision, String markdown) {
        when(snapshots.findLatest(releaseId, RUNBOOK)).thenReturn(Optional.of(snapshot(RUNBOOK, revision, markdown)));
    }

    private DocumentSnapshot savedAs(int revision) {
        return snapshot(RUNBOOK, revision, "# saved");
    }

    private DocumentSnapshot snapshot(TemplateDocumentType type, int revision, String markdown) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, type, revision, markdown, UUID.randomUUID(),
                "Template", "body", new ChromeFields(true, "Cover", "Sub", "Header", "Footer", true, 40),
                Instant.parse("2026-08-01T00:00:00Z"), "actor", null);
    }

    private String lastLog() {
        return logs.list.get(logs.list.size() - 1).getFormattedMessage();
    }

    private void assertNoSentinelLogged() {
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(DOC_SENTINEL, INSTRUCTION_SENTINEL, RESULT_SENTINEL);
        }
    }
}
