package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.BriefingClaudeConfig;
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
import com.evision.releasemgmt.ports.BriefingConfigSource;
import com.evision.releasemgmt.ports.BriefingReadPort;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.BriefingSettingsRepository;
import com.evision.releasemgmt.ports.CurrentUserPort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Briefing schedule and thresholds; any signed-in user may change them (D-17.1-18). */
@Service
public class BriefingSettingsService {

    private static final Pattern CRON = Pattern.compile("^0 (\\d{1,2}) (\\d{1,2}) \\* \\* ([A-Z,]+)$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final List<DayOfWeek> WEEK = List.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    private static final List<BriefingTask> APP_TASKS =
            List.of(BriefingTask.JIRA_DIGEST, BriefingTask.SPRINT_CHECK, BriefingTask.WEEKLY_STATUS);

    public record ClaudeFeed(String dataDir, String configFile, boolean readable) { }

    public record SchedulerUser(String email, boolean isYou) { }

    public record TaskView(String id, String name, boolean enabled, List<String> days, String time,
            int staleAfterWorkingDays, int dueSoonDays, int backlogItems) { }

    public record View(ClaudeFeed claudeFeed, SchedulerUser schedulerUser, boolean credentialProblem,
            boolean fallbackEnabled, int graceMinutes, List<String> workDays, int keepRuns, List<TaskView> tasks) { }

    public record TaskUpdate(String id, boolean enabled, List<String> days, String time,
            int staleAfterWorkingDays, int dueSoonDays, int backlogItems) { }

    public record Update(int graceMinutes, List<String> workDays, int keepRuns, List<TaskUpdate> tasks) { }

    public record Schedule(BriefingTask task, boolean enabled, String cron) { }

    private final BriefingSettingsRepository settings;
    private final BriefingRunRepository runs;
    private final BriefingConfigSource config;
    private final BriefingReadPort feed;
    private final BriefingsProperties props;
    private final CurrentUserPort currentUser;
    private final ActorDirectory actors;
    private final Clock clock;

    public BriefingSettingsService(BriefingSettingsRepository settings, BriefingRunRepository runs,
            BriefingConfigSource config, BriefingReadPort feed, BriefingsProperties props, CurrentUserPort currentUser,
            ActorDirectory actors, Clock clock) {
        this.settings = settings;
        this.runs = runs;
        this.config = config;
        this.feed = feed;
        this.props = props;
        this.currentUser = currentUser;
        this.actors = actors;
        this.clock = clock;
    }

    public View view() {
        BriefingSettings s = settings.get();
        BriefingClaudeConfig claude = config.read();
        Optional<String> me = currentUser.currentAccountId();
        String schedulerId = s.schedulerAccountId();
        String email = schedulerId == null ? null : actors.findEmailsByAccountId(List.of(schedulerId)).get(schedulerId);
        boolean credentialProblem = email == null
                || runs.latestStatus().filter(st -> st == BriefingStatus.NEEDS_CREDENTIAL).isPresent();
        List<TaskView> tasks = settings.tasks().stream().map(t -> {
            BriefingThresholds th = thresholds(t, claude);
            Matcher cron = CRON.matcher(t.cron());
            boolean parsed = cron.matches();
            return new TaskView(t.task().id(), t.task().displayName(), t.enabled(),
                    parsed ? codes(parseDays(cron.group(3)).orElse(Set.of())) : List.of(),
                    parsed ? "%02d:%02d".formatted(Integer.parseInt(cron.group(2)), Integer.parseInt(cron.group(1))) : "",
                    th.staleAfterWorkingDays(), th.dueSoonDays(), th.backlogItems());
        }).toList();
        return new View(new ClaudeFeed(props.dir().toString(), props.config().toString(), feed.directoryReadable()),
                new SchedulerUser(email, email != null && me.filter(schedulerId::equals).isPresent()),
                credentialProblem, s.fallbackEnabled(), s.graceMinutes(), codes(s.workDays()), keepRuns(s, claude), tasks);
    }

    public View save(Update update) {
        if (update == null || update.tasks() == null || update.tasks().size() != APP_TASKS.size()
                || !between(update.graceMinutes(), 1, 240) || !between(update.keepRuns(), 10, 500)) {
            throw invalid();
        }
        Set<DayOfWeek> workDays = parseDays(update.workDays()).filter(d -> !d.isEmpty()).orElseThrow(this::invalid);
        Map<BriefingTask, BriefingTaskSettings> previous = settings.tasks().stream()
                .collect(Collectors.toMap(BriefingTaskSettings::task, t -> t));
        Set<BriefingTask> seen = new HashSet<>();
        List<BriefingTaskSettings> tasks = new ArrayList<>();
        for (TaskUpdate t : update.tasks()) {
            BriefingTask task = Optional.ofNullable(t).flatMap(u -> BriefingTask.fromId(u.id()))
                    .filter(APP_TASKS::contains).filter(seen::add).orElseThrow(this::invalid);
            tasks.add(toSettings(task, t, previous.get(task)));
        }
        tasks.sort((a, b) -> a.task().compareTo(b.task()));
        settings.saveSchedule(update.graceMinutes(), workDays, update.keepRuns(), tasks, clock.instant(),
                currentUser.currentAccountId().orElse(null));
        return view();
    }

    public View designateMe() {
        String me = currentUser.currentAccountId()
                .orElseThrow(() -> new BriefingRejectedException(BriefingRejection.BRIEFING_NO_JIRA_CREDENTIAL));
        if (!actors.findEmailsByAccountId(List.of(me)).containsKey(me)) {
            throw new BriefingRejectedException(BriefingRejection.BRIEFING_NO_JIRA_CREDENTIAL);
        }
        settings.setSchedulerAccount(me, clock.instant());
        return view();
    }

    /** Off by default: the app then never runs a briefing on its own schedule; Run briefing now still works. */
    public BriefingSettings setFallbackEnabled(boolean enabled, String accountId) {
        settings.setFallbackEnabled(enabled, clock.instant(), accountId);
        return settings.get();
    }

    public boolean fallbackEnabled() {
        return settings.get().fallbackEnabled();
    }

    public List<Schedule> schedules() {
        return settings.tasks().stream().map(t -> new Schedule(t.task(), t.enabled(), t.cron())).toList();
    }

    public Duration grace() {
        return Duration.ofMinutes(settings.get().graceMinutes());
    }

    public BriefingRunSettings effective(BriefingTask task) {
        BriefingSettings s = settings.get();
        BriefingClaudeConfig claude = config.read();
        return new BriefingRunSettings(thresholds(taskSettings(task), claude),
                new WorkCalendar(WorkCalendar.CAIRO, s.workDays()), keepRuns(s, claude));
    }

    public BriefingTaskSettings taskSettings(BriefingTask task) {
        return settings.tasks().stream().filter(t -> t.task() == task).findFirst()
                .orElseThrow(() -> new BriefingRejectedException(BriefingRejection.BRIEFING_TASK_NOT_RUNNABLE));
    }

    private BriefingTaskSettings toSettings(BriefingTask task, TaskUpdate t, BriefingTaskSettings previous) {
        if (t.time() == null || !TIME.matcher(t.time()).matches()
                || !between(t.staleAfterWorkingDays(), 1, 30) || !between(t.dueSoonDays(), 1, 60)
                || !between(t.backlogItems(), 1, 100)) {
            throw invalid();
        }
        Set<DayOfWeek> days = parseDays(t.days()).orElseThrow(this::invalid);
        if (days.isEmpty()) {
            if (t.enabled()) {
                throw invalid();
            }
            days = previousDays(previous);
        }
        String cron = "0 %d %d * * %s".formatted(Integer.parseInt(t.time().substring(3)),
                Integer.parseInt(t.time().substring(0, 2)), String.join(",", codes(days)));
        if (!CronExpression.isValidExpression(cron)) {
            throw invalid();
        }
        return new BriefingTaskSettings(task, t.enabled(), cron, t.staleAfterWorkingDays(), t.dueSoonDays(),
                t.backlogItems());
    }

    private static Set<DayOfWeek> previousDays(BriefingTaskSettings previous) {
        if (previous != null) {
            Matcher m = CRON.matcher(previous.cron());
            if (m.matches()) {
                Optional<Set<DayOfWeek>> days = parseDays(m.group(3)).filter(d -> !d.isEmpty());
                if (days.isPresent()) {
                    return days.get();
                }
            }
        }
        return EnumSet.of(DayOfWeek.SUNDAY);
    }

    private static BriefingThresholds thresholds(BriefingTaskSettings t, BriefingClaudeConfig claude) {
        BriefingThresholds d = BriefingThresholds.DEFAULTS;
        return new BriefingThresholds(
                first(t.staleAfterWorkingDays(), claude.staleAfterWorkingDays(), d.staleAfterWorkingDays()),
                first(t.dueSoonDays(), claude.dueSoonDays(), d.dueSoonDays()),
                first(t.backlogItems(), claude.backlogItems(), d.backlogItems()));
    }

    private static int keepRuns(BriefingSettings s, BriefingClaudeConfig claude) {
        return first(s.keepRuns(), claude.keepRuns(), BriefingRunSettings.DEFAULT_KEEP_RUNS);
    }

    private static int first(Integer override, Integer configured, int fallback) {
        return override != null ? override : configured != null ? configured : fallback;
    }

    private static Optional<Set<DayOfWeek>> parseDays(String csv) {
        return parseDays(List.of(csv.split(",")));
    }

    private static Optional<Set<DayOfWeek>> parseDays(List<String> codes) {
        if (codes == null) {
            return Optional.empty();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String code : codes) {
            Optional<DayOfWeek> day = WEEK.stream().filter(d -> code(d).equals(code)).findFirst();
            if (day.isEmpty()) {
                return Optional.empty();
            }
            days.add(day.get());
        }
        return Optional.of(days);
    }

    private static List<String> codes(Set<DayOfWeek> days) {
        return WEEK.stream().filter(days::contains).map(BriefingSettingsService::code).toList();
    }

    private static String code(DayOfWeek d) {
        return d.name().substring(0, 3);
    }

    private static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private BriefingRejectedException invalid() {
        return new BriefingRejectedException(BriefingRejection.BRIEFING_SETTINGS_INVALID);
    }
}
