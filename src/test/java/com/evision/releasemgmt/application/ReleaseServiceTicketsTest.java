package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.release.AddTicketsResult;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceTicketsTest {

    @Mock
    private ReleaseRepository repository;

    @Mock
    private TicketSearchService ticketSearchService;

    private ReleaseService service;

    @BeforeEach
    void setUp() {
        service = new ReleaseService(repository, ticketSearchService);
    }

    private static TicketDetail ticket(String key) {
        return new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed", "Dev Person",
                "Reporter Person", List.of("2026.1"), null, "Full description " + key,
                List.of("Comment 1", "Comment 2"), 2);
    }

    private static Release release(UUID id, ReleaseStatus status) {
        return new Release(id, "Sprint 42", "2026.08", status, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null,
                List.of(), List.of());
    }

    @Test
    void addingNewTicketsMaterialisesTheFullTicketDetail() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        TicketDetail cnv1 = ticket("CNV-1");
        TicketDetail cnv2 = ticket("CNV-2");
        when(ticketSearchService.search(any())).thenReturn(List.of(cnv1, cnv2));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "CNV-2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReleaseItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).addItems(eq(id), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(2);
        assertThat(itemsCaptor.getValue().get(0).ticket()).isEqualTo(cnv1);
        assertThat(itemsCaptor.getValue().get(0).ticket().description()).isEqualTo("Full description CNV-1");
        assertThat(itemsCaptor.getValue().get(1).ticket()).isEqualTo(cnv2);
        assertThat(result.added()).isEqualTo(2);
        assertThat(result.alreadyPresent()).isEqualTo(0);
    }

    @Test
    void addingATicketAlreadyOnTheReleaseIsANoOpNotAnError() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1"));

        verify(ticketSearchService, never()).search(any());
        verify(repository, never()).addItems(any(), any());
        assertThat(result.added()).isEqualTo(0);
        assertThat(result.alreadyPresent()).isEqualTo(1);
    }

    @Test
    void aPartiallyOverlappingAddReportsBothCountsHonestly() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-2"), ticket("CNV-3")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "CNV-2", "CNV-3"));

        assertThat(result.added()).isEqualTo(2);
        assertThat(result.alreadyPresent()).isEqualTo(1);
    }

    @Test
    void duplicateKeysInOneRequestAreCollapsedBeforeCounting() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1"), ticket("CNV-2")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "CNV-1", "CNV-2"));

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        verify(ticketSearchService).search(criteria.capture());
        assertThat(criteria.getValue().toJql()).isEqualTo("key in (CNV-1, CNV-2) ORDER BY key ASC");
        assertThat(result.added()).isEqualTo(2);
    }

    @Test
    void aKeyJiraDoesNotReturnIsCountedAsNotFoundRatherThanVanishing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "CNV-2"));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReleaseItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).addItems(eq(id), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
    }

    @Test
    void aKeyRejectedByTheIssueKeyPatternIsCountedAsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "not a key"));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(1);
    }

    // An issue moved between projects is requested as OLD-1 and comes back as NEW-5. Filtering the
    // request alone let NEW-5 through to an insert that violates release_ticket_item_key_unique.
    @Test
    void aRequestedKeyThatResolvesToAnIssueAlreadyOnTheReleaseIsNotInsertedAgain() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("NEW-5"));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("NEW-5")));

        AddTicketsResult result = service.addTickets(id, List.of("OLD-1"));

        verify(repository, never()).addItems(any(), any());
        assertThat(result.added()).isEqualTo(0);
        assertThat(result.alreadyPresent()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(0);
    }

    @Test
    void everyRequestedKeyLandsInExactlyOneCount() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-2")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "CNV-2", "CNV-3"));

        assertThat(result.added() + result.alreadyPresent() + result.notFound()).isEqualTo(3);
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void createWithTicketsCountsTheKeysJiraDidNotReturnAsNotFound() {
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        when(repository.create(any(), any())).thenReturn(release(UUID.randomUUID(), ReleaseStatus.OPEN));

        AddTicketsResult result = service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1", "CNV-2"), "557058:abc-123");

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void ticketKeysAreComposedIntoJqlAsAKeyInClause() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1"), ticket("CNV-2")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        service.addTickets(id, List.of("CNV-1", "CNV-2"));

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        verify(ticketSearchService).search(criteria.capture());
        assertThat(criteria.getValue().toJql()).isEqualTo("key in (CNV-1, CNV-2) ORDER BY key ASC");
    }

    @Test
    void aKeyThatIsNotAValidJiraIssueKeyNeverReachesJql() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        when(repository.addItems(eq(id), any())).thenReturn(release(id, ReleaseStatus.OPEN));

        service.addTickets(id, List.of("CNV-1", "CNV-1) OR project = SECRET--"));

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        verify(ticketSearchService).search(criteria.capture());
        String jql = criteria.getValue().toJql();
        assertThat(jql).doesNotContain(" OR ").doesNotContain("SECRET");
        assertThat(jql).isEqualTo("key in (CNV-1) ORDER BY key ASC");
    }

    @Test
    void addingToAFrozenReleaseThrowsReleaseFrozenException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.addTickets(id, List.of("CNV-1")))
                .isInstanceOf(ReleaseFrozenException.class);

        verify(ticketSearchService, never()).search(any());
        verify(repository, never()).addItems(any(), any());
    }

    @Test
    void removingFromAFrozenReleaseThrowsReleaseFrozenException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.removeTicket(id, "CNV-1"))
                .isInstanceOf(ReleaseFrozenException.class);

        verify(repository, never()).removeItem(any(), any());
    }

    @Test
    void refreshingAFrozenReleaseThrowsReleaseFrozenException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.refresh(id))
                .isInstanceOf(ReleaseFrozenException.class);

        verify(ticketSearchService, never()).search(any());
        verify(repository, never()).replaceItemSnapshots(any(), any(), any());
    }

    @Test
    void refreshReplacesSnapshotsAndStampsLastRefreshedAt() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        TicketDetail changed = new TicketDetail("CNV-1", "Summary CNV-1", "Bug", "In Progress", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.1"), null, "Full description CNV-1",
                List.of("Comment 1", "Comment 2"), 2);
        when(ticketSearchService.search(any())).thenReturn(List.of(changed));
        when(repository.replaceItemSnapshots(any(), any(), any()))
                .thenReturn(release(id, ReleaseStatus.OPEN));

        service.refresh(id);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReleaseItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Instant> refreshedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).replaceItemSnapshots(eq(id), itemsCaptor.capture(), refreshedAtCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).ticket().status()).isEqualTo("In Progress");
        assertThat(refreshedAtCaptor.getValue()).isNotNull();
        assertThat(itemsCaptor.getValue().get(0).addedAt()).isEqualTo(refreshedAtCaptor.getValue());
    }

    private static List<TicketDetail> tickets(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive).mapToObj(i -> ticket("CNV-" + i)).toList();
    }

    @Test
    void refreshFetchesInBatchesOfOneHundred() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        List<String> keys = IntStream.rangeClosed(1, 250).mapToObj(i -> "CNV-" + i).toList();
        when(repository.findTicketKeys(id)).thenReturn(keys);
        when(ticketSearchService.search(any()))
                .thenReturn(tickets(1, 100), tickets(101, 200), tickets(201, 250));
        when(repository.replaceItemSnapshots(any(), any(), any()))
                .thenReturn(release(id, ReleaseStatus.OPEN));

        service.refresh(id);

        verify(ticketSearchService, times(3)).search(any());
    }

    @Test
    void refreshDoesNotStampLastRefreshedAtWhenJiraReturnedOnlySomeOfTheTickets() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1", "CNV-2"));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));

        assertThatThrownBy(() -> service.refresh(id))
                .isInstanceOf(ReleaseRefreshIncompleteException.class);

        verify(repository, never()).replaceItemSnapshots(any(), any(), any());
    }

    @Test
    void refreshDoesNotStampLastRefreshedAtWhenJiraReturnedNothingAtAll() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        when(ticketSearchService.search(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.refresh(id))
                .isInstanceOf(ReleaseRefreshIncompleteException.class);

        verify(repository, never()).replaceItemSnapshots(any(), any(), any());
    }

    @Test
    void createWithTicketsWritesTheReleaseAndItsItemsInOneRepositoryCall() {
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1"), ticket("CNV-2")));
        when(repository.create(any(), any())).thenReturn(release(UUID.randomUUID(), ReleaseStatus.OPEN));

        AddTicketsResult result = service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1", "CNV-2"), "557058:abc-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReleaseItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).create(any(), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(2);
        verify(repository, never()).addItems(any(), any());
        assertThat(result.added()).isEqualTo(2);
    }

    @Test
    void createWithTicketsDoesNotPersistAnythingWhenTheJiraFetchFails() {
        when(ticketSearchService.search(any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        assertThatThrownBy(() -> service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1"), "557058:abc-123"))
                .isInstanceOf(JiraTicketReadException.class);

        verify(repository, never()).create(any(), any());
    }

    @Test
    void removingATicketThatIsNotOnTheReleaseIsANoOp() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.removeItem(id, "CNV-99")).thenReturn(release(id, ReleaseStatus.OPEN));

        assertThatCode(() -> service.removeTicket(id, "CNV-99")).doesNotThrowAnyException();
    }
}
