package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RiceOverrides;
import com.evision.releasemgmt.domain.roadmap.RoadmapAddResult;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRefreshResult;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.RoadmapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoadmapImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-20T09:00:00Z");

    private final RoadmapRepository items = mock(RoadmapRepository.class);
    private final TicketSearchService tickets = mock(TicketSearchService.class);
    private final ReleaseService releases = mock(ReleaseService.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final JiraBriefingPort briefing = mock(JiraBriefingPort.class);
    private final JiraAccessContextService access = mock(JiraAccessContextService.class);
    private final FieldMetadataService fields = mock(FieldMetadataService.class);
    private final UUID liveProduct = UUID.randomUUID();
    private final RoadmapImportService service = new RoadmapImportService(items, tickets, releases, products,
            briefing, access, fields, Clock.fixed(NOW, ZoneOffset.UTC));

    private static final JiraAccessContext CONTEXT = new JiraAccessContext("https://x", "acct", "rm@acme.com", "t");
    private static final BriefingFieldIds ALL_FIELDS = new BriefingFieldIds("sp", "fl", "spr", "ac");

    @BeforeEach
    void everyProjectIsOwnedByALiveProduct() {
        when(products.productIdOwningJiraKey(any())).thenReturn(Optional.of(liveProduct));
        when(products.findById(liveProduct)).thenReturn(Optional.of(new Product(liveProduct, "Core", null)));
        when(access.currentContext()).thenReturn(CONTEXT);
        when(fields.briefingFieldIds(CONTEXT)).thenReturn(new BriefingFieldIds(null, null, null, null));
    }

    private static BriefingIssue signals(String key, String priority, boolean ac, BigDecimal points, String parent) {
        return new BriefingIssue(key, "s", "Story", "To Do", StatusCategory.TO_DO, priority, false, null, null, null,
                null, null, points, parent, 0, ac, null, List.of());
    }

    private void briefingReturns(String jql, BriefingIssue... issues) {
        when(fields.briefingFieldIds(CONTEXT)).thenReturn(ALL_FIELDS);
        when(briefing.search(eq(CONTEXT), eq(jql), eq(ALL_FIELDS), anyInt())).thenReturn(List.of(issues));
    }

    @Test
    void addFromJiraStoresPriorityAndReadinessFromTheBriefingSearch() {
        searchReturns(jql("CNV-1"), ticket("CNV-1", "101"));
        briefingReturns(jql("CNV-1"), signals("CNV-1", "High", true, BigDecimal.ONE, "CNV-100"));

        service.addFromJira(List.of("CNV-1"), "amr");

        ArgumentCaptor<RoadmapItem> captor = ArgumentCaptor.forClass(RoadmapItem.class);
        verify(items).add(captor.capture());
        assertThat(captor.getValue().jiraPriority()).isEqualTo("High");
        assertThat(captor.getValue().jiraReadiness()).isEqualTo(3);
        verify(briefing).search(CONTEXT, jql("CNV-1"), ALL_FIELDS, 1);
    }

    @Test
    void addFromReleaseReadsPriorityAndReadinessInBatchesOfOneHundred() {
        UUID releaseId = UUID.randomUUID();
        List<ReleaseItem> releaseItems = IntStream.rangeClosed(1, 100)
                .mapToObj(n -> new ReleaseItem(ticket("CNV-" + n, "" + n), NOW, CodeLinks.notAttempted())).toList();
        Release release = new Release(releaseId, "R", "1.0", null, "CNV", NOW, "amr", null, null, NOW, null,
                releaseItems, List.of(), null);
        when(releases.get(releaseId)).thenReturn(release);
        when(fields.briefingFieldIds(CONTEXT)).thenReturn(ALL_FIELDS);
        when(briefing.search(eq(CONTEXT), any(), eq(ALL_FIELDS), anyInt()))
                .thenReturn(List.of(signals("CNV-1", "Low", false, null, "CNV-100")));

        service.addFromRelease(releaseId, releaseItems.stream().map(ReleaseItem::ticketKey).toList(), "amr");

        verify(briefing, times(1)).search(eq(CONTEXT), any(), eq(ALL_FIELDS), eq(100));
        ArgumentCaptor<RoadmapItem> captor = ArgumentCaptor.forClass(RoadmapItem.class);
        verify(items, times(100)).add(captor.capture());
        RoadmapItem first = captor.getAllValues().get(0);
        assertThat(first.jiraPriority()).isEqualTo("Low");
        assertThat(first.jiraReadiness()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).jiraPriority()).isNull();
    }

    @Test
    void refreshTakesPriorityFromTheCurrentKeyAndKeepsStoredValuesForMissingIssues() {
        RoadmapItem moved = jiraItem("CNV-1", "101");
        RoadmapItem stored = RoadmapItem.fromJira(
                new JiraSnapshot("CNV-2", "102", "old", "To Do", UPDATED, UPDATED, "Medium", 2), liveProduct, "a",
                UPDATED);
        when(items.findAll()).thenReturn(List.of(moved, stored));
        searchReturns(jql("CNV-1", "CNV-2"), ticket("ABC-9", "101"), ticket("CNV-2", "102"));
        when(items.findIdsByJiraKey(List.of("ABC-9"))).thenReturn(Map.of());
        briefingReturns(jql("ABC-9", "CNV-2"), signals("ABC-9", "Highest", true, BigDecimal.ONE, null));

        service.refreshAll();

        verify(items).applyJiraSnapshot(moved.id(),
                new JiraSnapshot("ABC-9", "101", "Summary ABC-9", "In Progress", UPDATED, NOW, "Highest", 2));
        verify(items).applyJiraSnapshot(stored.id(),
                new JiraSnapshot("CNV-2", "102", "Summary CNV-2", "In Progress", UPDATED, NOW, "Medium", 2));
    }

    @Test
    void aBriefingSearchFailureDuringRefreshWritesNothing() {
        RoadmapItem a = jiraItem("CNV-1", "101");
        when(items.findAll()).thenReturn(List.of(a));
        searchReturns(jql("CNV-1"), ticket("CNV-1", "101"));
        when(briefing.search(any(), any(), any(), anyInt()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR));

        assertThatThrownBy(service::refreshAll).isInstanceOf(JiraTicketReadException.class);

        verify(items, never()).applyJiraSnapshot(any(), any());
        verify(items, never()).markJiraUnavailable(any(), any());
    }

    @Test
    void addFromJiraAssignsTheProductOwningTheKeysProject() {
        searchReturns(jql("CNV-1", "CNV-2"), ticket("CNV-1", "101"), ticket("CNV-2", "102"));

        service.addFromJira(List.of("CNV-1", "CNV-2"), "amr");

        ArgumentCaptor<RoadmapItem> captor = ArgumentCaptor.forClass(RoadmapItem.class);
        verify(items, times(2)).add(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoadmapItem::productId).containsOnly(liveProduct);
        verify(products, times(1)).productIdOwningJiraKey("CNV");
    }

    @Test
    void addFromJiraRejectsAKeyWhoseProjectNoProductOwnsAndAddsTheRest() {
        when(products.productIdOwningJiraKey("XYZ")).thenReturn(Optional.empty());
        searchReturns(jql("CNV-1", "XYZ-2"), ticket("CNV-1", "101"), ticket("XYZ-2", "202"));

        RoadmapAddResult result = service.addFromJira(List.of("CNV-1", "XYZ-2"), "amr");

        assertThat(result.added()).extracting(RoadmapAddResult.Added::jiraKey).containsExactly("CNV-1");
        assertThat(result.rejected()).containsExactly(
                new RoadmapAddResult.Rejected("XYZ-2", RoadmapRejection.ROADMAP_NO_PRODUCT_FOR_PROJECT));
        verify(items, times(1)).add(any());
    }

    @Test
    void addFromJiraRejectsAKeyWhoseOwningProductIsArchived() {
        UUID archived = UUID.randomUUID();
        when(products.productIdOwningJiraKey("OLD")).thenReturn(Optional.of(archived));
        when(products.findById(archived)).thenReturn(Optional.of(new Product(archived, "Old", NOW)));
        searchReturns(jql("OLD-1"), ticket("OLD-1", "301"));

        RoadmapAddResult result = service.addFromJira(List.of("OLD-1"), "amr");

        assertThat(result.rejected()).containsExactly(
                new RoadmapAddResult.Rejected("OLD-1", RoadmapRejection.ROADMAP_PRODUCT_ARCHIVED));
        verify(items, never()).add(any());
    }

    @Test
    void addFromJiraDerivesTheProductFromTheCurrentProjectOfAMovedIssue() {
        searchReturns(jql("CNV-1"));
        when(tickets.findByKey("CNV-1")).thenReturn(Optional.of(ticket("ABC-9", "109")));

        service.addFromJira(List.of("CNV-1"), "amr");

        verify(products).productIdOwningJiraKey("ABC");
        verify(products, never()).productIdOwningJiraKey("CNV");
    }

    @Test
    void addFromReleaseRejectsAKeyWhoseProjectNoProductOwns() {
        UUID releaseId = UUID.randomUUID();
        Release release = new Release(releaseId, "R", "1.0", null, "CNV", NOW, "amr", null, null, NOW, null,
                List.of(new ReleaseItem(ticket("XYZ-7", "707"), NOW, CodeLinks.notAttempted())), List.of(), null);
        when(releases.get(releaseId)).thenReturn(release);
        when(products.productIdOwningJiraKey("XYZ")).thenReturn(Optional.empty());

        RoadmapAddResult result = service.addFromRelease(releaseId, List.of("XYZ-7"), "amr");

        assertThat(result.added()).isEmpty();
        assertThat(result.rejected()).containsExactly(
                new RoadmapAddResult.Rejected("XYZ-7", RoadmapRejection.ROADMAP_NO_PRODUCT_FOR_PROJECT));
    }

    private static TicketDetail ticket(String key, String issueId) {
        return new TicketDetail(key, "Summary " + key, "Story", "In Progress", null, "Ann", "Bob", List.of(), null,
                "secret description", List.of("secret comment"), 1, issueId, UPDATED, null, null, false);
    }

    private static String jql(String... keys) {
        return "key in (" + String.join(", ", keys) + ")";
    }

    private void searchReturns(String jql, TicketDetail... found) {
        when(tickets.search(TicketSearchCriteria.byJql(jql))).thenReturn(List.of(found));
    }

    private static RoadmapItem jiraItem(String key, String issueId) {
        return RoadmapItem.fromJira(new JiraSnapshot(key, issueId, "old", "To Do", UPDATED, UPDATED, null, null), UUID.randomUUID(), "amr", UPDATED);
    }

    private List<JiraSnapshot> addedSnapshots(int expected) {
        ArgumentCaptor<RoadmapItem> captor = ArgumentCaptor.forClass(RoadmapItem.class);
        verify(items, times(expected)).add(captor.capture());
        return captor.getAllValues().stream()
                .map(i -> new JiraSnapshot(i.jiraKey(), i.jiraIssueId(), i.title(), i.jiraStatus(),
                        i.jiraUpdatedAt(), i.jiraSyncedAt(), i.jiraPriority(), i.jiraReadiness()))
                .toList();
    }

    @Test
    void addFromJiraRejectsMoreThanTheCapBeforeAnyJiraCall() {
        List<String> keys = IntStream.rangeClosed(1, 101).mapToObj(i -> "CNV-" + i).toList();

        assertThatThrownBy(() -> service.addFromJira(keys, "amr"))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_TOO_MANY_ITEMS));
        verifyNoInteractions(tickets);
    }

    @Test
    void addFromJiraSkipsKeysAlreadyOnTheRoadmapAndAddsTheRest() {
        UUID existing = UUID.randomUUID();
        when(items.findIdsByJiraKey(any())).thenReturn(Map.of("CNV-1", existing));
        searchReturns(jql("CNV-2"), ticket("CNV-2", "102"));

        RoadmapAddResult result = service.addFromJira(List.of("CNV-1", "CNV-2", "CNV-1"), "amr");

        assertThat(result.alreadyPresent()).containsExactly(new RoadmapAddResult.AlreadyPresent("CNV-1", existing));
        assertThat(result.added()).extracting(RoadmapAddResult.Added::jiraKey).containsExactly("CNV-2");
        assertThat(result.notFound()).isEmpty();
        verify(tickets, times(1)).search(any());
    }

    @Test
    void addFromJiraStoresOnlyTheJiraSnapshotFields() {
        searchReturns(jql("CNV-2"), ticket("CNV-2", "102"));

        service.addFromJira(List.of("CNV-2"), "amr");

        assertThat(addedSnapshots(1)).containsExactly(
                new JiraSnapshot("CNV-2", "102", "Summary CNV-2", "In Progress", UPDATED, NOW, null, null));
    }

    @Test
    void addFromJiraRetriesEachKeyWhenTheBatchComesBackEmpty() {
        searchReturns(jql("CNV-1", "OLD-9"));
        when(tickets.findByKey("CNV-1")).thenReturn(Optional.of(ticket("CNV-1", "101")));
        when(tickets.findByKey("OLD-9")).thenReturn(Optional.empty());

        RoadmapAddResult result = service.addFromJira(List.of("CNV-1", "OLD-9"), "amr");

        assertThat(result.added()).extracting(RoadmapAddResult.Added::jiraKey).containsExactly("CNV-1");
        assertThat(result.notFound()).containsExactly("OLD-9");
    }

    @Test
    void addFromJiraReportsAMovedIssueAlreadyOnTheRoadmapAsAlreadyPresent() {
        UUID existing = UUID.randomUUID();
        searchReturns(jql("OLD-1"));
        when(tickets.findByKey("OLD-1")).thenReturn(Optional.of(ticket("NEW-5", "105")));
        when(items.findIdsByJiraKey(List.of("NEW-5"))).thenReturn(Map.of("NEW-5", existing));

        RoadmapAddResult result = service.addFromJira(List.of("OLD-1"), "amr");

        assertThat(result.alreadyPresent()).containsExactly(new RoadmapAddResult.AlreadyPresent("NEW-5", existing));
        assertThat(result.added()).isEmpty();
        verify(items, never()).add(any());
    }

    @Test
    void addFromJiraPropagatesJiraFailureWithoutWriting() {
        when(tickets.search(any())).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE));

        assertThatThrownBy(() -> service.addFromJira(List.of("CNV-1"), "amr"))
                .isInstanceOf(JiraTicketReadException.class);
        verify(items, never()).add(any());
    }

    @Test
    void addFromJiraReportsARaceDuplicateAsAlreadyPresent() {
        UUID winner = UUID.randomUUID();
        searchReturns(jql("CNV-2"), ticket("CNV-2", "102"));
        when(items.add(any())).thenThrow(new RoadmapRejectedException(RoadmapRejection.ROADMAP_JIRA_KEY_DUPLICATE));
        when(items.findIdsByJiraKey(List.of("CNV-2"))).thenReturn(Map.of()).thenReturn(Map.of("CNV-2", winner));

        RoadmapAddResult result = service.addFromJira(List.of("CNV-2"), "amr");

        assertThat(result.alreadyPresent()).containsExactly(new RoadmapAddResult.AlreadyPresent("CNV-2", winner));
        assertThat(result.added()).isEmpty();
    }

    @Test
    void addFromJiraRejectsMalformedKeysBeforeBuildingJql() {
        RoadmapAddResult result = service.addFromJira(List.of("x) OR project = SECRET"), "amr");

        assertThat(result.notFound()).containsExactly("x) OR project = SECRET");
        verifyNoInteractions(tickets);
    }

    @Test
    void addFromReleaseUsesTheStoredSnapshotWithoutRefetchingTickets() {
        UUID releaseId = UUID.randomUUID();
        Instant addedAt = Instant.parse("2026-10-22T08:00:00Z");
        Instant refreshedAt = Instant.parse("2026-10-23T08:00:00Z");
        Release release = new Release(releaseId, "R", "1.0", null, "CNV", NOW, "amr", null, null, refreshedAt, null,
                List.of(new ReleaseItem(ticket("CNV-7", "107"), addedAt, CodeLinks.notAttempted())), List.of(), null);
        when(releases.get(releaseId)).thenReturn(release);

        RoadmapAddResult result = service.addFromRelease(releaseId, List.of("CNV-7", "CNV-8"), "amr");

        assertThat(result.added()).extracting(RoadmapAddResult.Added::jiraKey).containsExactly("CNV-7");
        assertThat(result.notFound()).containsExactly("CNV-8");
        assertThat(addedSnapshots(1)).containsExactly(
                new JiraSnapshot("CNV-7", "107", "Summary CNV-7", "In Progress", UPDATED, refreshedAt, null, null));
        verifyNoInteractions(tickets);
    }

    @Test
    void refreshAllQueriesOnlyActiveJiraItemsAndAppliesEachSnapshot() {
        RoadmapItem a = jiraItem("CNV-1", "101");
        RoadmapItem b = jiraItem("CNV-2", "102");
        RoadmapItem c = jiraItem("CNV-3", "103");
        RoadmapItem manual = RoadmapItem.manual("idea", null, "amr", NOW);
        RoadmapItem archived = jiraItem("CNV-9", "109");
        archived = new RoadmapItem(archived.id(), archived.source(), archived.jiraKey(), archived.jiraIssueId(),
                archived.title(), archived.jiraStatus(), archived.jiraUpdatedAt(), archived.jiraSyncedAt(), null,
                null, archived.status(), null, null, null, archived.inputs(), RiceOverrides.NONE, null, null, NOW, "amr", NOW);
        when(items.findAll()).thenReturn(List.of(a, b, c, manual, archived));
        searchReturns(jql("CNV-1", "CNV-2", "CNV-3"), ticket("CNV-1", "101"), ticket("CNV-2", "102"),
                ticket("CNV-3", "103"));

        RoadmapRefreshResult result = service.refreshAll();

        assertThat(result.refreshed()).isEqualTo(3);
        assertThat(result.unavailable()).isEmpty();
        verify(items).applyJiraSnapshot(eq(a.id()),
                eq(new JiraSnapshot("CNV-1", "101", "Summary CNV-1", "In Progress", UPDATED, NOW, null, null)));
        verify(items, times(3)).applyJiraSnapshot(any(), any());
        verify(tickets, times(1)).search(any());
    }

    @Test
    void refreshFlagsOnlyAKeyConfirmedGoneByASuccessfulCall() {
        RoadmapItem a = jiraItem("CNV-1", "101");
        RoadmapItem b = jiraItem("CNV-2", "102");
        RoadmapItem c = jiraItem("CNV-3", "103");
        when(items.findAll()).thenReturn(List.of(a, b, c));
        searchReturns(jql("CNV-1", "CNV-2", "CNV-3"), ticket("CNV-1", "101"), ticket("CNV-3", "103"));
        when(tickets.findByKey("CNV-2")).thenReturn(Optional.empty());

        RoadmapRefreshResult result = service.refreshAll();

        assertThat(result.refreshed()).isEqualTo(2);
        assertThat(result.unavailable()).containsExactly(new RoadmapRefreshResult.Unavailable(b.id(), "CNV-2"));
        verify(items).markJiraUnavailable(b.id(), NOW);
        verify(items, times(2)).applyJiraSnapshot(any(), any());
    }

    @Test
    void refreshRetriesEveryKeyWhenTheWholeBatchComesBackEmpty() {
        RoadmapItem a = jiraItem("CNV-1", "101");
        RoadmapItem b = jiraItem("CNV-2", "102");
        RoadmapItem c = jiraItem("CNV-3", "103");
        when(items.findAll()).thenReturn(List.of(a, b, c));
        searchReturns(jql("CNV-1", "CNV-2", "CNV-3"));
        when(tickets.findByKey("CNV-1")).thenReturn(Optional.of(ticket("CNV-1", "101")));
        when(tickets.findByKey("CNV-2")).thenReturn(Optional.empty());
        when(tickets.findByKey("CNV-3")).thenReturn(Optional.of(ticket("CNV-3", "103")));

        RoadmapRefreshResult result = service.refreshAll();

        assertThat(result.refreshed()).isEqualTo(2);
        assertThat(result.unavailable()).extracting(RoadmapRefreshResult.Unavailable::jiraKey).containsExactly("CNV-2");
        verify(items, never()).markJiraUnavailable(eq(a.id()), any());
        verify(items, never()).markJiraUnavailable(eq(c.id()), any());
    }

    @Test
    void refreshMatchesByIssueIdAndFollowsAMovedKey() {
        RoadmapItem a = jiraItem("OLD-1", "101");
        when(items.findAll()).thenReturn(List.of(a));
        searchReturns(jql("OLD-1"), ticket("NEW-1", "101"));
        when(items.findIdsByJiraKey(List.of("NEW-1"))).thenReturn(Map.of());

        service.refreshAll();

        verify(items).applyJiraSnapshot(a.id(),
                new JiraSnapshot("NEW-1", "101", "Summary NEW-1", "In Progress", UPDATED, NOW, null, null));
    }

    @Test
    void refreshKeepsTheOldKeyWhenTheNewKeyBelongsToAnotherItem() {
        RoadmapItem a = jiraItem("OLD-1", "101");
        when(items.findAll()).thenReturn(List.of(a));
        searchReturns(jql("OLD-1"), ticket("NEW-1", "101"));
        when(items.findIdsByJiraKey(List.of("NEW-1"))).thenReturn(Map.of("NEW-1", UUID.randomUUID()));

        service.refreshAll();

        verify(items).applyJiraSnapshot(a.id(),
                new JiraSnapshot("OLD-1", "101", "Summary NEW-1", "In Progress", UPDATED, NOW, null, null));
    }

    @Test
    void aJiraFailureLeavesNothingChanged() {
        RoadmapItem a = jiraItem("CNV-1", "101");
        RoadmapItem b = jiraItem("CNV-2", "102");
        when(items.findAll()).thenReturn(List.of(a, b));
        searchReturns(jql("CNV-1", "CNV-2"), ticket("CNV-1", "101"));
        when(tickets.findByKey("CNV-2")).thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED));

        assertThatThrownBy(service::refreshAll).isInstanceOf(JiraTicketReadException.class);

        verify(items, never()).applyJiraSnapshot(any(), any());
        verify(items, never()).markJiraUnavailable(any(), any());
    }

    @Test
    void refreshAllRejectsMoreThanTheCapBeforeAnyJiraCall() {
        List<RoadmapItem> many = new ArrayList<>();
        IntStream.rangeClosed(1, 501).forEach(i -> many.add(jiraItem("CNV-" + i, String.valueOf(i))));
        when(items.findAll()).thenReturn(many);

        assertThatThrownBy(service::refreshAll)
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_REFRESH_TOO_LARGE));
        verifyNoInteractions(tickets);
    }

    @Test
    void refreshOneRejectsManualAndUnknownItems() {
        RoadmapItem manual = RoadmapItem.manual("idea", null, "amr", NOW);
        when(items.findById(manual.id())).thenReturn(Optional.of(manual));
        UUID unknown = UUID.randomUUID();
        when(items.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(manual.id()))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_ITEM_NOT_JIRA));
        assertThatThrownBy(() -> service.refresh(unknown)).isInstanceOf(RoadmapItemNotFoundException.class);
    }

    @Test
    void refreshOneRefreshesJustThatItem() {
        RoadmapItem a = jiraItem("CNV-1", "101");
        when(items.findById(a.id())).thenReturn(Optional.of(a));
        searchReturns(jql("CNV-1"), ticket("CNV-1", "101"));

        RoadmapRefreshResult result = service.refresh(a.id());

        assertThat(result.refreshed()).isEqualTo(1);
        verify(items, never()).findAll();
        verify(items).applyJiraSnapshot(eq(a.id()), any());
    }
}
