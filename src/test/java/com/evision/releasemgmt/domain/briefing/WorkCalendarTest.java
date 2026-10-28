package com.evision.releasemgmt.domain.briefing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkCalendarTest {

    private final WorkCalendar calendar = new WorkCalendar(WorkCalendar.CAIRO, WorkCalendar.SUN_TO_THU);

    private static Instant cairo(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(WorkCalendar.CAIRO).toInstant();
    }

    @Test
    void countsWorkDaysAfterTheUpdateDateThroughToday() {
        assertThat(calendar.workingDaysElapsed(cairo(2026, 10, 21, 10, 0), cairo(2026, 10, 26, 9, 0))).isEqualTo(3);
    }

    @Test
    void weekendDaysAreNotCounted() {
        assertThat(calendar.workingDaysElapsed(cairo(2026, 10, 22, 10, 0), cairo(2026, 10, 24, 9, 0))).isZero();
    }

    @Test
    void weekStartIsSundayMidnightInCairoAcrossTheDstChange() {
        assertThat(calendar.weekStart(cairo(2026, 10, 29, 13, 41))).isEqualTo(Instant.parse("2026-10-24T21:00:00Z"));
        assertThat(calendar.weekStart(cairo(2026, 11, 1, 8, 44))).isEqualTo(Instant.parse("2026-10-31T22:00:00Z"));
    }

    @Test
    void previousWorkingDayStartSkipsTheWeekend() {
        assertThat(calendar.previousWorkingDayStart(cairo(2026, 11, 1, 8, 44))).isEqualTo(cairo(2026, 10, 29, 0, 0));
    }

    @Test
    void todayIsTheCairoDate() {
        assertThat(calendar.today(Instant.parse("2026-10-24T22:30:00Z"))).isEqualTo(LocalDate.of(2026, 10, 25));
    }
}
