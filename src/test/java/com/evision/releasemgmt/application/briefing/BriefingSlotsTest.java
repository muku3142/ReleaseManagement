package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static com.evision.releasemgmt.domain.briefing.WorkCalendar.CAIRO;
import static org.assertj.core.api.Assertions.assertThat;

class BriefingSlotsTest {

    private static final String DIGEST = "0 44 8 * * SUN,MON,TUE,WED,THU";
    private static final String SPRINT = "0 41 9 * * WED";
    private static final String WEEKLY = "0 41 13 * * THU";
    private static final Duration CATCH_UP = Duration.ofHours(12);
    private static final Duration GRACE = Duration.ofMinutes(30);
    private static final Instant SLOT = Instant.parse("2026-10-26T05:44:00Z");

    private static Instant cairo(String local) {
        return LocalDateTime.parse(local).atZone(CAIRO).toInstant();
    }

    @Test
    void lastSummerTimeThursdaySlotIsUtcPlusThree() {
        assertThat(BriefingSlots.latestSlotAtOrBefore(DIGEST, CAIRO, cairo("2026-10-29T09:20"), CATCH_UP))
                .contains(Instant.parse("2026-10-29T05:44:00Z"));
    }

    @Test
    void firstWinterTimeSundaySlotIsUtcPlusTwo() {
        assertThat(BriefingSlots.latestSlotAtOrBefore(DIGEST, CAIRO, cairo("2026-11-01T09:20"), CATCH_UP))
                .contains(Instant.parse("2026-11-01T06:44:00Z"));
    }

    @Test
    void noSlotWithinTheLookbackIsEmpty() {
        assertThat(BriefingSlots.latestSlotAtOrBefore(DIGEST, CAIRO, cairo("2026-10-31T12:00"), CATCH_UP)).isEmpty();
    }

    @Test
    void weeklySlotOnThursdayAfternoon() {
        assertThat(BriefingSlots.latestSlotAtOrBefore(WEEKLY, CAIRO, cairo("2026-10-29T14:15"), CATCH_UP))
                .contains(Instant.parse("2026-10-29T10:41:00Z"));
    }

    @Test
    void aSlotExactlyAtTheCutoffCounts() {
        assertThat(BriefingSlots.latestSlotAtOrBefore(DIGEST, CAIRO, SLOT, CATCH_UP)).contains(SLOT);
    }

    @Test
    void theSeededCronsAreValid() {
        assertThat(List.of(DIGEST, SPRINT, WEEKLY)).allMatch(CronExpression::isValidExpression);
    }

    @Test
    void anOkRowWithinGraceCovers() {
        assertThat(BriefingSlots.claudeCovers(feed(row(SLOT.plusSeconds(600), BriefingStatus.OK)), SLOT, GRACE)).isTrue();
    }

    @Test
    void aFailedRowDoesNotCover() {
        assertThat(BriefingSlots.claudeCovers(feed(row(SLOT.plusSeconds(600), BriefingStatus.FAILED)), SLOT, GRACE))
                .isFalse();
    }

    @Test
    void aRowUpToFiveMinutesEarlyCovers() {
        assertThat(BriefingSlots.claudeCovers(feed(row(SLOT.minusSeconds(240), BriefingStatus.QUIET)), SLOT, GRACE))
                .isTrue();
        assertThat(BriefingSlots.claudeCovers(feed(row(SLOT.minusSeconds(360), BriefingStatus.OK)), SLOT, GRACE))
                .isFalse();
    }

    @Test
    void aRowAfterGraceDoesNotCover() {
        Instant late = SLOT.plus(GRACE).plusSeconds(60);
        assertThat(BriefingSlots.claudeCovers(feed(row(late, BriefingStatus.ATTENTION)), SLOT, GRACE)).isFalse();
    }

    @Test
    void anUnreadableOrEmptyFeedDoesNotCover() {
        assertThat(BriefingSlots.claudeCovers(new BriefingFeed(BriefingTask.JIRA_DIGEST, List.of(), true), SLOT, GRACE))
                .isFalse();
        assertThat(BriefingSlots.claudeCovers(feed(), SLOT, GRACE)).isFalse();
    }

    private static BriefingRun row(Instant runAt, BriefingStatus status) {
        return new BriefingRun(runAt, status, "h", null, null, null, null);
    }

    private static BriefingFeed feed(BriefingRun... rows) {
        return new BriefingFeed(BriefingTask.JIRA_DIGEST, List.of(rows), false);
    }
}
