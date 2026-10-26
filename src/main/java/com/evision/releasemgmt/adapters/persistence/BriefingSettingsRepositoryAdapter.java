package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.BriefingSettingsEntity;
import com.evision.releasemgmt.adapters.persistence.entity.BriefingTaskSettingEntity;
import com.evision.releasemgmt.adapters.persistence.repository.BriefingSettingsJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.BriefingTaskSettingJpaRepository;
import com.evision.releasemgmt.domain.briefing.BriefingSettings;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTaskSettings;
import com.evision.releasemgmt.ports.BriefingSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BriefingSettingsRepositoryAdapter implements BriefingSettingsRepository {

    private static final short SINGLETON_ID = 1;

    private final BriefingSettingsJpaRepository settings;
    private final BriefingTaskSettingJpaRepository taskSettings;

    public BriefingSettingsRepositoryAdapter(BriefingSettingsJpaRepository settings,
            BriefingTaskSettingJpaRepository taskSettings) {
        this.settings = settings;
        this.taskSettings = taskSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public BriefingSettings get() {
        BriefingSettingsEntity e = settings.findById(SINGLETON_ID).orElseThrow();
        return new BriefingSettings(e.getSchedulerAccountId(), e.getGraceMinutes(), parseDays(e.getWorkDays()),
                e.getKeepRuns(), e.isFallbackEnabled(), e.getUpdatedAt(), e.getUpdatedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BriefingTaskSettings> tasks() {
        return taskSettings.findAll().stream()
                .map(BriefingSettingsRepositoryAdapter::toDomain)
                .sorted(Comparator.comparing(BriefingTaskSettings::task))
                .toList();
    }

    @Override
    @Transactional
    public void saveSchedule(int graceMinutes, Set<DayOfWeek> workDays, Integer keepRuns,
            List<BriefingTaskSettings> tasks, Instant at, String by) {
        BriefingSettingsEntity e = settings.findById(SINGLETON_ID).orElseThrow();
        e.setGraceMinutes(graceMinutes);
        e.setWorkDays(formatDays(workDays));
        e.setKeepRuns(keepRuns);
        e.setUpdatedAt(at);
        e.setUpdatedBy(by);
        for (BriefingTaskSettings t : tasks) {
            BriefingTaskSettingEntity row = taskSettings.findById(t.task().id()).orElseThrow();
            row.setEnabled(t.enabled());
            row.setCron(t.cron());
            row.setStaleAfterWorkingDays(t.staleAfterWorkingDays());
            row.setDueSoonDays(t.dueSoonDays());
            row.setBacklogItems(t.backlogItems());
        }
    }

    @Override
    @Transactional
    public void setSchedulerAccount(String accountId, Instant at) {
        BriefingSettingsEntity e = settings.findById(SINGLETON_ID).orElseThrow();
        e.setSchedulerAccountId(accountId);
        e.setUpdatedAt(at);
    }

    @Override
    @Transactional
    public void setFallbackEnabled(boolean enabled, Instant at, String by) {
        BriefingSettingsEntity e = settings.findById(SINGLETON_ID).orElseThrow();
        e.setFallbackEnabled(enabled);
        e.setUpdatedAt(at);
        e.setUpdatedBy(by);
    }

    private static BriefingTaskSettings toDomain(BriefingTaskSettingEntity e) {
        return new BriefingTaskSettings(BriefingTask.fromId(e.getTask()).orElseThrow(), e.isEnabled(), e.getCron(),
                e.getStaleAfterWorkingDays(), e.getDueSoonDays(), e.getBacklogItems());
    }

    private static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String code : csv.split(",")) {
            days.add(Arrays.stream(DayOfWeek.values()).filter(d -> code(d).equals(code)).findFirst().orElseThrow());
        }
        return days;
    }

    private static String formatDays(Set<DayOfWeek> days) {
        return days.stream().sorted().map(BriefingSettingsRepositoryAdapter::code).collect(Collectors.joining(","));
    }

    private static String code(DayOfWeek d) {
        return d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
    }
}
