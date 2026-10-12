package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.domain.ado.AdoCredentialState;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.enrichment.AdoRepoRef;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.DevStatusLinks;
import com.evision.releasemgmt.domain.enrichment.DevStatusUnrecognisedException;
import com.evision.releasemgmt.domain.enrichment.EnrichmentReason;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.StatusChange;
import com.evision.releasemgmt.domain.enrichment.StatusHistory;
import com.evision.releasemgmt.domain.enrichment.StatusTransition;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.AdoCredentialRepository;
import com.evision.releasemgmt.ports.AdoRepositoryPort;
import com.evision.releasemgmt.ports.JiraDevStatusPort;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // Service-wide so concurrent runs share one cap, which also protects the shared ADO budget.
    private final Semaphore permits;
    private final Duration deadline;
    private final int maxCommitsPerTicket;
    private final JiraAccessContextService accessContextService;
    private final JiraDevStatusPort devStatusPort;
    private final AdoCredentialRepository adoCredentialRepository;
    private final AdoRepositoryPort adoRepositoryPort;
    private final ReleaseRepository releaseRepository;
    private final JiraIssueHistoryPort historyPort;
    private final StatusCategoryService statusCategoryService;

    public EnrichmentService(JiraAccessContextService accessContextService, JiraDevStatusPort devStatusPort,
                             AdoCredentialRepository adoCredentialRepository, AdoRepositoryPort adoRepositoryPort,
                             ReleaseRepository releaseRepository, JiraIssueHistoryPort historyPort,
                             StatusCategoryService statusCategoryService,
                             @Value("${app.enrichment.max-concurrent}") int maxConcurrent,
                             @Value("${app.enrichment.deadline}") Duration deadline,
                             @Value("${app.enrichment.max-commits-per-ticket}") int maxCommitsPerTicket) {
        this.accessContextService = accessContextService;
        this.devStatusPort = devStatusPort;
        this.adoCredentialRepository = adoCredentialRepository;
        this.adoRepositoryPort = adoRepositoryPort;
        this.releaseRepository = releaseRepository;
        this.historyPort = historyPort;
        this.statusCategoryService = statusCategoryService;
        this.permits = new Semaphore(maxConcurrent);
        this.deadline = deadline;
        this.maxCommitsPerTicket = maxCommitsPerTicket;
    }

    /**
     * D-06-01/D-06-04: discovers and stores code links for the given tickets of one release. Per-item failures become
     * FAILED items and never escape; only the final write can throw (ReleaseFrozenException, ReleaseNotFoundException).
     */
    public void enrich(UUID releaseId, List<TicketDetail> tickets) {
        if (tickets.isEmpty()) {
            return;
        }
        // Virtual workers do not inherit the SecurityContext, so the credential is resolved here and passed by value.
        JiraAccessContext access = accessContextService.currentContext();
        Map<String, CodeLinks> previousByKey = previousCodeLinks(releaseId);
        AdoRun adoRun = adoRun();

        Map<String, Future<CodeLinks>> futures = new LinkedHashMap<>();
        for (TicketDetail ticket : tickets) {
            if (ticket.jiraIssueId() == null) {
                continue;
            }
            CodeLinks previous = previousByKey.getOrDefault(ticket.key(), CodeLinks.notAttempted());
            futures.put(ticket.key(), executor.submit(
                    () -> bounded(previous, () -> enrichOne(access, ticket, previous, adoRun))));
        }

        long deadlineNanos = System.nanoTime() + deadline.toNanos();
        Map<String, CodeLinks> results = new HashMap<>();
        futures.forEach((key, future) -> {
            CodeLinks previous = previousByKey.getOrDefault(key, CodeLinks.notAttempted());
            results.put(key, await(future, deadlineNanos, previous));
        });
        releaseRepository.saveEnrichment(releaseId, results);
    }

    private Map<String, CodeLinks> previousCodeLinks(UUID releaseId) {
        Map<String, CodeLinks> previous = new HashMap<>();
        releaseRepository.findById(releaseId).map(Release::items).ifPresent(items ->
                items.forEach(item -> previous.put(item.ticketKey(), item.codeLinks())));
        return previous;
    }

    // Read once on the request thread; the token never reaches a log or an exception message.
    private AdoRun adoRun() {
        Map<String, CompletableFuture<List<LinkedCommit>>> prCommits = new ConcurrentHashMap<>();
        try {
            Optional<AdoCredential> credential = adoCredentialRepository.findForUse();
            boolean expired = credential.isPresent()
                    && AdoCredentialState.of(credential.get().expiresOn(), LocalDate.now(ZoneOffset.UTC))
                    == AdoCredentialState.EXPIRED;
            return new AdoRun(credential, expired ? EnrichmentReason.ADO_CREDENTIAL_EXPIRED : null, prCommits);
        } catch (IllegalStateException e) {
            return new AdoRun(Optional.empty(), EnrichmentReason.ADO_CREDENTIAL_UNREADABLE, prCommits);
        }
    }

    // On-demand ticket-browser lookup for a ticket that is not in a release: stores nothing, so the status history
    // (which has nowhere to be kept) is skipped.
    public CodeLinks lookup(TicketDetail ticket) {
        if (ticket.jiraIssueId() == null) {
            return CodeLinks.notAttempted();
        }
        JiraAccessContext access = accessContextService.currentContext();
        AdoRun adoRun = adoRun();
        CodeLinks none = CodeLinks.notAttempted();
        Future<CodeLinks> future = executor.submit(() -> bounded(none,
                () -> discover(access, ticket, none, adoRun, StatusHistory.notRead())));
        return await(future, System.nanoTime() + deadline.toNanos(), none);
    }

    private CodeLinks bounded(CodeLinks previous, Supplier<CodeLinks> work) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return timedOut(previous);
        }
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }

    private CodeLinks await(Future<CodeLinks> future, long deadlineNanos, CodeLinks previous) {
        try {
            return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return timedOut(previous);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return timedOut(previous);
        } catch (ExecutionException e) {
            log.warn("Enrichment worker failed", e);
            return previous.failed(EnrichmentReason.DEV_STATUS_ERROR, Instant.now(), previous.history());
        }
    }

    private static CodeLinks timedOut(CodeLinks previous) {
        return previous.failed(EnrichmentReason.TIMEOUT, Instant.now(), previous.history());
    }

    CodeLinks enrichOne(JiraAccessContext access, TicketDetail ticket, CodeLinks previous, AdoRun adoRun) {
        return discover(access, ticket, previous, adoRun, statusHistory(access, ticket, previous.history()));
    }

    private CodeLinks discover(JiraAccessContext access, TicketDetail ticket, CodeLinks previous, AdoRun adoRun,
                               StatusHistory history) {
        try {
            DevStatusLinks links = devStatusPort.fetchLinks(access, ticket.jiraIssueId(), ticket.key());
            // release_item_commit.authored_at is NOT NULL, so a commit without an author date cannot be stored.
            List<LinkedCommit> commits = links.keyTaggedCommits().stream()
                    .filter(commit -> commit.authoredAt() != null).toList();
            EnrichmentStatus status = links.hasDisplayableLinks() ? EnrichmentStatus.LINKED : EnrichmentStatus.NO_LINK;
            CodeLinks result = new CodeLinks(status, null, false, Instant.now(), CommitsSource.KEY_TAGGED,
                    links.pullRequests(), commits, links.branches(), history);
            return capCommits(status == EnrichmentStatus.LINKED ? completeFromAdo(result, previous, adoRun) : result);
        } catch (DevStatusUnrecognisedException e) {
            return failed(previous, EnrichmentReason.DEV_STATUS_UNRECOGNISED, history);
        } catch (JiraTicketReadException e) {
            return failed(previous, switch (e.reason()) {
                case INVALID_CREDENTIALS, JIRA_FORBIDDEN -> EnrichmentReason.JIRA_UNAUTHORIZED;
                case JIRA_RATE_LIMITED -> EnrichmentReason.JIRA_RATE_LIMITED;
                case JIRA_UNREACHABLE -> EnrichmentReason.JIRA_UNREACHABLE;
                default -> EnrichmentReason.DEV_STATUS_ERROR;
            }, history);
        } catch (RuntimeException e) {
            log.warn("Enrichment of {} failed", ticket.key(), e);
            return failed(previous, EnrichmentReason.DEV_STATUS_ERROR, history);
        }
    }

    // D-06-08: history is independent of code links and never fails the item. The recorded updated time is the one from
    // the SEARCH response, so a change between search and changelog read is caught on the next run.
    private StatusHistory statusHistory(JiraAccessContext access, TicketDetail ticket, StatusHistory previous) {
        if (!previous.needsRead(ticket.updatedAt())) {
            return previous;
        }
        try {
            List<StatusTransition> transitions = historyPort.statusTransitions(access, ticket.key());
            Set<String> ids = new HashSet<>();
            for (StatusTransition transition : transitions) {
                if (transition.fromStatusId() != null) {
                    ids.add(transition.fromStatusId());
                }
                ids.add(transition.toStatusId());
            }
            Map<String, StatusCategory> categories = statusCategoryService.categoriesFor(access, ids);
            List<StatusChange> changes = transitions.stream()
                    .map(t -> new StatusChange(t.at(), t.fromStatusId(), t.toStatusId(),
                            t.fromStatusId() == null ? null : categories.get(t.fromStatusId()),
                            categories.get(t.toStatusId())))
                    .toList();
            return StatusHistory.derive(changes, ticket.updatedAt());
        } catch (RuntimeException e) {
            log.warn("Status history of {} unavailable", ticket.key(), e);
            return StatusHistory.unavailable();
        }
    }

    // The cap keeps any item under the template render collection limit (ADO-02).
    private CodeLinks capCommits(CodeLinks links) {
        if (links.commits().size() <= maxCommitsPerTicket) {
            return links;
        }
        List<LinkedCommit> newest = links.commits().stream()
                .sorted(Comparator.comparing(LinkedCommit::authoredAt).reversed())
                .limit(maxCommitsPerTicket).toList();
        return new CodeLinks(links.status(), links.reason() != null ? links.reason() : EnrichmentReason.COMMIT_LIMIT,
                true, links.attemptedAt(), links.commitsSource(), links.pullRequests(), newest, links.branches(),
                links.history());
    }

    // D-06-09/D-06-11: ADO failures never produce FAILED; the PAT only adds completeness to a LINKED item.
    private CodeLinks completeFromAdo(CodeLinks links, CodeLinks previous, AdoRun run) {
        if (run.problem() != null) {
            return flagged(links, run.problem());
        }
        if (run.credential().isEmpty()) {
            return links;
        }
        List<LinkedPullRequest> counting = links.pullRequests().stream()
                .filter(LinkedPullRequest::countsTowardCommits).toList();
        if (counting.isEmpty()) {
            return links;
        }
        if (isClosedAndFetched(previous, counting)) {
            return reuseFetched(links, previous);
        }
        return fetchFromAdo(links, counting, run);
    }

    private static boolean isClosedAndFetched(CodeLinks previous, List<LinkedPullRequest> counting) {
        if (previous.status() != EnrichmentStatus.LINKED || previous.commitsSource() != CommitsSource.ADO_COMPLETE) {
            return false;
        }
        List<LinkedPullRequest> previousCounting = previous.pullRequests().stream()
                .filter(LinkedPullRequest::countsTowardCommits).toList();
        Set<String> previousIds = previousCounting.stream().map(LinkedPullRequest::id).collect(Collectors.toSet());
        Set<String> currentIds = counting.stream().map(LinkedPullRequest::id).collect(Collectors.toSet());
        return previousIds.equals(currentIds) && previousCounting.stream()
                .allMatch(pr -> pr.commitsFetchedAt() != null && pr.status() != null
                        && !"OPEN".equalsIgnoreCase(pr.status()));
    }

    private static CodeLinks reuseFetched(CodeLinks links, CodeLinks previous) {
        Map<String, Instant> fetchedAt = previous.pullRequests().stream()
                .filter(pr -> pr.commitsFetchedAt() != null)
                .collect(Collectors.toMap(LinkedPullRequest::id, LinkedPullRequest::commitsFetchedAt, (a, b) -> a));
        List<LinkedPullRequest> pullRequests = links.pullRequests().stream()
                .map(pr -> pr.countsTowardCommits() ? stamped(pr, fetchedAt.get(pr.id())) : pr).toList();
        return completed(links, pullRequests, union(previous.commits(), links.commits()));
    }

    private CodeLinks fetchFromAdo(CodeLinks links, List<LinkedPullRequest> counting, AdoRun run) {
        AdoCredential credential = run.credential().orElseThrow();
        // Start every PR first so one ticket's PRs overlap; a PR shared with another ticket is fetched once per run.
        Map<String, CompletableFuture<List<LinkedCommit>>> started = new LinkedHashMap<>();
        for (LinkedPullRequest pr : counting) {
            Optional<AdoRepoRef> ref = AdoRepoRef.fromPullRequestUrl(pr.url());
            if (ref.isEmpty()) {
                continue;
            }
            if (!ref.get().organization().equalsIgnoreCase(credential.organization())) {
                return flagged(links, EnrichmentReason.ADO_ORG_MISMATCH);
            }
            String key = ref.get().organization() + "|" + pr.repositoryId() + "|" + pr.id();
            // No permit here: the calling worker already holds one.
            started.put(pr.id(), run.prCommits().computeIfAbsent(key, k -> CompletableFuture.supplyAsync(
                    () -> adoRepositoryPort.pullRequestCommits(credential, ref.get(), pr.repositoryId(), pr.id()),
                    executor)));
        }
        if (started.isEmpty()) {
            return links;
        }
        List<LinkedCommit> adoCommits = new ArrayList<>();
        for (CompletableFuture<List<LinkedCommit>> future : started.values()) {
            try {
                adoCommits.addAll(future.join());
            } catch (CompletionException e) {
                return flagged(links, adoReason(e.getCause()));
            }
        }
        Instant now = Instant.now();
        List<LinkedPullRequest> pullRequests = links.pullRequests().stream()
                .map(pr -> pr.countsTowardCommits() && started.containsKey(pr.id()) ? stamped(pr, now) : pr).toList();
        return completed(links, pullRequests, union(adoCommits, links.commits()));
    }

    private static EnrichmentReason adoReason(Throwable cause) {
        if (cause instanceof AdoAccessException e) {
            return switch (e.reason()) {
                case UNAUTHORIZED -> EnrichmentReason.ADO_UNAUTHORIZED;
                case NOT_FOUND -> EnrichmentReason.ADO_NOT_FOUND;
                case RATE_LIMITED -> EnrichmentReason.ADO_RATE_LIMITED;
                case UNREACHABLE -> EnrichmentReason.ADO_UNREACHABLE;
                case TOO_LARGE -> EnrichmentReason.COMMIT_LIMIT;
                case ERROR -> EnrichmentReason.ADO_ERROR;
            };
        }
        return EnrichmentReason.ADO_ERROR;
    }

    private static LinkedPullRequest stamped(LinkedPullRequest pr, Instant fetchedAt) {
        return new LinkedPullRequest(pr.repositoryId(), pr.id(), pr.title(), pr.status(), pr.url(), pr.sourceBranch(),
                pr.targetBranch(), pr.countsTowardCommits(), fetchedAt);
    }

    // First list wins on a shared sha, so ADO's full record is preferred over the key-tagged one.
    private static List<LinkedCommit> union(List<LinkedCommit> first, List<LinkedCommit> second) {
        Map<String, LinkedCommit> bySha = new LinkedHashMap<>();
        first.forEach(commit -> bySha.putIfAbsent(commit.sha(), commit));
        second.forEach(commit -> bySha.putIfAbsent(commit.sha(), commit));
        return List.copyOf(bySha.values());
    }

    private static CodeLinks flagged(CodeLinks links, EnrichmentReason reason) {
        return new CodeLinks(links.status(), reason, true, links.attemptedAt(), links.commitsSource(),
                links.pullRequests(), links.commits(), links.branches(), links.history());
    }

    private static CodeLinks completed(CodeLinks links, List<LinkedPullRequest> pullRequests,
                                       List<LinkedCommit> commits) {
        return new CodeLinks(links.status(), null, false, links.attemptedAt(), CommitsSource.ADO_COMPLETE,
                pullRequests, commits, links.branches(), links.history());
    }

    private static CodeLinks failed(CodeLinks previous, EnrichmentReason reason, StatusHistory history) {
        return previous.failed(reason, Instant.now(), history);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record AdoRun(Optional<AdoCredential> credential, EnrichmentReason problem,
                          Map<String, CompletableFuture<List<LinkedCommit>>> prCommits) {
    }
}
