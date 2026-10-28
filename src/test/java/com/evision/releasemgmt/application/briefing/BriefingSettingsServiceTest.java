package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.briefing.BriefingSettingsService.TaskUpdate;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.TaskView;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.Update;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.View;
import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingClaudeConfig;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRejectedException;
import com.evision.releasemgmt.domain.briefing.BriefingRejection;
import com.evision.releasemgmt.domain.briefing.BriefingRunSettings;
import com.evision.releasemgmt.domain.briefing.BriefingSettings;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTaskSettings;
import com.evision.releasemgmt.domain.briefing.BriefingThresholds;
import com.evision.releasemgmt.domain.briefing.WorkCalendar;
import com.evision.releasemgmt.ports.ActorDirectory;
import com.evision.releasemgmt.ports.BriefingReadPort;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.BriefingSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BriefingSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T08:00:00Z");
    private static final BriefingClaudeConfig REPO_CONFIG = new BriefingClaudeConfig(true,
            Map.of(BriefingTask.JIRA_DIGEST, Set.of("CNV"), BriefingTask.SPRINT_CHECK, Set.of("CNV"),
                    BriefingTask.WEEKLY_STATUS, Set.of("CNV")), 3, 5, 15, 60);

    private final FakeSettings settings = new FakeSettings();
    private final FakeRuns runs = new FakeRuns();
    private final Map<String, String> emails = new HashMap<>();
    private final List<Collection<String>> directoryLookups = new ArrayList<>();
    private BriefingClaudeConfig config = REPO_CONFIG;
    private Optional<String> currentAccount = Optional.of("acc-me");

    private final BriefingsProperties props = new BriefingsProperties(Path.of("briefings/data"),
            Path.of("briefings/config.json"), null, null, null, null);

    private final BriefingSettingsService service = new BriefingSettingsService(settings, runs, () -> config,
            new BriefingReadPort() {
                @Override
                public BriefingFeed feed(BriefingTask task) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean directoryReadable() {
                    return true;
                }
            }, props, () -> currentAccount, ids -> {
                directoryLookups.add(ids);
                Map<String, String> found = new HashMap<>();
                ids.forEach(id -> { if (emails.containsKey(id)) found.put(id, emails.get(id)); });
                return found;
            }, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void viewShowsSeededSettingsWithConfigDefaults() {
        settings.schedulerAccountId = "acc-me";
        emails.put("acc-me", "me@example.com");

        View view = service.view();

        assertThat(view.graceMinutes()).isEqualTo(30);
        assertThat(view.workDays()).containsExactly("SUN", "MON", "TUE", "WED", "THU");
        assertThat(view.keepRuns()).isEqualTo(60);
        assertThat(view.tasks()).extracting(TaskView::id).containsExactly("jira_digest", "sprint_check", "weekly_status");
        TaskView digest = view.tasks().get(0);
        assertThat(digest.name()).isEqualTo("Daily Jira digest");
        assertThat(digest.days()).containsExactly("SUN", "MON", "TUE", "WED", "THU");
        assertThat(digest.time()).isEqualTo("08:44");
        assertThat(digest.staleAfterWorkingDays()).isEqualTo(3);
        assertThat(digest.dueSoonDays()).isEqualTo(5);
        assertThat(digest.backlogItems()).isEqualTo(15);
        assertThat(view.tasks().get(1).days()).containsExactly("WED");
        assertThat(view.tasks().get(1).time()).isEqualTo("09:41");
        assertThat(view.tasks().get(2).days()).containsExactly("THU");
        assertThat(view.tasks().get(2).time()).isEqualTo("13:41");
        assertThat(view.claudeFeed().dataDir()).isEqualTo(props.dir().toString());
        assertThat(view.claudeFeed().configFile()).isEqualTo(props.config().toString());
        assertThat(view.claudeFeed().readable()).isTrue();
        assertThat(view.schedulerUser().email()).isEqualTo("me@example.com");
        assertThat(view.schedulerUser().isYou()).isTrue();
        assertThat(view.credentialProblem()).isFalse();
    }

    @Test
    void credentialProblemWhenNoSchedulerUserOrNewestRunNeedsCredential() {
        View none = service.view();
        assertThat(none.schedulerUser().email()).isNull();
        assertThat(none.schedulerUser().isYou()).isFalse();
        assertThat(none.credentialProblem()).isTrue();

        settings.schedulerAccountId = "acc-other";
        emails.put("acc-other", "other@example.com");
        assertThat(service.view().schedulerUser().isYou()).isFalse();
        assertThat(service.view().credentialProblem()).isFalse();

        runs.latest = BriefingStatus.NEEDS_CREDENTIAL;
        assertThat(service.view().credentialProblem()).isTrue();
    }

    @Test
    void effectiveUsesDatabaseThenConfigThenDefaults() {
        settings.replaceTask(BriefingTask.JIRA_DIGEST, t -> new BriefingTaskSettings(t.task(), t.enabled(), t.cron(),
                4, null, null));
        settings.workDays = Set.of(MONDAY, TUESDAY);

        BriefingRunSettings digest = service.effective(BriefingTask.JIRA_DIGEST);
        assertThat(digest.thresholds()).isEqualTo(new BriefingThresholds(4, 5, 15));
        assertThat(digest.keepRuns()).isEqualTo(60);
        assertThat(digest.calendar()).isEqualTo(new WorkCalendar(WorkCalendar.CAIRO, Set.of(MONDAY, TUESDAY)));

        config = new BriefingClaudeConfig(true, Map.of(), 7, null, null, 100);
        assertThat(service.effective(BriefingTask.SPRINT_CHECK).thresholds()).isEqualTo(new BriefingThresholds(7, 5, 15));
        assertThat(service.effective(BriefingTask.SPRINT_CHECK).keepRuns()).isEqualTo(100);

        config = BriefingClaudeConfig.UNREADABLE;
        assertThat(service.effective(BriefingTask.WEEKLY_STATUS).thresholds()).isEqualTo(BriefingThresholds.DEFAULTS);
        assertThat(service.effective(BriefingTask.WEEKLY_STATUS).keepRuns()).isEqualTo(BriefingRunSettings.DEFAULT_KEEP_RUNS);

        settings.keepRuns = 25;
        assertThat(service.effective(BriefingTask.WEEKLY_STATUS).keepRuns()).isEqualTo(25);
    }

    @Test
    void schedulesGraceAndTaskSettingsComeFromTheRepository() {
        assertThat(service.grace()).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.schedules()).extracting(BriefingSettingsService.Schedule::cron)
                .containsExactly("0 44 8 * * SUN,MON,TUE,WED,THU", "0 41 9 * * WED", "0 41 13 * * THU");
        assertThat(service.taskSettings(BriefingTask.SPRINT_CHECK).cron()).isEqualTo("0 41 9 * * WED");
    }

    @Test
    void saveStoresCronFromDaysAndTime() {
        service.save(validUpdate());

        assertThat(settings.saved).isTrue();
        assertThat(settings.taskList.get(0).cron()).isEqualTo("0 44 8 * * SUN,MON,TUE,WED,THU");
        assertThat(settings.taskList.get(1).cron()).isEqualTo("0 41 9 * * WED");
        assertThat(settings.taskList.get(2).cron()).isEqualTo("0 41 13 * * THU");
        assertThat(settings.taskList).allSatisfy(t -> assertThat(CronExpression.isValidExpression(t.cron())).isTrue());
        assertThat(settings.updatedBy).isEqualTo("acc-me");
        assertThat(settings.updatedAt).isEqualTo(NOW);
        assertThat(settings.workDays).containsExactlyInAnyOrder(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY);
        assertThat(settings.taskList.get(0).staleAfterWorkingDays()).isEqualTo(3);
    }

    @Test
    void disabledTaskMayHaveNoDaysAndKeepsItsPreviousDays() {
        Update update = withTask(1, t -> new TaskUpdate(t.id(), false, List.of(), "10:05", 3, 5, 15));

        service.save(update);

        assertThat(settings.taskList.get(1).enabled()).isFalse();
        assertThat(settings.taskList.get(1).cron()).isEqualTo("0 5 10 * * WED");
    }

    @Test
    void disabledTaskWithNoDaysAndUnparseablePreviousCronFallsBackToSunday() {
        settings.replaceTask(BriefingTask.WEEKLY_STATUS, t -> new BriefingTaskSettings(t.task(), t.enabled(),
                "0 0 0 1 * *", null, null, null));

        service.save(withTask(2, t -> new TaskUpdate(t.id(), false, List.of(), "13:41", 3, 5, 15)));

        assertThat(settings.taskList.get(2).cron()).isEqualTo("0 41 13 * * SUN");
    }

    @Test
    void saveRejectsStructuralErrors() {
        List<Update> invalid = List.of(
                new Update(30, List.of("SUN"), 60, validUpdate().tasks().subList(0, 2)),
                new Update(30, List.of("SUN"), 60, List.of(validUpdate().tasks().get(0), validUpdate().tasks().get(0),
                        validUpdate().tasks().get(2))),
                new Update(30, List.of("SUN"), 60, List.of(validUpdate().tasks().get(0), validUpdate().tasks().get(1),
                        new TaskUpdate("reg_watch", true, List.of("SUN"), "08:00", 3, 5, 15))),
                new Update(30, List.of("SUNDAY"), 60, validUpdate().tasks()),
                new Update(30, List.of(), 60, validUpdate().tasks()),
                new Update(0, List.of("SUN"), 60, validUpdate().tasks()),
                new Update(241, List.of("SUN"), 60, validUpdate().tasks()),
                new Update(30, List.of("SUN"), 9, validUpdate().tasks()),
                new Update(30, List.of("SUN"), 501, validUpdate().tasks()),
                withTask(0, t -> new TaskUpdate(t.id(), true, List.of(), t.time(), 3, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, List.of("XYZ"), t.time(), 3, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), "24:00", 3, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), "8:44", 3, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), null, 3, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), t.time(), 0, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), t.time(), 31, 5, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), t.time(), 3, 61, 15)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), t.time(), 3, 5, 101)),
                withTask(0, t -> new TaskUpdate(t.id(), true, t.days(), t.time(), 3, 5, 0)));

        for (Update update : invalid) {
            assertThatThrownBy(() -> service.save(update))
                    .isInstanceOfSatisfying(BriefingRejectedException.class,
                            e -> assertThat(e.reason()).isEqualTo(BriefingRejection.BRIEFING_SETTINGS_INVALID));
        }
        assertThat(settings.saved).isFalse();
    }

    @Test
    void designateMeStoresOnlyTheCallersAccountId() {
        emails.put("acc-me", "me@example.com");

        View view = service.designateMe();

        assertThat(settings.schedulerAccountId).isEqualTo("acc-me");
        assertThat(settings.updatedAt).isEqualTo(NOW);
        assertThat(view.schedulerUser().isYou()).isTrue();
        assertThat(directoryLookups.getFirst()).containsExactly("acc-me");
    }

    @Test
    void designateMeRejectsWithoutAStoredCredential() {
        assertThatThrownBy(service::designateMe).isInstanceOfSatisfying(BriefingRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(BriefingRejection.BRIEFING_NO_JIRA_CREDENTIAL));

        currentAccount = Optional.empty();
        assertThatThrownBy(service::designateMe).isInstanceOfSatisfying(BriefingRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(BriefingRejection.BRIEFING_NO_JIRA_CREDENTIAL));
        assertThat(settings.schedulerAccountId).isNull();
    }

    @Test
    void fallbackSwitchDefaultsOffAndRecordsWhoChangedIt() {
        assertThat(service.view().fallbackEnabled()).isFalse();
        assertThat(service.fallbackEnabled()).isFalse();

        BriefingSettings on = service.setFallbackEnabled(true, "acc-me");
        assertThat(on.fallbackEnabled()).isTrue();
        assertThat(service.fallbackEnabled()).isTrue();
        assertThat(on.updatedBy()).isEqualTo("acc-me");
        assertThat(on.updatedAt()).isEqualTo(NOW);
        assertThat(service.view().fallbackEnabled()).isTrue();

        BriefingSettings off = service.setFallbackEnabled(false, "acc-other");
        assertThat(off.fallbackEnabled()).isFalse();
        assertThat(off.updatedBy()).isEqualTo("acc-other");
        assertThat(settings.saved).isFalse();
    }

    @Test
    void serviceHasNoDependencyOnTheCredentialRepository() {
        assertThat(Arrays.stream(BriefingSettingsService.class.getDeclaredFields()).map(f -> f.getType().getName()))
                .doesNotContain("com.evision.releasemgmt.ports.JiraCredentialRepository");
    }

    private static Update validUpdate() {
        return new Update(30, List.of("SUN", "MON", "TUE", "WED", "THU"), 60, List.of(
                new TaskUpdate("jira_digest", true, List.of("SUN", "MON", "TUE", "WED", "THU"), "08:44", 3, 5, 15),
                new TaskUpdate("sprint_check", true, List.of("WED"), "09:41", 3, 5, 15),
                new TaskUpdate("weekly_status", true, List.of("THU"), "13:41", 3, 5, 15)));
    }

    private static Update withTask(int index, UnaryOperator<TaskUpdate> change) {
        Update base = validUpdate();
        List<TaskUpdate> tasks = new ArrayList<>(base.tasks());
        tasks.set(index, change.apply(tasks.get(index)));
        return new Update(base.graceMinutes(), base.workDays(), base.keepRuns(), tasks);
    }

    private static final class FakeSettings implements BriefingSettingsRepository {
        String schedulerAccountId;
        int graceMinutes = 30;
        Set<DayOfWeek> workDays = WorkCalendar.SUN_TO_THU;
        Integer keepRuns;
        boolean fallbackEnabled;
        Instant updatedAt = Instant.EPOCH;
        String updatedBy;
        boolean saved;
        List<BriefingTaskSettings> taskList = new ArrayList<>(List.of(
                new BriefingTaskSettings(BriefingTask.JIRA_DIGEST, true, "0 44 8 * * SUN,MON,TUE,WED,THU", null, null, null),
                new BriefingTaskSettings(BriefingTask.SPRINT_CHECK, true, "0 41 9 * * WED", null, null, null),
                new BriefingTaskSettings(BriefingTask.WEEKLY_STATUS, true, "0 41 13 * * THU", null, null, null)));

        void replaceTask(BriefingTask task, UnaryOperator<BriefingTaskSettings> change) {
            taskList.replaceAll(t -> t.task() == task ? change.apply(t) : t);
        }

        @Override
        public BriefingSettings get() {
            return new BriefingSettings(schedulerAccountId, graceMinutes, workDays, keepRuns, fallbackEnabled, updatedAt,
                    updatedBy);
        }

        @Override
        public void setFallbackEnabled(boolean enabled, Instant at, String by) {
            this.fallbackEnabled = enabled;
            this.updatedAt = at;
            this.updatedBy = by;
        }

        @Override
        public List<BriefingTaskSettings> tasks() {
            return List.copyOf(taskList);
        }

        @Override
        public void saveSchedule(int graceMinutes, Set<DayOfWeek> workDays, Integer keepRuns,
                List<BriefingTaskSettings> tasks, Instant at, String by) {
            this.graceMinutes = graceMinutes;
            this.workDays = workDays;
            this.keepRuns = keepRuns;
            this.taskList = new ArrayList<>(tasks);
            this.updatedAt = at;
            this.updatedBy = by;
            this.saved = true;
        }

        @Override
        public void setSchedulerAccount(String accountId, Instant at) {
            this.schedulerAccountId = accountId;
            this.updatedAt = at;
        }
    }

    private static final class FakeRuns implements BriefingRunRepository {
        BriefingStatus latest = BriefingStatus.OK;

        @Override
        public boolean insert(AppBriefingRun run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsForSlot(BriefingTask task, UUID productId, Instant slotAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Instant> lastSuccessfulFinishedAt(BriefingTask task, UUID productId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AppBriefingRun> findByTask(BriefingTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void prune(BriefingTask task, UUID productId, int keepRuns) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BriefingStatus> latestStatus() {
            return Optional.ofNullable(latest);
        }
    }
}
