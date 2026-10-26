package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BriefingRunRepository {

    /** False when (task, product, slotAt) already has a run; never throws for that case. */
    boolean insert(AppBriefingRun run);

    boolean existsForSlot(BriefingTask task, UUID productId, Instant slotAt);

    /** finished_at of the newest ok/attention/quiet run of this task and product. */
    Optional<Instant> lastSuccessfulFinishedAt(BriefingTask task, UUID productId);

    /** All runs of a task, newest first by finished_at. */
    List<AppBriefingRun> findByTask(BriefingTask task);

    /** Keeps the newest keepRuns runs of (task, product) by finished_at; deletes the rest in one statement. */
    void prune(BriefingTask task, UUID productId, int keepRuns);

    /** Status of the newest non-skipped run across all tasks and products. */
    Optional<BriefingStatus> latestStatus();
}
