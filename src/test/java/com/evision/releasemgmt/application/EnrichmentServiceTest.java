package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.DevStatusLinks;
import com.evision.releasemgmt.domain.enrichment.DevStatusUnrecognisedException;
import com.evision.releasemgmt.domain.enrichment.EnrichmentReason;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.StatusHistory;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.ports.JiraDevStatusPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrichmentServiceTest {

    private static final JiraAccessContext ACCESS = new JiraAccessContext("https://x.atlassian.net", "acc", "a@b.c", "tok");
    private static final UUID RELEASE_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private JiraAccessContextService accessService;
    private JiraDevStatusPort port;
    private ReleaseRepository repository;

    @BeforeEach
    void setUp() {
        accessService = mock(JiraAccessContextService.class);
        port = mock(JiraDevStatusPort.class);
        repository = mock(ReleaseRepository.class);
        when(accessService.currentContext()).thenReturn(ACCESS);
        when(repository.findById(RELEASE_ID)).thenReturn(Optional.empty());
    }

    private EnrichmentService service(int concurrent, Duration deadline, int cap) {
        return new EnrichmentService(accessService, port, repository, concurrent, deadline, cap);
    }

    private static TicketDetail ticket(String key, String issueId) {
        return new TicketDetail(key, "s", "Bug", "Done", null, null, null, List.of(), null, "", List.of(), 0,
                issueId, T0, StatusCategory.DONE);
    }

    private static LinkedPullRequest pr(String id) {
        return new LinkedPullRequest("repo", id, "t", "MERGED", "u", "feature/x", "main", true, null);
    }

    private static LinkedCommit commit(String sha, Instant at) {
        return new LinkedCommit(sha, "msg", "dev", at, "u");
    }

    @SuppressWarnings("unchecked")
    private Map<String, CodeLinks> saved() {
        ArgumentCaptor<Map<String, CodeLinks>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repository, times(1)).saveEnrichment(eq(RELEASE_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void linksWithPullRequestsAreLinkedAndKeyTagged() {
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenReturn(
                new DevStatusLinks(List.of(pr("5")), List.of(commit("abc", T0)), List.of("feature/x")));

        service(2, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, List.of(ticket("EX-1", "1")));

        CodeLinks result = saved().get("EX-1");
        assertThat(result.status()).isEqualTo(EnrichmentStatus.LINKED);
        assertThat(result.commitsSource()).isEqualTo(CommitsSource.KEY_TAGGED);
        assertThat(result.attemptedAt()).isNotNull();
        assertThat(result.pullRequests()).hasSize(1);
        assertThat(result.commits()).hasSize(1);
        assertThat(result.branches()).containsExactly("feature/x");
        assertThat(result.incomplete()).isFalse();
    }

    @Test
    void branchOnlyAndEmptyLinksAreNoLinkButBranchIsKept() {
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenReturn(new DevStatusLinks(List.of(), List.of(), List.of("b")));
        when(port.fetchLinks(ACCESS, "2", "EX-2")).thenReturn(DevStatusLinks.none());

        service(2, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, List.of(ticket("EX-1", "1"), ticket("EX-2", "2")));

        Map<String, CodeLinks> results = saved();
        assertThat(results.get("EX-1").status()).isEqualTo(EnrichmentStatus.NO_LINK);
        assertThat(results.get("EX-1").branches()).containsExactly("b");
        assertThat(results.get("EX-2").status()).isEqualTo(EnrichmentStatus.NO_LINK);
    }

    @Test
    void failuresAreMappedToReasons() {
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenThrow(new DevStatusUnrecognisedException());
        when(port.fetchLinks(ACCESS, "2", "EX-2")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        when(port.fetchLinks(ACCESS, "3", "EX-3")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_FORBIDDEN));
        when(port.fetchLinks(ACCESS, "4", "EX-4")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED));
        when(port.fetchLinks(ACCESS, "5", "EX-5")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));
        when(port.fetchLinks(ACCESS, "6", "EX-6")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR));
        when(port.fetchLinks(ACCESS, "7", "EX-7")).thenThrow(new IllegalStateException("boom"));

        service(3, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, IntStream.rangeClosed(1, 7)
                .mapToObj(i -> ticket("EX-" + i, String.valueOf(i))).toList());

        Map<String, CodeLinks> results = saved();
        assertThat(results.get("EX-1").reason()).isEqualTo(EnrichmentReason.DEV_STATUS_UNRECOGNISED);
        assertThat(results.get("EX-2").reason()).isEqualTo(EnrichmentReason.JIRA_UNAUTHORIZED);
        assertThat(results.get("EX-3").reason()).isEqualTo(EnrichmentReason.JIRA_UNAUTHORIZED);
        assertThat(results.get("EX-4").reason()).isEqualTo(EnrichmentReason.JIRA_RATE_LIMITED);
        assertThat(results.get("EX-5").reason()).isEqualTo(EnrichmentReason.JIRA_UNREACHABLE);
        assertThat(results.get("EX-6").reason()).isEqualTo(EnrichmentReason.DEV_STATUS_ERROR);
        assertThat(results.get("EX-7").reason()).isEqualTo(EnrichmentReason.DEV_STATUS_ERROR);
        assertThat(results.values()).allMatch(r -> r.status() == EnrichmentStatus.FAILED);
    }

    @Test
    void failureKeepsPreviousLinksAndHistory() {
        StatusHistory history = StatusHistory.unavailable();
        CodeLinks previous = new CodeLinks(EnrichmentStatus.LINKED, null, false, T0, CommitsSource.KEY_TAGGED,
                List.of(pr("5")), List.of(commit("abc", T0)), List.of(), history);
        Release release = new Release(RELEASE_ID, "r", "1", com.evision.releasemgmt.domain.release.ReleaseStatus.OPEN,
                null, T0, "acc", null, null, null, null,
                List.of(new ReleaseItem(ticket("EX-1", "1"), T0, previous)), List.of());
        when(repository.findById(RELEASE_ID)).thenReturn(Optional.of(release));
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        service(2, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, List.of(ticket("EX-1", "1")));

        CodeLinks result = saved().get("EX-1");
        assertThat(result.status()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(result.pullRequests()).hasSize(1);
        assertThat(result.commits()).hasSize(1);
        assertThat(result.history()).isSameAs(history);
    }

    @Test
    void ticketWithoutIssueIdIsSkipped() {
        service(2, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, List.of(ticket("EX-1", null)));

        assertThat(saved()).isEmpty();
        verify(port, never()).fetchLinks(any(), anyString(), anyString());
    }

    @Test
    void emptyTicketListDoesNothing() {
        service(2, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, List.of());

        verify(repository, never()).saveEnrichment(any(), anyMap());
        verify(accessService, never()).currentContext();
    }

    @Test
    void concurrencyNeverExceedsTheCap() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        when(port.fetchLinks(any(), anyString(), anyString())).thenAnswer(invocation -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            Thread.sleep(50);
            inFlight.decrementAndGet();
            return DevStatusLinks.none();
        });

        List<TicketDetail> tickets = new ArrayList<>();
        IntStream.rangeClosed(1, 5).forEach(i -> tickets.add(ticket("EX-" + i, String.valueOf(i))));
        service(2, Duration.ofSeconds(10), 400).enrich(RELEASE_ID, tickets);

        assertThat(maxInFlight.get()).isEqualTo(2);
        assertThat(saved()).hasSize(5);
    }

    @Test
    void ticketThatNeverReturnsTimesOutAndOthersAreKept() {
        CountDownLatch never = new CountDownLatch(1);
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenAnswer(invocation -> {
            never.await();
            return DevStatusLinks.none();
        });
        when(port.fetchLinks(ACCESS, "2", "EX-2")).thenReturn(
                new DevStatusLinks(List.of(pr("9")), List.of(), List.of()));

        service(6, Duration.ofMillis(200), 400).enrich(RELEASE_ID, List.of(ticket("EX-1", "1"), ticket("EX-2", "2")));

        Map<String, CodeLinks> results = saved();
        assertThat(results.get("EX-1").status()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(results.get("EX-1").reason()).isEqualTo(EnrichmentReason.TIMEOUT);
        assertThat(results.get("EX-2").status()).isEqualTo(EnrichmentStatus.LINKED);
    }

    @Test
    void commitsWithoutAnAuthorDateAreDropped() {
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenReturn(
                new DevStatusLinks(List.of(), List.of(commit("a", null), commit("b", T0)), List.of()));

        service(2, Duration.ofSeconds(5), 400).enrich(RELEASE_ID, List.of(ticket("EX-1", "1")));

        assertThat(saved().get("EX-1").commits()).extracting(LinkedCommit::sha).containsExactly("b");
    }

    @Test
    void commitsAboveTheCapAreCutToTheNewestAndMarkedIncomplete() {
        List<LinkedCommit> commits = List.of(commit("a", T0), commit("b", T0.plusSeconds(30)),
                commit("c", T0.plusSeconds(10)), commit("d", T0.plusSeconds(20)));
        when(port.fetchLinks(ACCESS, "1", "EX-1")).thenReturn(new DevStatusLinks(List.of(), commits, List.of()));

        service(2, Duration.ofSeconds(5), 2).enrich(RELEASE_ID, List.of(ticket("EX-1", "1")));

        CodeLinks result = saved().get("EX-1");
        assertThat(result.status()).isEqualTo(EnrichmentStatus.LINKED);
        assertThat(result.incomplete()).isTrue();
        assertThat(result.reason()).isEqualTo(EnrichmentReason.COMMIT_LIMIT);
        assertThat(result.commits()).extracting(LinkedCommit::sha).containsExactlyInAnyOrder("b", "d");
    }
}
