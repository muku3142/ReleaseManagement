package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.InputChangeKind;
import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RiceCalculation;
import com.evision.releasemgmt.domain.roadmap.RiceEdits;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RiceOverrides;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RoadmapPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private RoadmapRepository roadmap;

    @Autowired
    private RoadmapHistoryRepository history;

    @Autowired
    private ProductRepository products;

    @Autowired
    private JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE roadmap_item CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID anyProduct() {
        return products.add("P-" + UUID.randomUUID()).id();
    }

    private JiraSnapshot snapshot(String key, String title) {
        return new JiraSnapshot(key, "id-" + key, title, "To Do", now, now, null, null);
    }

    @Test
    void manualItemRoundTripsEveryField() {
        UUID product = products.add("Core").id();
        RoadmapItem item = roadmap.add(RoadmapItem.manual("Old", product, "pm@acme.com", now));

        roadmap.updateDetails(item.id(),
                new RoadmapItemDetails("New", RoadmapStatus.PLANNED, product, "2027-Q1", "Dana", "line1\nline2"));

        RoadmapItem stored = roadmap.findById(item.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("New");
        assertThat(stored.status()).isEqualTo(RoadmapStatus.PLANNED);
        assertThat(stored.productId()).isEqualTo(product);
        assertThat(stored.targetQuarter()).isEqualTo("2027-Q1");
        assertThat(stored.owner()).isEqualTo("Dana");
        assertThat(stored.notes()).isEqualTo("line1\nline2");
        assertThat(stored.createdBy()).isEqualTo("pm@acme.com");
    }

    @Test
    void productIsRequired() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO roadmap_item (id, source, title, created_at, created_by) "
                + "VALUES (?, 'MANUAL', 't', now(), 'u')", UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void findMatchingFiltersInSql() {
        UUID a = anyProduct();
        UUID b = anyProduct();
        RoadmapItem aOpen = roadmap.add(RoadmapItem.manual("A open", a, "u", now));
        RoadmapItem aDone = roadmap.add(RoadmapItem.manual("A done", a, "u", now));
        roadmap.updateDetails(aDone.id(), new RoadmapItemDetails(null, RoadmapStatus.DONE, a, null, null, null));
        RoadmapItem aArchived = roadmap.add(RoadmapItem.manual("A archived", a, "u", now));
        roadmap.setArchivedAt(aArchived.id(), now);
        RoadmapItem bOpen = roadmap.add(RoadmapItem.manual("B open", b, "u", now));

        assertThat(ids(new RoadmapFilter(Set.of(), false, false))).containsExactlyInAnyOrder(aOpen.id(), bOpen.id());
        assertThat(ids(new RoadmapFilter(Set.of(a), false, false))).containsExactly(aOpen.id());
        assertThat(ids(new RoadmapFilter(Set.of(a, b), false, false)))
                .containsExactlyInAnyOrder(aOpen.id(), bOpen.id());
        assertThat(ids(new RoadmapFilter(Set.of(), true, false)))
                .containsExactlyInAnyOrder(aOpen.id(), aDone.id(), bOpen.id());
        assertThat(ids(new RoadmapFilter(Set.of(), false, true)))
                .containsExactlyInAnyOrder(aOpen.id(), aArchived.id(), bOpen.id());
    }

    private List<UUID> ids(RoadmapFilter filter) {
        return roadmap.findMatching(filter).stream().map(RoadmapItem::id).toList();
    }

    @Test
    void jiraKeyIsUniqueOnTheRoadmap() {
        RoadmapItem first = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "One"), anyProduct(), "pm@acme.com", now));

        assertThatThrownBy(() -> roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "Again"), anyProduct(), "pm@acme.com", now)))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_JIRA_KEY_DUPLICATE));
        assertThat(roadmap.findIdsByJiraKey(List.of("CNV-1", "CNV-2"))).isEqualTo(Map.of("CNV-1", first.id()));
        assertThat(roadmap.findIdsByJiraKey(List.of())).isEmpty();
    }

    private RoadmapItem enter(RoadmapItem item, String impact, Integer confidence, String effort, Instant at) {
        RiceInputs requested = new RiceInputs(null, impact == null ? null : new BigDecimal(impact), confidence,
                effort == null ? null : new BigDecimal(effort));
        return roadmap.updateInputsUnderLock(item.id(),
                current -> RiceEdits.entered(current, requested, "pm@acme.com", at));
    }

    private RoadmapItem overrideReach(RoadmapItem item, int reach, String reason) {
        return roadmap.updateInputsUnderLock(item.id(), current -> RiceEdits.override(current,
                RiceCalculation.calculated(current, 3), RiceField.REACH, BigDecimal.valueOf(reach), reason,
                "pm@acme.com", now));
    }

    @Test
    void inputEditWritesOneHistoryRowPerChangedField() {
        RoadmapItem item = roadmap.add(RoadmapItem.manual("Idea", anyProduct(), "pm@acme.com", now));

        enter(item, "2", 80, "4", now);
        List<InputChange> first = history.historyOf(item.id());
        assertThat(first).hasSize(3);
        assertThat(first).allSatisfy(change -> {
            assertThat(change.oldValue()).isNull();
            assertThat(change.kind()).isEqualTo(InputChangeKind.ENTERED);
            assertThat(change.reason()).isNull();
        });

        enter(item, "3", 80, "4", now.plusSeconds(5));
        List<InputChange> second = history.historyOf(item.id());
        assertThat(second).hasSize(4);
        InputChange newest = second.get(0);
        assertThat(newest.field()).isEqualTo(RiceField.IMPACT);
        assertThat(newest.oldValue()).isEqualByComparingTo("2");
        assertThat(newest.newValue()).isEqualByComparingTo("3");

        enter(item, "3", 80, "4", now.plusSeconds(10));
        assertThat(history.historyOf(item.id())).hasSize(4);
    }

    @Test
    void overrideAndResetRoundTripWithKindAndReason() {
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "One"), anyProduct(), "pm@acme.com", now));

        RoadmapItem overridden = overrideReach(item, 20, "Big client onboarding");

        RoadmapItem stored = roadmap.findById(item.id()).orElseThrow();
        assertThat(stored).isEqualTo(overridden);
        assertThat(stored.inputs().reach()).isEqualTo(20);
        assertThat(stored.overrides()).isEqualTo(new RiceOverrides("Big client onboarding", null, null));
        InputChange change = history.historyOf(item.id()).get(0);
        assertThat(change.kind()).isEqualTo(InputChangeKind.OVERRIDDEN);
        assertThat(change.reason()).isEqualTo("Big client onboarding");
        assertThat(change.oldValue()).isEqualByComparingTo("3");

        roadmap.updateInputsUnderLock(item.id(), current -> RiceEdits.reset(current,
                RiceCalculation.calculated(current, 3), RiceField.REACH, "pm@acme.com", now.plusSeconds(1)));

        RoadmapItem reset = roadmap.findById(item.id()).orElseThrow();
        assertThat(reset.inputs().reach()).isNull();
        assertThat(reset.overrides()).isEqualTo(RiceOverrides.NONE);
        assertThat(history.historyOf(item.id())).first().satisfies(c -> {
            assertThat(c.kind()).isEqualTo(InputChangeKind.RESET);
            assertThat(c.reason()).isNull();
            assertThat(c.newValue()).isEqualByComparingTo("3");
        });
    }

    @Test
    void databaseRejectsAnOverrideWithoutAReason() {
        RoadmapItem item = roadmap.add(RoadmapItem.manual("Idea", anyProduct(), "pm@acme.com", now));

        assertThatThrownBy(() -> jdbc.update("UPDATE roadmap_item SET reach = 5 WHERE id = ?", item.id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO roadmap_item_input_history "
                + "(id, item_id, field, old_value, new_value, changed_at, changed_by, kind) "
                + "VALUES (?, ?, 'REACH', null, 5, now(), 'u', 'OVERRIDDEN')", UUID.randomUUID(), item.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refreshNeverTouchesLocalFields() {
        UUID product = products.add("Core").id();
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "Old title"), anyProduct(), "pm@acme.com", now));
        roadmap.updateDetails(item.id(), new RoadmapItemDetails(null, RoadmapStatus.PLANNED, product, null, null, null));
        overrideReach(item, 10, "why");
        enter(item, null, null, "4", now);
        roadmap.markJiraUnavailable(item.id(), now);
        Instant later = now.plusSeconds(60);

        roadmap.applyJiraSnapshot(item.id(),
                new JiraSnapshot("CNV-9", "id-CNV-1", "New title", "Done", later, later, "High", 2));

        RoadmapItem stored = roadmap.findById(item.id()).orElseThrow();
        assertThat(stored.inputs().changedFields(new RiceInputs(10, null, null, new BigDecimal("4")))).isEmpty();
        assertThat(stored.overrides().reach()).isEqualTo("why");
        assertThat(stored.productId()).isEqualTo(product);
        assertThat(stored.status()).isEqualTo(RoadmapStatus.PLANNED);
        assertThat(stored.title()).isEqualTo("New title");
        assertThat(stored.jiraKey()).isEqualTo("CNV-9");
        assertThat(stored.jiraStatus()).isEqualTo("Done");
        assertThat(stored.jiraSyncedAt()).isEqualTo(later);
        assertThat(stored.jiraUnavailableSince()).isNull();
        assertThat(stored.jiraPriority()).isEqualTo("High");
        assertThat(stored.jiraReadiness()).isEqualTo(2);
        assertThat(history.historyOf(item.id())).hasSize(2);
    }

    @Test
    void markJiraUnavailableKeepsTheFirstTime() {
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "One"), anyProduct(), "pm@acme.com", now));
        Instant t1 = now.plusSeconds(1);

        roadmap.markJiraUnavailable(item.id(), t1);
        roadmap.markJiraUnavailable(item.id(), now.plusSeconds(2));

        assertThat(roadmap.findById(item.id()).orElseThrow().jiraUnavailableSince()).isEqualTo(t1);
    }

    @Test
    void detailsUpdateNeverChangesAJiraTitle() {
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "Jira title"), anyProduct(), "pm@acme.com", now));

        roadmap.updateDetails(item.id(),
                new RoadmapItemDetails("Other", RoadmapStatus.IN_PROGRESS, anyProduct(), null, null, null));

        RoadmapItem stored = roadmap.findById(item.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("Jira title");
        assertThat(stored.status()).isEqualTo(RoadmapStatus.IN_PROGRESS);
    }

    @Test
    void historyRowsAreAppendOnly() {
        RoadmapItem item = roadmap.add(RoadmapItem.manual("Idea", anyProduct(), "pm@acme.com", now));
        enter(item, "2", 80, "4", now);

        assertThatThrownBy(() -> jdbc.update("UPDATE roadmap_item_input_history SET changed_by = 'x'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void unknownItemIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> roadmap.updateInputsUnderLock(unknown,
                current -> RiceEdits.entered(current, RiceInputs.NONE, "pm@acme.com", now)))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThatThrownBy(() -> roadmap.updateDetails(unknown,
                new RoadmapItemDetails("T", RoadmapStatus.IDEA, null, null, null, null)))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThatThrownBy(() -> roadmap.setArchivedAt(unknown, now))
                .isInstanceOf(RoadmapItemNotFoundException.class);
    }
}
