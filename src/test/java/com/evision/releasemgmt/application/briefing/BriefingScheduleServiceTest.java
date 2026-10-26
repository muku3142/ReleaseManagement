package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.FieldMetadataService;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.Schedule;
import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingClaudeConfig;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingRunSettings;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingThresholds;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.domain.briefing.WorkCalendar;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.ports.BriefingConfigSource;
import com.evision.releasemgmt.ports.BriefingReadPort;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.evision.releasemgmt.domain.briefing.WorkCalendar.CAIRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BriefingScheduleServiceTest {

    private static final Instant MONDAY_0920 = cairo("2026-10-26T09:20");
    private static final Instant DIGEST_SLOT = Instant.parse("2026-10-26T05:44:00Z");
    private static final BriefingRunSettings RUN_SETTINGS = new BriefingRunSettings(BriefingThresholds.DEFAULTS,
            new WorkCalendar(CAIRO, WorkCalendar.SUN_TO_THU), 60);
    private static final BriefingClaudeConfig CONFIG = new BriefingClaudeConfig(true,
            Map.of(BriefingTask.JIRA_DIGEST, Set.of("CNV"), BriefingTask.SPRINT_CHECK, Set.of("CNV")),
            null, null, null, null);

    private final BriefingSettingsService settings = mock(BriefingSettingsService.class);
    private final BriefingRunner runner = mock(BriefingRunner.class);
    private final List<BriefingTask> feedReads = new ArrayList<>();
    private BriefingFeed digestFeed = feed();
    private BriefingClaudeConfig config = CONFIG;
    private List<Schedule> schedules = List.of(
            new Schedule(BriefingTask.JIRA_DIGEST, true, "0 44 8 * * SUN,MON,TUE,WED,THU"),
            new Schedule(BriefingTask.SPRINT_CHECK, true, "0 41 9 * * WED"),
            new Schedule(BriefingTask.WEEKLY_STATUS, true, "0 41 13 * * THU"));

    private final BriefingReadPort claudeFeed = new BriefingReadPort() {
        @Override
        public BriefingFeed feed(BriefingTask task) {
            feedReads.add(task);
            return task == BriefingTask.JIRA_DIGEST ? digestFeed : new BriefingFeed(task, List.of(), false);
        }

        @Override
        public boolean directoryReadable() {
            return true;
        }
    };
    private final BriefingConfigSource configSource = () -> config;
    private final BriefingScheduleService service = new BriefingScheduleService(settings, claudeFeed, configSource,
            runner, new BriefingsProperties(null, null, null, null, Duration.ofHours(12), null),
            Clock.fixed(MONDAY_0920, ZoneOffset.UTC));

    private static Instant cairo(String local) {
        return LocalDateTime.parse(local).atZone(CAIRO).toInstant();
    }

    private static BriefingFeed feed(BriefingRun... rows) {
        return new BriefingFeed(BriefingTask.JIRA_DIGEST, List.of(rows), false);
    }

    private static BriefingRun row(String cairoTime, BriefingStatus status) {
        return new BriefingRun(cairo(cairoTime), status, "h", null, null, null, null);
    }

    @BeforeEach
    void defaults() {
        when(settings.fallbackEnabled()).thenReturn(true);
        when(settings.schedules()).thenAnswer(inv -> schedules);
        when(settings.grace()).thenReturn(Duration.ofMinutes(30));
        when(settings.effective(any())).thenReturn(RUN_SETTINGS);
        when(runner.runSlot(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void aClaudeRowForTheSlotPassesTheConfiguredCoverage() {
        digestFeed = feed(row("2026-10-26T08:50", BriefingStatus.OK));

        service.tick(MONDAY_0920);

        verify(runner).runSlot(BriefingTask.JIRA_DIGEST, DIGEST_SLOT, Set.of("CNV"), RUN_SETTINGS);
        verify(runner, times(1)).runSlot(any(), any(), any(), any());
    }

    @Test
    void noUsableClaudeRowMeansNothingIsCovered() {
        List<Runnable> setups = List.of(
                () -> digestFeed = feed(),
                () -> digestFeed = feed(row("2026-10-26T08:50", BriefingStatus.FAILED)),
                () -> digestFeed = new BriefingFeed(BriefingTask.JIRA_DIGEST, List.of(), true),
                () -> {
                    digestFeed = feed(row("2026-10-26T08:50", BriefingStatus.OK));
                    config = BriefingClaudeConfig.UNREADABLE;
                });
        for (Runnable setup : setups) {
            setup.run();
            service.tick(MONDAY_0920);
        }

        verify(runner, times(4)).runSlot(BriefingTask.JIRA_DIGEST, DIGEST_SLOT, Set.of(), RUN_SETTINGS);
    }

    @Test
    void beforeSlotPlusGraceNothingIsDue() {
        service.tick(cairo("2026-10-26T09:00"));

        verify(runner, never()).runSlot(any(), any(), any(), any());
    }

    @Test
    void aDisabledTaskIsNeverEvaluated() {
        schedules = List.of(new Schedule(BriefingTask.JIRA_DIGEST, false, "0 44 8 * * SUN,MON,TUE,WED,THU"));

        service.tick(MONDAY_0920);

        verify(runner, never()).runSlot(any(), any(), any(), any());
        assertThat(feedReads).isEmpty();
    }

    @Test
    void sprintCheckIsNotDueOnMonday() {
        service.tick(MONDAY_0920);

        verify(runner, never()).runSlot(eq(BriefingTask.SPRINT_CHECK), any(), any(), any());
        assertThat(feedReads).containsExactly(BriefingTask.JIRA_DIGEST);
    }

    @Test
    void oneFailingTaskDoesNotStopTheNext() {
        schedules = List.of(
                new Schedule(BriefingTask.JIRA_DIGEST, true, "0 44 8 * * SUN,MON,TUE,WED,THU"),
                new Schedule(BriefingTask.WEEKLY_STATUS, true, "0 44 8 * * MON"));
        when(runner.runSlot(eq(BriefingTask.JIRA_DIGEST), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        service.tick(MONDAY_0920);

        verify(runner).runSlot(BriefingTask.WEEKLY_STATUS, DIGEST_SLOT, Set.of(), RUN_SETTINGS);
    }

    @Test
    void aTickWhileOneIsRunningReturnsImmediately() {
        when(runner.runSlot(any(), any(), any(), any())).thenAnswer(inv -> {
            service.tick(MONDAY_0920);
            return List.of();
        });

        service.tick(MONDAY_0920);

        verify(runner, times(1)).runSlot(any(), any(), any(), any());
    }

    @Test
    void withTheSwitchOffADueSlotRecordsNothingAndTouchesNothing() {
        RealRunner real = new RealRunner();
        when(settings.fallbackEnabled()).thenReturn(false);

        real.service.tick(MONDAY_0920);

        verify(real.runs, never()).insert(any());
        verifyNoInteractions(real.jira, real.access, real.products);
        verify(settings, never()).schedules();
        assertThat(feedReads).isEmpty();
    }

    @Test
    void withTheSwitchOnTheSameSlotRecordsRuns() {
        RealRunner real = new RealRunner();

        real.service.tick(MONDAY_0920);

        ArgumentCaptor<AppBriefingRun> saved = ArgumentCaptor.forClass(AppBriefingRun.class);
        verify(real.runs).insert(saved.capture());
        assertThat(saved.getValue().task()).isEqualTo(BriefingTask.JIRA_DIGEST);
        assertThat(saved.getValue().slotAt()).isEqualTo(DIGEST_SLOT);
        assertThat(saved.getValue().trigger()).isEqualTo(BriefingTrigger.SLOT);
        verify(real.access).designatedContext();
    }

    // The production runner over mocked ports, so the switch tests can assert saved runs and Jira calls.
    private final class RealRunner {
        final ProductRepository products = mock(ProductRepository.class);
        final BriefingRunRepository runs = mock(BriefingRunRepository.class);
        final BriefingJiraAccess access = mock(BriefingJiraAccess.class);
        final JiraBriefingPort jira = mock(JiraBriefingPort.class);
        final BriefingScheduleService service;

        RealRunner() {
            Product product = new Product(UUID.randomUUID(), "Alpha", null);
            when(products.findAll()).thenReturn(List.of(product));
            when(products.jiraProjectKeys()).thenReturn(Map.of(product.id(), List.of("ABC")));
            when(access.designatedContext()).thenReturn(Optional.empty());
            when(runs.insert(any())).thenReturn(true);
            BriefingRunner runner = new BriefingRunner(products, runs, access, jira, mock(JiraIssueHistoryPort.class),
                    mock(FieldMetadataService.class), mock(BriefingHeadlineService.class),
                    Clock.fixed(MONDAY_0920, ZoneOffset.UTC));
            service = new BriefingScheduleService(settings, claudeFeed, configSource, runner,
                    new BriefingsProperties(null, null, null, null, Duration.ofHours(12), null),
                    Clock.fixed(MONDAY_0920, ZoneOffset.UTC));
        }
    }

    @Test
    void theScheduledTickUsesTheClock() {
        service.scheduledTick();

        verify(runner).runSlot(BriefingTask.JIRA_DIGEST, DIGEST_SLOT, Set.of(), RUN_SETTINGS);
    }
}
