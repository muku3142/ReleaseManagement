package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.briefing.BriefingRunNowService.Result;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingRejectedException;
import com.evision.releasemgmt.domain.briefing.BriefingRejection;
import com.evision.releasemgmt.domain.briefing.BriefingRunSettings;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTaskSettings;
import com.evision.releasemgmt.domain.briefing.BriefingThresholds;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.domain.briefing.WorkCalendar;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BriefingRunNowServiceTest {

    private static final BriefingRunSettings EFFECTIVE = new BriefingRunSettings(BriefingThresholds.DEFAULTS,
            new WorkCalendar(WorkCalendar.CAIRO, EnumSet.of(DayOfWeek.SUNDAY)), 60);

    private final BriefingRunner runner = mock(BriefingRunner.class);
    private final BriefingSettingsService settings = mock(BriefingSettingsService.class);
    private final BriefingRunNowService service = new BriefingRunNowService(runner, settings);

    @Test
    void countsRecordedRunsByStatus() {
        enabled(BriefingTask.SPRINT_CHECK, true);
        when(runner.runNow(BriefingTask.SPRINT_CHECK, EFFECTIVE)).thenReturn(List.of(
                run(BriefingStatus.ATTENTION), run(BriefingStatus.OK), run(BriefingStatus.NEEDS_CREDENTIAL),
                run(BriefingStatus.SKIPPED)));

        Result result = service.runNow("sprint_check");

        assertThat(result).isEqualTo(new Result("sprint_check", "Sprint backlog check", 3, 1, 0, 1, 1));
        verify(runner).runNow(BriefingTask.SPRINT_CHECK, EFFECTIVE);
    }

    @Test
    void countsFailedRuns() {
        enabled(BriefingTask.JIRA_DIGEST, true);
        when(runner.runNow(BriefingTask.JIRA_DIGEST, EFFECTIVE))
                .thenReturn(List.of(run(BriefingStatus.FAILED), run(BriefingStatus.QUIET)));

        assertThat(service.runNow("jira_digest")).isEqualTo(new Result("jira_digest", "Daily Jira digest", 2, 0, 1, 0, 0));
    }

    @Test
    void displayOnlyAndUnknownTasksAreNotRunnable() {
        for (String id : List.of("reg_watch", "gulf_invest", "nope")) {
            assertThatThrownBy(() -> service.runNow(id))
                    .isInstanceOfSatisfying(BriefingRejectedException.class,
                            e -> assertThat(e.reason()).isEqualTo(BriefingRejection.BRIEFING_TASK_NOT_RUNNABLE));
        }
        verify(runner, never()).runNow(any(), any());
    }

    @Test
    void disabledTaskIsRejected() {
        enabled(BriefingTask.SPRINT_CHECK, false);

        assertThatThrownBy(() -> service.runNow("sprint_check"))
                .isInstanceOfSatisfying(BriefingRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(BriefingRejection.BRIEFING_TASK_DISABLED));
        verify(runner, never()).runNow(any(), any());
    }

    private void enabled(BriefingTask task, boolean enabled) {
        when(settings.taskSettings(task))
                .thenReturn(new BriefingTaskSettings(task, enabled, "0 41 9 * * WED", null, null, null));
        when(settings.effective(task)).thenReturn(EFFECTIVE);
    }

    private static AppBriefingRun run(BriefingStatus status) {
        Instant now = Instant.parse("2026-10-26T07:00:00Z");
        return new AppBriefingRun(UUID.randomUUID(), BriefingTask.SPRINT_CHECK, UUID.randomUUID(), null,
                BriefingTrigger.MANUAL, status, null, status == BriefingStatus.ATTENTION ? 2 : null, "h", null,
                List.of(), now, now);
    }
}
