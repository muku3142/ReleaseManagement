package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.application.FieldMetadataService;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.briefing.BriefingQueries;
import com.evision.releasemgmt.domain.briefing.BriefingRunSettings;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.domain.briefing.DigestInput;
import com.evision.releasemgmt.domain.briefing.DigestRules;
import com.evision.releasemgmt.domain.briefing.SprintCheckInput;
import com.evision.releasemgmt.domain.briefing.SprintCheckRules;
import com.evision.releasemgmt.domain.briefing.WeeklyInput;
import com.evision.releasemgmt.domain.briefing.WeeklyStatusRules;
import com.evision.releasemgmt.domain.enrichment.StatusTransition;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import com.evision.releasemgmt.ports.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The app-fallback runner: one recorded outcome per product, and never an exception for Jira or credential trouble. */
@Service
public class BriefingRunner {

    private static final Logger log = LoggerFactory.getLogger(BriefingRunner.class);

    static final String NEEDS_CREDENTIAL_HEADLINE = "The scheduler's Jira credential is missing or expired.";
    static final String FAILED_HEADLINE = "This run failed.";
    static final String CLAUDE_COVERED_HEADLINE = "Skipped: Claude covered this slot";
    static final String NO_JIRA_PROJECT_HEADLINE = "Skipped: no Jira project";
    static final String REASON_ERROR = "ERROR";

    private static final int REOPEN_CANDIDATE_CAP = 30;
    private static final int WEEK_DAYS = 7;

    private final ProductRepository products;
    private final BriefingRunRepository runs;
    private final BriefingJiraAccess access;
    private final JiraBriefingPort jira;
    private final JiraIssueHistoryPort history;
    private final FieldMetadataService fields;
    private final BriefingHeadlineService headlines;
    private final Clock clock;

    public BriefingRunner(ProductRepository products, BriefingRunRepository runs, BriefingJiraAccess access,
            JiraBriefingPort jira, JiraIssueHistoryPort history, FieldMetadataService fields,
            BriefingHeadlineService headlines, Clock clock) {
        this.products = products;
        this.runs = runs;
        this.access = access;
        this.jira = jira;
        this.history = history;
        this.fields = fields;
        this.headlines = headlines;
        this.clock = clock;
    }

    public List<AppBriefingRun> runSlot(BriefingTask task, Instant slotAt, Set<String> claudeCoveredKeys,
            BriefingRunSettings settings) {
        return run(task, slotAt, BriefingTrigger.SLOT, claudeCoveredKeys, settings);
    }

    public List<AppBriefingRun> runNow(BriefingTask task, BriefingRunSettings settings) {
        return run(task, null, BriefingTrigger.MANUAL, Set.of(), settings);
    }

