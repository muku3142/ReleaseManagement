package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
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

    private JiraSnapshot snapshot(String key, String title) {
        return new JiraSnapshot(key, "id-" + key, title, "To Do", now, now);
    }

    private static RiceInputs inputs(int reach, String impact, int confidence, String effort) {
        return new RiceInputs(reach, new BigDecimal(impact), confidence, new BigDecimal(effort));
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
    void jiraKeyIsUniqueOnTheRoadmap() {
        RoadmapItem first = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "One"), "pm@acme.com", now));

        assertThatThrownBy(() -> roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "Again"), "pm@acme.com", now)))
                .isInstanceOfSatisfying(RoadmapRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(RoadmapRejection.ROADMAP_JIRA_KEY_DUPLICATE));
        assertThat(roadmap.findIdsByJiraKey(List.of("CNV-1", "CNV-2"))).isEqualTo(Map.of("CNV-1", first.id()));
        assertThat(roadmap.findIdsByJiraKey(List.of())).isEmpty();
    }

    @Test
    void inputEditWritesOneHistoryRowPerChangedField() {
        RoadmapItem item = roadmap.add(RoadmapItem.manual("Idea", null, "pm@acme.com", now));

        roadmap.setInputsUnderLock(item.id(), current -> inputs(10, "2", 80, "4"), "pm@acme.com", now);
        List<InputChange> first = history.historyOf(item.id());
        assertThat(first).hasSize(4);
        assertThat(first).allSatisfy(change -> assertThat(change.oldValue()).isNull());

        roadmap.setInputsUnderLock(item.id(), current -> inputs(10, "3", 80, "4"), "pm@acme.com",
                now.plusSeconds(5));
        List<InputChange> second = history.historyOf(item.id());
        assertThat(second).hasSize(5);
        InputChange newest = second.get(0);
        assertThat(newest.field()).isEqualTo(RiceField.IMPACT);
        assertThat(newest.oldValue()).isEqualByComparingTo("2");
        assertThat(newest.newValue()).isEqualByComparingTo("3");

        roadmap.setInputsUnderLock(item.id(), current -> inputs(10, "3", 80, "4"), "pm@acme.com",
                now.plusSeconds(10));
        assertThat(history.historyOf(item.id())).hasSize(5);
    }

    @Test
    void refreshNeverTouchesLocalFields() {
        UUID product = products.add("Core").id();
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "Old title"), "pm@acme.com", now));
        roadmap.updateDetails(item.id(), new RoadmapItemDetails(null, RoadmapStatus.PLANNED, product, null, null, null));
        roadmap.setInputsUnderLock(item.id(), current -> inputs(10, "2", 80, "4"), "pm@acme.com", now);
        roadmap.markJiraUnavailable(item.id(), now);
        Instant later = now.plusSeconds(60);

        roadmap.applyJiraSnapshot(item.id(),
                new JiraSnapshot("CNV-9", "id-CNV-1", "New title", "Done", later, later));

        RoadmapItem stored = roadmap.findById(item.id()).orElseThrow();
        assertThat(stored.inputs().changedFields(inputs(10, "2", 80, "4"))).isEmpty();
        assertThat(stored.productId()).isEqualTo(product);
        assertThat(stored.status()).isEqualTo(RoadmapStatus.PLANNED);
        assertThat(stored.title()).isEqualTo("New title");
        assertThat(stored.jiraKey()).isEqualTo("CNV-9");
        assertThat(stored.jiraStatus()).isEqualTo("Done");
        assertThat(stored.jiraSyncedAt()).isEqualTo(later);
        assertThat(stored.jiraUnavailableSince()).isNull();
        assertThat(history.historyOf(item.id())).hasSize(4);
    }

    @Test
    void markJiraUnavailableKeepsTheFirstTime() {
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "One"), "pm@acme.com", now));
        Instant t1 = now.plusSeconds(1);

        roadmap.markJiraUnavailable(item.id(), t1);
        roadmap.markJiraUnavailable(item.id(), now.plusSeconds(2));

        assertThat(roadmap.findById(item.id()).orElseThrow().jiraUnavailableSince()).isEqualTo(t1);
    }

    @Test
    void detailsUpdateNeverChangesAJiraTitle() {
        RoadmapItem item = roadmap.add(RoadmapItem.fromJira(snapshot("CNV-1", "Jira title"), "pm@acme.com", now));

        roadmap.updateDetails(item.id(),
                new RoadmapItemDetails("Other", RoadmapStatus.IN_PROGRESS, null, null, null, null));

        RoadmapItem stored = roadmap.findById(item.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("Jira title");
        assertThat(stored.status()).isEqualTo(RoadmapStatus.IN_PROGRESS);
    }

    @Test
    void historyRowsAreAppendOnly() {
        RoadmapItem item = roadmap.add(RoadmapItem.manual("Idea", null, "pm@acme.com", now));
        roadmap.setInputsUnderLock(item.id(), current -> inputs(10, "2", 80, "4"), "pm@acme.com", now);

        assertThatThrownBy(() -> jdbc.update("UPDATE roadmap_item_input_history SET changed_by = 'x'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void unknownItemIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> roadmap.setInputsUnderLock(unknown, current -> RiceInputs.NONE, "pm@acme.com", now))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThatThrownBy(() -> roadmap.updateDetails(unknown,
                new RoadmapItemDetails("T", RoadmapStatus.IDEA, null, null, null, null)))
                .isInstanceOf(RoadmapItemNotFoundException.class);
        assertThatThrownBy(() -> roadmap.setArchivedAt(unknown, now))
                .isInstanceOf(RoadmapItemNotFoundException.class);
    }
}
