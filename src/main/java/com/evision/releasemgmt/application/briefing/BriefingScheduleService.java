package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.briefing.BriefingSettingsService.Schedule;
import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.WorkCalendar;
import com.evision.releasemgmt.ports.BriefingConfigSource;
import com.evision.releasemgmt.ports.BriefingReadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the app fallback for each due slot after the grace period, for the products Claude did not cover. */
@Service
public class BriefingScheduleService {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduleService.class);

    private final BriefingSettingsService settings;
    private final BriefingReadPort claudeFeed;
    private final BriefingConfigSource config;
    private final BriefingRunner runner;
    private final BriefingsProperties props;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public BriefingScheduleService(BriefingSettingsService settings, BriefingReadPort claudeFeed,
            BriefingConfigSource config, BriefingRunner runner, BriefingsProperties props, Clock clock) {
        this.settings = settings;
        this.claudeFeed = claudeFeed;
        this.config = config;
        this.runner = runner;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.briefings.initial-delay}",
            fixedDelayString = "${app.briefings.tick-interval}")
    public void scheduledTick() {
        tick(clock.instant());
    }

    public void tick(Instant now) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            // D-17.1-24: off by default; until the user turns it on, the scheduler records nothing and reads nothing.
            if (!settings.fallbackEnabled()) {
                return;
            }
            Duration grace = settings.grace();
            for (Schedule schedule : settings.schedules()) {
                if (schedule.enabled()) {
                    evaluate(schedule, now, grace);
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void evaluate(Schedule schedule, Instant now, Duration grace) {
        try {
            Optional<Instant> slot = BriefingSlots.latestSlotAtOrBefore(schedule.cron(), WorkCalendar.CAIRO,
                    now.minus(grace), props.catchUp());
            if (slot.isEmpty()) {
                return;
            }
            Set<String> covered = BriefingSlots.claudeCovers(claudeFeed.feed(schedule.task()), slot.get(), grace)
                    ? config.read().coveredProjects().getOrDefault(schedule.task(), Set.of())
                    : Set.of();
            List<AppBriefingRun> runs = runner.runSlot(schedule.task(), slot.get(), covered,
                    settings.effective(schedule.task()));
            log.info("briefing tick task={} slot={} runs={}", schedule.task().id(), slot.get(), runs.size());
        } catch (RuntimeException e) {
            log.warn("briefing tick failed task={} exceptionType={}", schedule.task().id(),
                    e.getClass().getSimpleName());
        }
    }
}
