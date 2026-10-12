package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.DevStatusLinks;
import com.evision.releasemgmt.domain.enrichment.DevStatusUnrecognisedException;
import com.evision.releasemgmt.domain.enrichment.EnrichmentReason;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.JiraDevStatusPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // Service-wide so concurrent runs share one cap, which also protects the shared ADO budget later.
    private final Semaphore permits;
    private final Duration deadline;
    private final int maxCommitsPerTicket;
    private final JiraAccessContextService accessContextService;
    private final JiraDevStatusPort devStatusPort;
    private final ReleaseRepository releaseRepository;

    public EnrichmentService(JiraAccessContextService accessContextService, JiraDevStatusPort devStatusPort,
                             ReleaseRepository releaseRepository,
                             @Value("${app.enrichment.max-concurrent}") int maxConcurrent,
                             @Value("${app.enrichment.deadline}") Duration deadline,
                             @Value("${app.enrichment.max-commits-per-ticket}") int maxCommitsPerTicket) {
        this.accessContextService = accessContextService;
        this.devStatusPort = devStatusPort;
        this.releaseRepository = releaseRepository;
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

        Map<String, Future<CodeLinks>> futures = new LinkedHashMap<>();
        for (TicketDetail ticket : tickets) {
            if (ticket.jiraIssueId() == null) {
                continue;
            }
            CodeLinks previous = previousByKey.getOrDefault(ticket.key(), CodeLinks.notAttempted());
            futures.put(ticket.key(), executor.submit(() -> boundedEnrichOne(access, ticket, previous)));
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

    private CodeLinks boundedEnrichOne(JiraAccessContext access, TicketDetail ticket, CodeLinks previous) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return timedOut(previous);
        }
        try {
            return enrichOne(access, ticket, previous);
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

    CodeLinks enrichOne(JiraAccessContext access, TicketDetail ticket, CodeLinks previous) {
        try {
            DevStatusLinks links = devStatusPort.fetchLinks(access, ticket.jiraIssueId(), ticket.key());
            // release_item_commit.authored_at is NOT NULL, so a commit without an author date cannot be stored.
            List<LinkedCommit> commits = links.keyTaggedCommits().stream()
                    .filter(commit -> commit.authoredAt() != null).toList();
            boolean capped = commits.size() > maxCommitsPerTicket;
            if (capped) {
                // The cap keeps any item under the template render collection limit (ADO-02).
                commits = commits.stream()
                        .sorted(Comparator.comparing(LinkedCommit::authoredAt).reversed())
                        .limit(maxCommitsPerTicket).toList();
            }
            EnrichmentStatus status = links.hasDisplayableLinks() ? EnrichmentStatus.LINKED : EnrichmentStatus.NO_LINK;
            return new CodeLinks(status, capped ? EnrichmentReason.COMMIT_LIMIT : null, capped, Instant.now(),
                    CommitsSource.KEY_TAGGED, links.pullRequests(), commits, links.branches(), previous.history());
        } catch (DevStatusUnrecognisedException e) {
            return failed(previous, EnrichmentReason.DEV_STATUS_UNRECOGNISED);
        } catch (JiraTicketReadException e) {
            return failed(previous, switch (e.reason()) {
                case INVALID_CREDENTIALS, JIRA_FORBIDDEN -> EnrichmentReason.JIRA_UNAUTHORIZED;
                case JIRA_RATE_LIMITED -> EnrichmentReason.JIRA_RATE_LIMITED;
                case JIRA_UNREACHABLE -> EnrichmentReason.JIRA_UNREACHABLE;
                default -> EnrichmentReason.DEV_STATUS_ERROR;
            });
        } catch (RuntimeException e) {
            log.warn("Enrichment of {} failed", ticket.key(), e);
            return failed(previous, EnrichmentReason.DEV_STATUS_ERROR);
        }
    }

    private static CodeLinks failed(CodeLinks previous, EnrichmentReason reason) {
        return previous.failed(reason, Instant.now(), previous.history());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
