package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.briefing.BriefingSettings;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTaskSettings;
import com.evision.releasemgmt.ports.BriefingSettingsRepository;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BriefingSettingsPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private BriefingSettingsRepository settings;

    @Autowired
    private JiraCredentialRepository credentials;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restoreSeed() {
        jdbc.update("UPDATE briefing_settings SET scheduler_account_id = NULL, grace_minutes = 30, "
                + "work_days = 'SUN,MON,TUE,WED,THU', keep_runs = NULL, fallback_enabled = FALSE");
        jdbc.update("UPDATE briefing_task_setting SET enabled = TRUE, stale_after_working_days = NULL, "
                + "due_soon_days = NULL, backlog_items = NULL");
        jdbc.update("DELETE FROM jira_credential");
    }

    @Test
    void freshDatabaseHoldsTheSeededDefaults() {
        BriefingSettings s = settings.get();

        assertThat(s.graceMinutes()).isEqualTo(30);
        assertThat(s.workDays()).containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        assertThat(s.keepRuns()).isNull();
        assertThat(s.fallbackEnabled()).isFalse();
        assertThat(s.schedulerAccountId()).isNull();
        assertThat(settings.tasks()).containsExactly(
                new BriefingTaskSettings(BriefingTask.JIRA_DIGEST, true, "0 44 8 * * SUN,MON,TUE,WED,THU", null, null, null),
                new BriefingTaskSettings(BriefingTask.SPRINT_CHECK, true, "0 41 9 * * WED", null, null, null),
                new BriefingTaskSettings(BriefingTask.WEEKLY_STATUS, true, "0 41 13 * * THU", null, null, null));
    }

    @Test
    void saveScheduleRoundTripsAndLeavesTheSchedulerAccountAlone() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        settings.setSchedulerAccount("557058:abc", at.minusSeconds(60));

        settings.saveSchedule(45, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 80,
                List.of(new BriefingTaskSettings(BriefingTask.JIRA_DIGEST, false, "0 0 9 * * MON", 4, null, null),
                        new BriefingTaskSettings(BriefingTask.SPRINT_CHECK, true, "0 41 9 * * WED", null, 7, 20)),
                at, "rm@acme.com");

        BriefingSettings s = settings.get();
        assertThat(s.graceMinutes()).isEqualTo(45);
        assertThat(s.workDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY);
        assertThat(s.keepRuns()).isEqualTo(80);
        assertThat(s.updatedAt()).isEqualTo(at);
        assertThat(s.updatedBy()).isEqualTo("rm@acme.com");
        assertThat(s.schedulerAccountId()).isEqualTo("557058:abc");
        assertThat(settings.tasks()).containsExactly(
                new BriefingTaskSettings(BriefingTask.JIRA_DIGEST, false, "0 0 9 * * MON", 4, null, null),
                new BriefingTaskSettings(BriefingTask.SPRINT_CHECK, true, "0 41 9 * * WED", null, 7, 20),
                new BriefingTaskSettings(BriefingTask.WEEKLY_STATUS, true, "0 41 13 * * THU", null, null, null));
    }

    @Test
    void fallbackSwitchRoundTripsWithoutTouchingTheSchedule() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        settings.setFallbackEnabled(true, at, "557058:abc");

        BriefingSettings on = settings.get();
        assertThat(on.fallbackEnabled()).isTrue();
        assertThat(on.updatedAt()).isEqualTo(at);
        assertThat(on.updatedBy()).isEqualTo("557058:abc");
        assertThat(on.graceMinutes()).isEqualTo(30);
        assertThat(on.keepRuns()).isNull();

        settings.setFallbackEnabled(false, at.plusSeconds(1), "557058:def");
        assertThat(settings.get().fallbackEnabled()).isFalse();
    }

    @Test
    void settingTheSchedulerAccountNeverDecryptsItsCredential() {
        credentials.upsert("557058:abc", "rm@acme.com", "token-under-old-key");
        byte[] undecryptable = new byte[1 + 12 + 32];
        undecryptable[0] = 1;
        jdbc.update("UPDATE jira_credential SET encrypted_token = ? WHERE jira_account_id = ?",
                Base64.getEncoder().encodeToString(undecryptable), "557058:abc");

        settings.setSchedulerAccount("557058:abc", Instant.now());

        assertThat(settings.get().schedulerAccountId()).isEqualTo("557058:abc");
    }
}
