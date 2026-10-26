package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingRejectedException;
import com.evision.releasemgmt.domain.briefing.BriefingRejection;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import org.springframework.stereotype.Service;

import java.util.List;

/** "Run briefing now" ignores Claude and reports credential trouble as a count, never as an error. */
@Service
public class BriefingRunNowService {

    public record Result(String task, String name, int products, int attention, int failed, int needsCredential,
            int skipped) { }

    private final BriefingRunner runner;
    private final BriefingSettingsService settings;

    public BriefingRunNowService(BriefingRunner runner, BriefingSettingsService settings) {
        this.runner = runner;
        this.settings = settings;
    }

    public Result runNow(String taskId) {
        BriefingTask task = BriefingTask.fromId(taskId).filter(BriefingTask::appFallback)
                .orElseThrow(() -> new BriefingRejectedException(BriefingRejection.BRIEFING_TASK_NOT_RUNNABLE));
        if (!settings.taskSettings(task).enabled()) {
            throw new BriefingRejectedException(BriefingRejection.BRIEFING_TASK_DISABLED);
        }
        List<AppBriefingRun> runs = runner.runNow(task, settings.effective(task));
        int skipped = count(runs, BriefingStatus.SKIPPED);
        return new Result(task.id(), task.displayName(), runs.size() - skipped, count(runs, BriefingStatus.ATTENTION),
                count(runs, BriefingStatus.FAILED), count(runs, BriefingStatus.NEEDS_CREDENTIAL), skipped);
    }

    private static int count(List<AppBriefingRun> runs, BriefingStatus status) {
        return (int) runs.stream().filter(run -> run.status() == status).count();
    }
}
