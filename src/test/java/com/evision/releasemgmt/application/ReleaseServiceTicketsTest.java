package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.release.AddTicketsResult;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.UnfreezeRejectedException;
import com.evision.releasemgmt.domain.release.UnfreezeRejection;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private ReleaseProductRepository releaseProducts;

    @Mock
    private ProductRepository products;

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private ReleaseService service;

    @BeforeEach
    void setUp() {
        service = new ReleaseService(repository, ticketSearchService, enrichmentService,
                releaseProducts, products);
        lenient().when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(new Product(PRODUCT_ID, "Core", null)));
    }

    private static TicketDetail ticket(String key) {
        return new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed", "Dev Person",
                "Reporter Person", List.of("2026.1"), null, "Full description " + key,
                List.of("Comment 1", "Comment 2"), 2, null, null, null, null, false);
    }

    private static Release release(UUID id, ReleaseStatus status) {
        return new Release(id, "Sprint 42", "2026.08", status, "CNV",
                Instant.parse("2026-08-04T09:00:00Z"), "557058:abc-123", null, null, null, null,
                List.of(), List.of(), null);
    }

    @Test
    void addingNewTicketsMaterialisesTheFullTicketDetail() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        TicketDetail cnv1 = ticket("CNV-1");
        TicketDetail cnv2 = ticket("CNV-2");
        when(ticketSearchService.search(any())).thenReturn(List.of(cnv1, cnv2));

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

        AddTicketsResult result = service.addTickets(id, List.of("CNV-1", "CNV-2", "CNV-3"));

        assertThat(result.added() + result.alreadyPresent() + result.notFound()).isEqualTo(3);
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void createWithTicketsCountsTheKeysJiraDidNotReturnAsNotFound() {
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        UUID createdId = UUID.randomUUID();
        when(repository.create(any(), any())).thenReturn(release(createdId, ReleaseStatus.OPEN));
        when(repository.findById(createdId)).thenReturn(Optional.of(release(createdId, ReleaseStatus.OPEN)));

        AddTicketsResult result = service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1", "CNV-2"), List.of(PRODUCT_ID), "557058:abc-123");

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void ticketKeysAreComposedIntoJqlAsAKeyInClause() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of());
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1"), ticket("CNV-2")));

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
                List.of("Comment 1", "Comment 2"), 2, null, null, null, null, false);
        when(ticketSearchService.search(any())).thenReturn(List.of(changed));

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

    @Test
    void unfreezeReturnsTheRepositoryResult() {
        UUID id = UUID.randomUUID();
        Release open = release(id, ReleaseStatus.OPEN);
        when(repository.unfreeze(id)).thenReturn(open);

        assertThat(service.unfreeze(id, "557058:abc-123")).isSameAs(open);
    }

    @Test
    void unfreezePropagatesARejectionUnchanged() {
        UUID id = UUID.randomUUID();
        UnfreezeRejectedException rejected = new UnfreezeRejectedException(UnfreezeRejection.UNFREEZE_HAS_PROMOTIONS);
        when(repository.unfreeze(id)).thenThrow(rejected);

        assertThatThrownBy(() -> service.unfreeze(id, "557058:abc-123")).isSameAs(rejected);
    }

    @Test
    void anOpenReleaseHasNoUnfreezeBlockerAndTheRepositoryIsNotAsked() {
        Release open = release(UUID.randomUUID(), ReleaseStatus.OPEN);

        assertThat(service.unfreezeBlocker(open)).isEmpty();

        verify(repository, never()).unfreezeBlocker(any());
    }

    @Test
    void aFrozenReleaseReportsTheRepositoryBlocker() {
        UUID id = UUID.randomUUID();
        when(repository.unfreezeBlocker(id)).thenReturn(Optional.of(UnfreezeRejection.UNFREEZE_HAS_INSTALLS));

        assertThat(service.unfreezeBlocker(release(id, ReleaseStatus.FROZEN)))
                .contains(UnfreezeRejection.UNFREEZE_HAS_INSTALLS);
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
        UUID createdId = UUID.randomUUID();
        when(repository.create(any(), any())).thenReturn(release(createdId, ReleaseStatus.OPEN));
        when(repository.findById(createdId)).thenReturn(Optional.of(release(createdId, ReleaseStatus.OPEN)));

        AddTicketsResult result = service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1", "CNV-2"), List.of(PRODUCT_ID), "557058:abc-123");

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
                List.of("CNV-1"), List.of(PRODUCT_ID), "557058:abc-123"))
                .isInstanceOf(JiraTicketReadException.class);

        verify(repository, never()).create(any(), any());
    }

    @Test
    void refreshStillSucceedsWhenEnrichmentThrows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        doThrow(new IllegalStateException("boom")).when(enrichmentService).enrich(any(), any());

        Release result = service.refresh(id);

        assertThat(result.id()).isEqualTo(id);
        verify(repository).replaceItemSnapshots(eq(id), any(), any());
    }

    @Test
    void refreshEnrichesOnlyAfterTheSnapshotsAreWritten() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));

        service.refresh(id);

        InOrder order = inOrder(repository, enrichmentService);
        order.verify(repository).replaceItemSnapshots(eq(id), any(), any());
        order.verify(enrichmentService).enrich(eq(id), any());
    }

    @Test
    void addEnrichesOnlyTheNewlyInsertedTickets() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));
        TicketDetail added = ticket("CNV-2");
        when(ticketSearchService.search(any())).thenReturn(List.of(added));

        service.addTickets(id, List.of("CNV-1", "CNV-2"));

        verify(enrichmentService).enrich(id, List.of(added));
    }

    @Test
    void createEnrichesTheFetchedTickets() {
        UUID createdId = UUID.randomUUID();
        TicketDetail fetched = ticket("CNV-1");
        when(ticketSearchService.search(any())).thenReturn(List.of(fetched));
        when(repository.create(any(), any())).thenReturn(release(createdId, ReleaseStatus.OPEN));
        when(repository.findById(createdId)).thenReturn(Optional.of(release(createdId, ReleaseStatus.OPEN)));

        service.createWithTickets("Sprint 42", "2026.08", "CNV", List.of("CNV-1"),
                List.of(PRODUCT_ID), "557058:abc-123");

        verify(enrichmentService).enrich(createdId, List.of(fetched));
    }

    @Test
    void createWithoutProductsIsRejectedBeforeJiraOrInsert() {
        assertThatThrownBy(() -> service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1"), List.of(), "557058:abc-123"))
                .isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.RELEASE_PRODUCT_REQUIRED));

        verify(ticketSearchService, never()).search(any());
        verify(repository, never()).create(any(), any());
    }

    @Test
    void createWithAnArchivedProductIsRejectedBeforeJiraOrInsert() {
        UUID archived = UUID.randomUUID();
        when(products.findById(archived))
                .thenReturn(Optional.of(new Product(archived, "Old", Instant.parse("2026-01-01T00:00:00Z"))));

        assertThatThrownBy(() -> service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1"), List.of(archived), "557058:abc-123"))
                .isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.PRODUCT_ARCHIVED));

        verify(ticketSearchService, never()).search(any());
        verify(repository, never()).create(any(), any());
    }

    @Test
    void createWithAnUnknownProductIsRejectedBeforeJiraOrInsert() {
        UUID unknown = UUID.randomUUID();
        when(products.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1"), List.of(unknown), "557058:abc-123"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(ticketSearchService, never()).search(any());
        verify(repository, never()).create(any(), any());
    }

    @Test
    void createAssignsTheRequestedProducts() {
        UUID second = UUID.randomUUID();
        when(products.findById(second)).thenReturn(Optional.of(new Product(second, "Mobile", null)));
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        UUID createdId = UUID.randomUUID();
        when(repository.create(any(), any())).thenReturn(release(createdId, ReleaseStatus.OPEN));
        when(repository.findById(createdId)).thenReturn(Optional.of(release(createdId, ReleaseStatus.OPEN)));

        service.createWithTickets("Sprint 42", "2026.08", "CNV", List.of("CNV-1"),
                List.of(PRODUCT_ID, second), "557058:abc-123");

        verify(releaseProducts).replace(createdId, Set.of(PRODUCT_ID, second));
    }

    @Test
    void createDeletesTheNewReleaseWhenProductAssignmentFails() {
        when(ticketSearchService.search(any())).thenReturn(List.of(ticket("CNV-1")));
        UUID createdId = UUID.randomUUID();
        when(repository.create(any(), any())).thenReturn(release(createdId, ReleaseStatus.OPEN));
        doThrow(new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED))
                .when(releaseProducts).replace(any(), any());

        assertThatThrownBy(() -> service.createWithTickets("Sprint 42", "2026.08", "CNV",
                List.of("CNV-1"), List.of(PRODUCT_ID), "557058:abc-123"))
                .isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.PRODUCT_ARCHIVED));

        verify(repository).delete(createdId);
    }

    @Test
    void setProductsWorksOnAFrozenRelease() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        service.setProducts(id, List.of(PRODUCT_ID));

        verify(releaseProducts).replace(id, Set.of(PRODUCT_ID));
    }

    @Test
    void refreshCodeLinksOnAFrozenReleaseIsRefusedWithoutEnriching() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.FROZEN)));

        assertThatThrownBy(() -> service.refreshCodeLinks(id, null)).isInstanceOf(ReleaseFrozenException.class);

        verify(enrichmentService, never()).enrich(any(), any());
    }

    @Test
    void refreshCodeLinksForAKeyNotOnTheReleaseEnrichesNothing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1"));

        service.refreshCodeLinks(id, "NOPE-1");

        verify(enrichmentService).enrich(id, List.of());
        verify(ticketSearchService, never()).search(any());
    }

    @Test
    void refreshCodeLinksForOneKeyFetchesAndEnrichesOnlyThatTicket() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.findTicketKeys(id)).thenReturn(List.of("CNV-1", "CNV-2"));
        TicketDetail one = ticket("CNV-2");
        when(ticketSearchService.search(any())).thenReturn(List.of(one));

        service.refreshCodeLinks(id, "CNV-2");

        verify(enrichmentService).enrich(id, List.of(one));
    }

    @Test
    void removingATicketThatIsNotOnTheReleaseIsANoOp() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(release(id, ReleaseStatus.OPEN)));
        when(repository.removeItem(id, "CNV-99")).thenReturn(release(id, ReleaseStatus.OPEN));

        assertThatCode(() -> service.removeTicket(id, "CNV-99")).doesNotThrowAnyException();
    }
}