    private List<AppBriefingRun> run(BriefingTask task, Instant slotAt, BriefingTrigger trigger,
            Set<String> claudeCoveredKeys, BriefingRunSettings settings) {
        if (!task.appFallback()) {
            throw new IllegalArgumentException("no app fallback for " + task.id());
        }
        Map<UUID, List<String>> keysByProduct = products.jiraProjectKeys();
        List<Product> active = products.findAll().stream()
                .filter(product -> !product.archived())
                .sorted(Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        JiraSession session = new JiraSession();
        List<AppBriefingRun> recorded = new ArrayList<>();
        for (Product product : active) {
            if (slotAt != null && runs.existsForSlot(task, product.id(), slotAt)) {
                continue;
            }
            List<String> keys = keysByProduct.getOrDefault(product.id(), List.of());
            AppBriefingRun outcome;
            if (keys.isEmpty()) {
                outcome = marker(task, product, slotAt, trigger, BriefingStatus.SKIPPED,
                        AppBriefingRun.REASON_NO_JIRA_PROJECT, NO_JIRA_PROJECT_HEADLINE);
            } else if (trigger == BriefingTrigger.SLOT && claudeCoveredKeys.containsAll(keys)) {
                outcome = marker(task, product, slotAt, trigger, BriefingStatus.SKIPPED,
                        AppBriefingRun.REASON_CLAUDE_COVERED, CLAUDE_COVERED_HEADLINE);
            } else {
                outcome = jiraRun(task, product, keys, slotAt, trigger, settings, session);
            }
            if (runs.insert(outcome)) {
                runs.prune(task, product.id(), settings.keepRuns());
                recorded.add(outcome);
            }
        }
        return recorded;
    }

    private AppBriefingRun jiraRun(BriefingTask task, Product product, List<String> keys, Instant slotAt,
            BriefingTrigger trigger, BriefingRunSettings settings, JiraSession session) {
        Instant startedAt = clock.instant();
        if (session.credentialRejected) {
            return marker(task, product, slotAt, trigger, BriefingStatus.NEEDS_CREDENTIAL, null,
                    NEEDS_CREDENTIAL_HEADLINE, startedAt);
        }
        try {
            Optional<JiraAccessContext> context = session.context();
            if (context.isEmpty()) {
                session.credentialRejected = true;
                return marker(task, product, slotAt, trigger, BriefingStatus.NEEDS_CREDENTIAL, null,
                        NEEDS_CREDENTIAL_HEADLINE, startedAt);
            }
            BriefingContent content = compute(task, product, keys, settings, context.get(),
                    session.fieldIds(context.get()), session, startedAt);
            BriefingContent enriched = headlines.enrich(content, product.name());
            AppBriefingRun run = new AppBriefingRun(UUID.randomUUID(), task, product.id(), slotAt, trigger,
                    enriched.status(), null, enriched.attention(), enriched.headline(), enriched.summary(),
                    enriched.sections(), startedAt, clock.instant());
            logRun(run, null);
            return run;
        } catch (JiraAuthenticationException e) {
            if (e.reason() == JiraAuthFailureReason.INVALID_CREDENTIALS) {
                // One 401 means every later call in this slot would be rejected too; stop hitting Jira.
                session.credentialRejected = true;
                return marker(task, product, slotAt, trigger, BriefingStatus.NEEDS_CREDENTIAL, null,
                        NEEDS_CREDENTIAL_HEADLINE, startedAt);
            }
            return failed(task, product, slotAt, trigger, e.reason().name(), startedAt, e);
        } catch (RuntimeException e) {
            return failed(task, product, slotAt, trigger, REASON_ERROR, startedAt, e);
        }
    }

    private BriefingContent compute(BriefingTask task, Product product, List<String> keys,
            BriefingRunSettings settings, JiraAccessContext context, BriefingFieldIds fieldIds, JiraSession session,
            Instant now) {
        return switch (task) {
            case JIRA_DIGEST -> {
                Instant since = runs.lastSuccessfulFinishedAt(task, product.id())
                        .orElseGet(() -> settings.calendar().previousWorkingDayStart(now));
                int daysBack = BriefingQueries.daysBack(since, now);
                List<BriefingIssue> reopenCandidates = search(context,
                        BriefingQueries.reopenCandidates(keys, daysBack), fieldIds, REOPEN_CANDIDATE_CAP);
                yield DigestRules.compute(new DigestInput(product.name(),
                        search(context, BriefingQueries.activeSprintOpen(keys), fieldIds),
                        search(context, BriefingQueries.staleCandidates(keys,
                                settings.thresholds().staleAfterWorkingDays()), fieldIds),
                        search(context, BriefingQueries.nextSprint(keys), fieldIds),
                        search(context, BriefingQueries.createdSince(keys, daysBack), fieldIds),
                        search(context, BriefingQueries.doneSince(keys, daysBack), fieldIds),
                        reopened(context, reopenCandidates, since, session),
                        search(context, BriefingQueries.dueWithin(keys, settings.thresholds().dueSoonDays()),
                                fieldIds),
                        since, now, settings, fieldIds));
            }
            case SPRINT_CHECK -> SprintCheckRules.compute(new SprintCheckInput(product.name(),
                    search(context, BriefingQueries.nextSprint(keys), fieldIds),
                    search(context, BriefingQueries.backlog(keys), fieldIds, settings.thresholds().backlogItems()),
                    settings, fieldIds));
            case WEEKLY_STATUS -> WeeklyStatusRules.compute(new WeeklyInput(product.name(),
                    search(context, BriefingQueries.doneSince(keys, WEEK_DAYS), fieldIds),
                    search(context, BriefingQueries.inProgress(keys), fieldIds),
                    search(context, BriefingQueries.activeSprintAll(keys), fieldIds),
                    search(context, BriefingQueries.createdSince(keys, WEEK_DAYS), fieldIds),
                    search(context, BriefingQueries.dueWithin(keys, 0), fieldIds),
                    now, settings));
            case REG_WATCH, GULF_INVEST -> throw new IllegalArgumentException("no app fallback for " + task.id());
        };
    }

    private List<BriefingIssue> search(JiraAccessContext context, String jql, BriefingFieldIds fieldIds) {
        return search(context, jql, fieldIds, BriefingQueries.UNCAPPED);
    }

    private List<BriefingIssue> search(JiraAccessContext context, String jql, BriefingFieldIds fieldIds, int max) {
        return jira.search(context, jql, fieldIds, max);
    }

    // Reopened = moved out of a Done-category status since the last run; ids, not names, because names collide.
    private List<BriefingIssue> reopened(JiraAccessContext context, List<BriefingIssue> candidates, Instant since,
            JiraSession session) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, StatusCategory> categories = session.statusCategories(context);
        return candidates.stream().filter(issue -> {
            List<StatusTransition> transitions = history.statusTransitions(context, issue.key());
            return transitions.stream().anyMatch(t -> !t.at().isBefore(since)
                    && categories.get(t.fromStatusId()) == StatusCategory.DONE
                    && categories.get(t.toStatusId()) != StatusCategory.DONE);
        }).toList();
    }

