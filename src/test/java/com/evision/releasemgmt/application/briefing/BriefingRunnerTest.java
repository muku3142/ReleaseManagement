package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.FieldMetadataService;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.briefing.BriefingQueries;
import com.evision.releasemgmt.domain.briefing.BriefingRunSettings;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingThresholds;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.domain.briefing.DigestInput;
import com.evision.releasemgmt.domain.briefing.DigestRules;
import com.evision.releasemgmt.domain.briefing.WorkCalendar;
import com.evision.releasemgmt.domain.enrichment.StatusTransition;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BriefingRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T06:44:00Z");
    private static final Instant SLOT = Instant.parse("2026-10-26T05:44:00Z");
    private static final JiraAccessContext CONTEXT =
            new JiraAccessContext("https://acme.atlassian.net", "557058:sched", "s@acme.com", "tok");
    private static final BriefingRunSettings SETTINGS = new BriefingRunSettings(BriefingThresholds.DEFAULTS,
            new WorkCalendar(WorkCalendar.CAIRO, WorkCalendar.SUN_TO_THU), 60);
    private static final BriefingFieldIds FIELD_IDS = new BriefingFieldIds("customfield_1", null, "customfield_2", null);

    private static final Product A = new Product(UUID.randomUUID(), "Alpha", null);
    private static final Product B = new Product(UUID.randomUUID(), "Beta", null);
    private static final Product C = new Product(UUID.randomUUID(), "Gamma", null);
    private static final Product D = new Product(UUID.randomUUID(), "Delta", Instant.parse("2026-01-01T00:00:00Z"));

    private final ProductRepository products = mock(ProductRepository.class);
    private final FakeRuns runs = new FakeRuns();
    private final BriefingJiraAccess access = mock(BriefingJiraAccess.class);
    private final JiraBriefingPort jira = mock(JiraBriefingPort.class);
    private final JiraIssueHistoryPort history = mock(JiraIssueHistoryPort.class);
    private final FieldMetadataService fields = mock(FieldMetadataService.class);
    private final BriefingHeadlineService headlines = mock(BriefingHeadlineService.class);
    private final BriefingRunner runner = new BriefingRunner(products, runs, access, jira, history, fields, headlines,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void defaults() {
        when(products.findAll()).thenReturn(List.of(A, B, D, C));
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("CNV"), B.id(), List.of("ABC"),
                D.id(), List.of("XYZ")));
        when(access.designatedContext()).thenReturn(Optional.of(CONTEXT));
        when(fields.resolveOptional(CONTEXT, FieldMetadataService.BRIEFING_FIELD_NAMES))
                .thenReturn(Map.of("story points", "customfield_1", "sprint", "customfield_2"));
        when(headlines.enrich(any(), anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AppBriefingRun runOf(List<AppBriefingRun> result, Product product) {
        return result.stream().filter(r -> r.productId().equals(product.id())).findFirst().orElseThrow();
    }

    @Test
    void aSlotRunsUncoveredProductsAndMarksTheRest() {
        List<AppBriefingRun> result = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of("CNV"), SETTINGS);

        assertThat(result).extracting(AppBriefingRun::productId).containsExactly(A.id(), B.id(), C.id());
        assertThat(runOf(result, A).status()).isEqualTo(BriefingStatus.SKIPPED);
        assertThat(runOf(result, A).reason()).isEqualTo(AppBriefingRun.REASON_CLAUDE_COVERED);
        assertThat(runOf(result, A).headline()).isEqualTo("Skipped: Claude covered this slot");
        assertThat(runOf(result, A).attention()).isNull();
        assertThat(runOf(result, C).status()).isEqualTo(BriefingStatus.SKIPPED);
        assertThat(runOf(result, C).reason()).isEqualTo(AppBriefingRun.REASON_NO_JIRA_PROJECT);
        assertThat(runOf(result, C).headline()).isEqualTo("Skipped: no Jira project");
        assertThat(result).allSatisfy(run -> {
            assertThat(run.trigger()).isEqualTo(BriefingTrigger.SLOT);
            assertThat(run.slotAt()).isEqualTo(SLOT);
            assertThat(run.startedAt()).isEqualTo(NOW);
            assertThat(run.finishedAt()).isEqualTo(NOW);
        });
        assertThat(runs.inserted).containsExactlyElementsOf(result);
        assertThat(runs.pruned).containsExactly(
                "jira_digest/" + A.id() + "/60", "jira_digest/" + B.id() + "/60", "jira_digest/" + C.id() + "/60");

        Instant since = SETTINGS.calendar().previousWorkingDayStart(NOW);
        BriefingContent expected = DigestRules.compute(new DigestInput("Beta", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), since, NOW, SETTINGS, FIELD_IDS));
        AppBriefingRun beta = runOf(result, B);
        assertThat(beta.status()).isEqualTo(expected.status());
        assertThat(beta.attention()).isEqualTo(expected.attention());
        assertThat(beta.headline()).isEqualTo(expected.headline());
        assertThat(beta.summary()).isEqualTo(expected.summary());
        assertThat(beta.sections()).isEqualTo(expected.sections());
        assertThat(beta.reason()).isNull();
    }

    @Test
    void theDigestUsesItsQueriesAndCaps() {
        when(products.findAll()).thenReturn(List.of(B));
        Instant since = SETTINGS.calendar().previousWorkingDayStart(NOW);
        int daysBack = BriefingQueries.daysBack(since, NOW);
        List<String> keys = List.of("ABC");

        runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS);

        int uncapped = BriefingQueries.UNCAPPED;
        verify(jira).search(CONTEXT, BriefingQueries.activeSprintOpen(keys), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.staleCandidates(keys, 3), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.nextSprint(keys), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.createdSince(keys, daysBack), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.doneSince(keys, daysBack), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.reopenCandidates(keys, daysBack), FIELD_IDS, 30);
        verify(jira).search(CONTEXT, BriefingQueries.dueWithin(keys, 5), FIELD_IDS, uncapped);
        verify(jira, times(7)).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void theDigestStartsFromTheLastSuccessfulRun() {
        when(products.findAll()).thenReturn(List.of(B));
        Instant last = NOW.minusSeconds(3600);
        runs.lastSuccess.put(B.id(), last);

        runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS);

        verify(jira).search(CONTEXT, BriefingQueries.createdSince(List.of("ABC"), BriefingQueries.daysBack(last, NOW)),
                FIELD_IDS, BriefingQueries.UNCAPPED);
    }

    @Test
    void reopenedMeansMovedOutOfDoneSinceTheLastRun() {
        when(products.findAll()).thenReturn(List.of(B));
        Instant since = SETTINGS.calendar().previousWorkingDayStart(NOW);
        List<String> keys = List.of("ABC");
        BriefingIssue recent = issue("ABC-1", NOW.minusSeconds(60));
        BriefingIssue old = issue("ABC-2", NOW.minusSeconds(60));
        BriefingIssue forward = issue("ABC-3", NOW.minusSeconds(60));
        when(jira.search(CONTEXT, BriefingQueries.reopenCandidates(keys, BriefingQueries.daysBack(since, NOW)),
                FIELD_IDS, 30)).thenReturn(List.of(recent, old, forward));
        when(history.statusCategories(CONTEXT)).thenReturn(Map.of("10", StatusCategory.DONE,
                "3", StatusCategory.IN_PROGRESS, "1", StatusCategory.TO_DO));
        when(history.statusTransitions(CONTEXT, "ABC-1"))
                .thenReturn(List.of(new StatusTransition(since.plusSeconds(3600), "10", "3")));
        when(history.statusTransitions(CONTEXT, "ABC-2"))
                .thenReturn(List.of(new StatusTransition(since.minusSeconds(3600), "10", "3")));
        when(history.statusTransitions(CONTEXT, "ABC-3"))
                .thenReturn(List.of(new StatusTransition(since.plusSeconds(3600), "1", "10")));

        AppBriefingRun run = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS).getFirst();

        List<String> changedKeys = run.sections().stream()
                .filter(s -> s.title().equals("Changes since the last run"))
                .flatMap(s -> s.items().stream()).map(BriefingContent.Item::issueKey).toList();
        assertThat(changedKeys).containsExactly("ABC-1");
        verify(history, times(1)).statusCategories(CONTEXT);
    }

    @Test
    void aProductAlreadyEvaluatedForTheSlotIsSkippedSilently() {
        runs.existing.add(B.id());

        List<AppBriefingRun> result = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of("CNV"), SETTINGS);

        assertThat(result).extracting(AppBriefingRun::productId).containsExactly(A.id(), C.id());
        verify(jira, never()).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void coverageNeedsEveryKeyCovered() {
        when(products.findAll()).thenReturn(List.of(A));
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("ABC", "CNV")));

        AppBriefingRun run = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of("CNV"), SETTINGS).getFirst();

        assertThat(run.status()).isNotEqualTo(BriefingStatus.SKIPPED);
        verify(jira, times(7)).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void noSchedulerCredentialRecordsNeedsCredentialWithoutCallingJira() {
        when(access.designatedContext()).thenReturn(Optional.empty());
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("CNV"), B.id(), List.of("ABC")));

        List<AppBriefingRun> result = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS);

        assertThat(runOf(result, A).status()).isEqualTo(BriefingStatus.NEEDS_CREDENTIAL);
        assertThat(runOf(result, A).headline()).isEqualTo("The scheduler's Jira credential is missing or expired.");
        assertThat(runOf(result, A).attention()).isNull();
        assertThat(runOf(result, B).status()).isEqualTo(BriefingStatus.NEEDS_CREDENTIAL);
        assertThat(runOf(result, C).status()).isEqualTo(BriefingStatus.SKIPPED);
        verify(jira, never()).search(any(), anyString(), any(), anyInt());
        verify(fields, never()).resolveOptional(any(), any());
    }

    @Test
    void theFirstRejectionStopsAllJiraCallsForTheSlot() {
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("CNV"), B.id(), List.of("ABC")));
        when(jira.search(any(), anyString(), any(), anyInt()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS));

        List<AppBriefingRun> result = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS);

        assertThat(runOf(result, A).status()).isEqualTo(BriefingStatus.NEEDS_CREDENTIAL);
        assertThat(runOf(result, B).status()).isEqualTo(BriefingStatus.NEEDS_CREDENTIAL);
        verify(jira, times(1)).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void aRejectedFieldLookupMeansNeedsCredentialForAll() {
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("CNV"), B.id(), List.of("ABC")));
        when(fields.resolveOptional(any(), any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS));

        List<AppBriefingRun> result = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS);

        assertThat(runOf(result, A).status()).isEqualTo(BriefingStatus.NEEDS_CREDENTIAL);
        assertThat(runOf(result, B).status()).isEqualTo(BriefingStatus.NEEDS_CREDENTIAL);
        verify(fields, times(1)).resolveOptional(any(), any());
        verify(jira, never()).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void otherJiraFailuresRecordAFailedRunAndTheNextProductStillRuns() {
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("CNV"), B.id(), List.of("ABC")));
        when(jira.search(eq(CONTEXT), eq(BriefingQueries.activeSprintOpen(List.of("CNV"))), any(), anyInt()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED));

        List<AppBriefingRun> result = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS);

        assertThat(runOf(result, A).status()).isEqualTo(BriefingStatus.FAILED);
        assertThat(runOf(result, A).reason()).isEqualTo("JIRA_RATE_LIMITED");
        assertThat(runOf(result, A).headline()).isEqualTo("This run failed.");
        assertThat(runOf(result, A).attention()).isNull();
        assertThat(runOf(result, B).status()).isNotEqualTo(BriefingStatus.FAILED);
        verify(jira).search(CONTEXT, BriefingQueries.activeSprintOpen(List.of("ABC")), FIELD_IDS,
                BriefingQueries.UNCAPPED);
    }

    @Test
    void anUnexpectedErrorRecordsFailedWithReasonError() {
        when(products.findAll()).thenReturn(List.of(B));
        when(jira.search(any(), anyString(), any(), anyInt())).thenThrow(new IllegalStateException("boom"));

        AppBriefingRun run = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS).getFirst();

        assertThat(run.status()).isEqualTo(BriefingStatus.FAILED);
        assertThat(run.reason()).isEqualTo("ERROR");
    }

    @Test
    void theContentPassesThroughTheHeadlineService() {
        when(products.findAll()).thenReturn(List.of(B));
        when(headlines.enrich(any(), eq("Beta"))).thenAnswer(inv -> {
            BriefingContent c = inv.getArgument(0);
            return new BriefingContent(c.status(), c.attention(), "Model headline", "Model summary", c.sections());
        });

        AppBriefingRun run = runner.runSlot(BriefingTask.JIRA_DIGEST, SLOT, Set.of(), SETTINGS).getFirst();

        assertThat(run.headline()).isEqualTo("Model headline");
        assertThat(run.summary()).isEqualTo("Model summary");
    }

    @Test
    void theSprintCheckUsesTheNextSprintAndACappedBacklog() {
        when(products.findAll()).thenReturn(List.of(B));
        List<String> keys = List.of("ABC");

        runner.runSlot(BriefingTask.SPRINT_CHECK, SLOT, Set.of(), SETTINGS);

        verify(jira).search(CONTEXT, BriefingQueries.nextSprint(keys), FIELD_IDS, BriefingQueries.UNCAPPED);
        verify(jira).search(CONTEXT, BriefingQueries.backlog(keys), FIELD_IDS, 15);
        verify(jira, times(2)).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void theWeeklyStatusUsesItsQueries() {
        when(products.findAll()).thenReturn(List.of(B));
        List<String> keys = List.of("ABC");
        int uncapped = BriefingQueries.UNCAPPED;

        runner.runSlot(BriefingTask.WEEKLY_STATUS, SLOT, Set.of(), SETTINGS);

        verify(jira).search(CONTEXT, BriefingQueries.doneSince(keys, 7), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.inProgress(keys), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.activeSprintAll(keys), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.createdSince(keys, 7), FIELD_IDS, uncapped);
        verify(jira).search(CONTEXT, BriefingQueries.dueWithin(keys, 0), FIELD_IDS, uncapped);
        verify(jira, times(5)).search(any(), anyString(), any(), anyInt());
    }

    @Test
    void fieldIdsAreResolvedOncePerCall() {
        when(products.jiraProjectKeys()).thenReturn(Map.of(A.id(), List.of("CNV"), B.id(), List.of("ABC")));

        runner.runSlot(BriefingTask.SPRINT_CHECK, SLOT, Set.of(), SETTINGS);

        verify(fields, times(1)).resolveOptional(any(), any());
        verify(access, times(1)).designatedContext();
    }

    @Test
    void runNowIgnoresClaudeAndRecordsManualRuns() {
        List<AppBriefingRun> result = runner.runNow(BriefingTask.SPRINT_CHECK, SETTINGS);

        assertThat(result).extracting(AppBriefingRun::productId).containsExactly(A.id(), B.id(), C.id());
        assertThat(result).allSatisfy(run -> {
            assertThat(run.trigger()).isEqualTo(BriefingTrigger.MANUAL);
            assertThat(run.slotAt()).isNull();
        });
        assertThat(runOf(result, A).status()).isNotEqualTo(BriefingStatus.SKIPPED);
        assertThat(runOf(result, C).reason()).isEqualTo(AppBriefingRun.REASON_NO_JIRA_PROJECT);
    }

    @Test
    void tasksWithoutAnAppFallbackAreRejected() {
        assertThatThrownBy(() -> runner.runNow(BriefingTask.REG_WATCH, SETTINGS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.runSlot(BriefingTask.GULF_INVEST, SLOT, Set.of(), SETTINGS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BriefingIssue issue(String key, Instant updated) {
        return new BriefingIssue(key, "Summary " + key, "Story", "In Progress", StatusCategory.IN_PROGRESS, "Medium",
                false, null, updated, updated, null, null, null, null, 0, false, null, List.of());
    }

    private static final class FakeRuns implements BriefingRunRepository {
        final List<AppBriefingRun> inserted = new ArrayList<>();
        final List<String> pruned = new ArrayList<>();
        final Set<UUID> existing = new HashSet<>();
        final Map<UUID, Instant> lastSuccess = new HashMap<>();

        @Override
        public boolean insert(AppBriefingRun run) {
            inserted.add(run);
            return true;
        }

        @Override
        public boolean existsForSlot(BriefingTask task, UUID productId, Instant slotAt) {
            return existing.contains(productId);
        }

        @Override
        public Optional<Instant> lastSuccessfulFinishedAt(BriefingTask task, UUID productId) {
            return Optional.ofNullable(lastSuccess.get(productId));
        }

        @Override
        public List<AppBriefingRun> findByTask(BriefingTask task) {
            return List.of();
        }

        @Override
        public void prune(BriefingTask task, UUID productId, int keepRuns) {
            pruned.add(task.id() + "/" + productId + "/" + keepRuns);
        }

        @Override
        public Optional<BriefingStatus> latestStatus() {
            return Optional.empty();
        }
    }
}
