package com.evision.releasemgmt.domain.briefing;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

public record WorkCalendar(ZoneId zone, Set<DayOfWeek> workDays) {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    public static final Set<DayOfWeek> SUN_TO_THU = EnumSet.of(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY);

    public LocalDate today(Instant now) {
        return now.atZone(zone).toLocalDate();
    }

    /** Work days after from's local date through now's local date, inclusive. */
    public int workingDaysElapsed(Instant from, Instant now) {
        LocalDate last = today(now);
        int count = 0;
        for (LocalDate d = today(from).plusDays(1); !d.isAfter(last); d = d.plusDays(1)) {
            if (workDays.contains(d.getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }

    public Instant weekStart(Instant now) {
        LocalDate d = today(now);
        while (d.getDayOfWeek() != SUNDAY) {
            d = d.minusDays(1);
        }
        return d.atStartOfDay(zone).toInstant();
    }

    public Instant previousWorkingDayStart(Instant now) {
        LocalDate d = today(now).minusDays(1);
        while (!workDays.contains(d.getDayOfWeek())) {
            d = d.minusDays(1);
        }
        return d.atStartOfDay(zone).toInstant();
    }
}