    private AppBriefingRun failed(BriefingTask task, Product product, Instant slotAt, BriefingTrigger trigger,
            String reason, Instant startedAt, RuntimeException e) {
        AppBriefingRun run = new AppBriefingRun(UUID.randomUUID(), task, product.id(), slotAt, trigger,
                BriefingStatus.FAILED, reason, null, FAILED_HEADLINE, null, List.of(), startedAt, clock.instant());
        logRun(run, e);
        return run;
    }

    private AppBriefingRun marker(BriefingTask task, Product product, Instant slotAt, BriefingTrigger trigger,
            BriefingStatus status, String reason, String headline) {
        return marker(task, product, slotAt, trigger, status, reason, headline, clock.instant());
    }

    private AppBriefingRun marker(BriefingTask task, Product product, Instant slotAt, BriefingTrigger trigger,
            BriefingStatus status, String reason, String headline, Instant startedAt) {
        AppBriefingRun run = new AppBriefingRun(UUID.randomUUID(), task, product.id(), slotAt, trigger, status,
                reason, null, headline, null, List.of(), startedAt, clock.instant());
        logRun(run, null);
        return run;
    }

    private static void logRun(AppBriefingRun run, RuntimeException e) {
        long durationMs = Duration.between(run.startedAt(), run.finishedAt()).toMillis();
        if (e == null) {
            log.info("briefing run task={} productId={} status={} reason={} attention={} durationMs={}",
                    run.task().id(), run.productId(), run.status().wire(), run.reason(), run.attention(), durationMs);
        } else {
            log.warn("briefing run task={} productId={} status={} reason={} durationMs={} exceptionType={}",
                    run.task().id(), run.productId(), run.status().wire(), run.reason(), durationMs,
                    e.getClass().getSimpleName());
        }
    }

    // Per-call Jira state, loaded lazily and never kept between calls so a rotated token is picked up next run.
    private final class JiraSession {
        private Optional<JiraAccessContext> context;
        private BriefingFieldIds fieldIds;
        private Map<String, StatusCategory> statusCategories;
        private boolean credentialRejected;

        Optional<JiraAccessContext> context() {
            if (context == null) {
                context = access.designatedContext();
            }
            return context;
        }

        BriefingFieldIds fieldIds(JiraAccessContext ctx) {
            if (fieldIds == null) {
                Map<String, String> ids = fields.resolveOptional(ctx, FieldMetadataService.BRIEFING_FIELD_NAMES);
                fieldIds = new BriefingFieldIds(id(ids, "Story Points"), id(ids, "Flagged"), id(ids, "Sprint"),
                        id(ids, "Acceptance Criteria"));
            }
            return fieldIds;
        }

        Map<String, StatusCategory> statusCategories(JiraAccessContext ctx) {
            if (statusCategories == null) {
                statusCategories = history.statusCategories(ctx);
            }
            return statusCategories;
        }

        private static String id(Map<String, String> ids, String name) {
            return ids.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
