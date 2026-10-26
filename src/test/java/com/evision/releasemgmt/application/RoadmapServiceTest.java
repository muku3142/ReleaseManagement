package com.evision.releasemgmt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapFilter;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemDetails;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.RoadmapHistoryRepository;
import com.evision.releasemgmt.ports.RoadmapRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoadmapServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-10-01T10:00:00Z");

    private final RoadmapRepository items = mock(RoadmapRepository.class);
    private final RoadmapHistoryRepository history = mock(RoadmapHistoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final RoadmapService service =
            new RoadmapService(items, history, products, Clock.fixed(NOW, ZoneOffset.UTC));

    private static RoadmapItem item(RiceInputs inputs, RoadmapStatus status, UUID productId, Instant archivedAt) {
        RoadmapItem base = RoadmapItem.manual("t", productId, "a", EARLIER);
        return new RoadmapItem(base.id(), base.source(), null, null, "t", null, null, null, null, productId, status,
                null, null, null, inputs, EARLIER, "a", archivedAt);
    }

    private static RoadmapItem jiraItem(UUID productId) {
        return RoadmapItem.fromJira(new JiraSnapshot("K-1", "1", "summary", "Open", EARLIER, EARLIER), "a", EARLIER);
    }

    private static RiceInputs scored() {
        return new RiceInputs(100, BigDecimal.ONE, 100, BigDecimal.ONE);
    }

    private static Product product(UUID id, boolean archived) {
        return new Product(id, "P", archived ? EARLIER : null);
    }

    @Test
    void rankedHidesDoneAndArchivedByDefaultAndRanksScoredFirst() {
        RoadmapItem unscored = item(RiceInputs.NONE, RoadmapStatus.IDEA, null, null);
        RoadmapItem open = item(scored(), RoadmapStatus.IDEA, null, null);
        RoadmapItem done = item(scored(), RoadmapStatus.DONE, null, null);
        RoadmapItem archived = item(scored(), RoadmapStatus.IDEA, null, NOW);
        when(items.findAll()).thenReturn(List.of(unscored, open, done, archived));

        List<RankedItem> ranked = service.ranked(new RoadmapFilter(Set.of(), false, false));

        assertThat(ranked).extracting(RankedItem::item).containsExactly(open, unscored);
        assertThat(ranked.get(0).rank()).isEqualTo(1);
    }

    @Test
    void rankedIncludesDoneAndArchivedWhenAskedAndFiltersByProduct() {
        UUID p = UUID.randomUUID();
        RoadmapItem done = item(scored(), RoadmapStatus.DONE, p, null);
        RoadmapItem archived = item(scored(), RoadmapStatus.IDEA, p, NOW);
        RoadmapItem other = item(scored(), RoadmapStatus.IDEA, UUID.randomUUID(), null);
        when(items.findAll()).thenReturn(List.of(done, archived, other));

        assertThat(service.ranked(new RoadmapFilter(Set.of(), true, true))).hasSize(3);
        assertThat(service.ranked(new RoadmapFilter(Set.of(p), true, true)))
                .extracting(RankedItem::item).containsExactlyInAnyOrder(done, archived);
    }

    @Test
    void addManualTrimsTitleAndStartsAsIdeaWithoutInputs() {
        when(items.add(any())).thenAnswer(call -> call.getArgument(0));

        RoadmapItem added = service.addManual("  Title ", null, "acc-1");

        assertThat(added.title()).isEqualTo("Title");
        assertThat(added.status()).isEqualTo(RoadmapStatus.IDEA);
        assertThat(added.inputs()).isEqualTo(RiceInputs.NONE);
        assertThat(added.createdBy()).isEqualTo("acc-1");
        assertThat(added.createdAt()).isEqualTo(NOW);
    }

    @Test
    void addManualRejectsArchivedProductAndUnknownProduct() {
        UUID archived = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(products.findById(archived)).thenReturn(Optional.of(product(archived, true)));
        when(products.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addManual("T", archived, "a"))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_PRODUCT_ARCHIVED));
        assertThatThrownBy(() -> service.addManual("T", unknown, "a")).isInstanceOf(ProductNotFoundException.class);
        verify(items, never()).add(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setInputsRejectsArchivedItemFromInsideTheDecideFunction() {
        ArgumentCaptor<Function<RoadmapItem, RiceInputs>> decide = ArgumentCaptor.forClass(Function.class);
        UUID id = UUID.randomUUID();

        service.setInputs(id, scored(), "acc-1");

        verify(items).setInputsUnderLock(eq(id), decide.capture(), eq("acc-1"), eq(NOW));
        assertThat(decide.getValue().apply(item(RiceInputs.NONE, RoadmapStatus.IDEA, null, null))).isEqualTo(scored());
        assertThatThrownBy(() -> decide.getValue().apply(item(RiceInputs.NONE, RoadmapStatus.IDEA, null, NOW)))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_ITEM_ARCHIVED));
    }

    @Test
    void updateDetailsRejectsLinkingADifferentArchivedProduct() {
        RoadmapItem current = item(RiceInputs.NONE, RoadmapStatus.IDEA, UUID.randomUUID(), null);
        UUID archived = UUID.randomUUID();
        when(items.findById(current.id())).thenReturn(Optional.of(current));
        when(products.findById(archived)).thenReturn(Optional.of(product(archived, true)));

        assertThatThrownBy(() -> service.updateDetails(current.id(),
                new RoadmapItemDetails("t", RoadmapStatus.IDEA, archived, null, null, null)))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_PRODUCT_ARCHIVED));
    }

    @Test
    void updateDetailsKeepsAnAlreadyLinkedProductEvenIfArchivedAndClearsOnNull() {
        UUID linked = UUID.randomUUID();
        RoadmapItem current = item(RiceInputs.NONE, RoadmapStatus.IDEA, linked, null);
        when(items.findById(current.id())).thenReturn(Optional.of(current));

        service.updateDetails(current.id(), new RoadmapItemDetails("t", RoadmapStatus.PLANNED, linked, null, null, null));
        service.updateDetails(current.id(), new RoadmapItemDetails("t", RoadmapStatus.PLANNED, null, null, null, null));

        verify(products, never()).findById(any());
        ArgumentCaptor<RoadmapItemDetails> saved = ArgumentCaptor.forClass(RoadmapItemDetails.class);
        verify(items, org.mockito.Mockito.times(2)).updateDetails(eq(current.id()), saved.capture());
        assertThat(saved.getAllValues().get(0).productId()).isEqualTo(linked);
        assertThat(saved.getAllValues().get(1).productId()).isNull();
    }

    @Test
    void updateDetailsTrimsTextAndOnlyPassesTitleForManualItems() {
        RoadmapItem manual = item(RiceInputs.NONE, RoadmapStatus.IDEA, null, null);
        RoadmapItem jira = jiraItem(null);
        when(items.findById(manual.id())).thenReturn(Optional.of(manual));
        when(items.findById(jira.id())).thenReturn(Optional.of(jira));
        RoadmapItemDetails request =
                new RoadmapItemDetails("  New  ", RoadmapStatus.PLANNED, null, " Q1 ", "   ", " note ");

        service.updateDetails(manual.id(), request);
        service.updateDetails(jira.id(), request);

        ArgumentCaptor<RoadmapItemDetails> manualSaved = ArgumentCaptor.forClass(RoadmapItemDetails.class);
        verify(items).updateDetails(eq(manual.id()), manualSaved.capture());
        assertThat(manualSaved.getValue())
                .isEqualTo(new RoadmapItemDetails("New", RoadmapStatus.PLANNED, null, "Q1", null, "note"));
        ArgumentCaptor<RoadmapItemDetails> jiraSaved = ArgumentCaptor.forClass(RoadmapItemDetails.class);
        verify(items).updateDetails(eq(jira.id()), jiraSaved.capture());
        assertThat(jiraSaved.getValue().title()).isNull();
    }

    @Test
    void archiveAndRestoreOnlyWriteWhenTheStateChanges() {
        RoadmapItem open = item(RiceInputs.NONE, RoadmapStatus.IDEA, null, null);
        RoadmapItem archived = item(RiceInputs.NONE, RoadmapStatus.IDEA, null, EARLIER);
        when(items.findById(open.id())).thenReturn(Optional.of(open));
        when(items.findById(archived.id())).thenReturn(Optional.of(archived));

        service.archive(open.id());
        service.archive(archived.id());
        service.restore(archived.id());
        service.restore(open.id());

        verify(items).setArchivedAt(open.id(), NOW);
        verify(items).setArchivedAt(archived.id(), null);
        verify(items, org.mockito.Mockito.times(2)).setArchivedAt(any(), any());
    }

    @Test
    void historyDelegatesAfterCheckingTheItemExists() {
        RoadmapItem open = item(RiceInputs.NONE, RoadmapStatus.IDEA, null, null);
        List<InputChange> changes = List.of();
        when(items.findById(open.id())).thenReturn(Optional.of(open));
        when(history.historyOf(open.id())).thenReturn(changes);
        UUID missing = UUID.randomUUID();
        when(items.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.history(open.id())).isSameAs(changes);
        assertThatThrownBy(() -> service.history(missing)).isInstanceOf(RoadmapItemNotFoundException.class);
    }
}
