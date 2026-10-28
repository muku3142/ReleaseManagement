package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

final class BriefingSlots {

    static final Duration CLAUDE_EARLY_TOLERANCE = Duration.ofMinutes(5);

    private BriefingSlots() {
    }

    // Walks the cron in the zone itself so DST shifts move the slot's UTC time, never a fixed 24 h step.
    static Optional<Instant> latestSlotAtOrBefore(String cron, ZoneId zone, Instant cutoff, Duration lookback) {
        CronExpression expression = CronExpression.parse(cron);
        Instant latest = null;
        ZonedDateTime next = expression.next(cutoff.minus(lookback).minusNanos(1).atZone(zone));
        while (next != null && !next.toInstant().isAfter(cutoff)) {
            latest = next.toInstant();
            next = expression.next(next);
        }
        return Optional.ofNullable(latest);
    }

    static boolean claudeCovers(BriefingFeed feed, Instant slot, Duration grace) {
        Instant from = slot.minus(CLAUDE_EARLY_TOLERANCE);
        Instant to = slot.plus(grace);
        return !feed.readError() && feed.runs().stream().anyMatch(run -> run.status() != BriefingStatus.FAILED
                && !run.runAt().isBefore(from) && !run.runAt().isAfter(to));
    }
}
