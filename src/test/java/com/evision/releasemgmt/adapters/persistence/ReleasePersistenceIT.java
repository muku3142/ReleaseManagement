package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseDuplicateException;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ReleasePersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ReleaseRepository repository;

    private static ReleaseItem item(String key) {
        return new ReleaseItem(new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed",
                "Dev Person", "Reporter Person", List.of("2026.08"),
                "Ship it", List.of("Looks good", "He said \"ship it\""), 2), Instant.now());
    }

    @Test
    void createPersistsReleaseAndItsItemsInOneCall() {
        Release release = Release.create("Create Test", "1.0", "CNV", "rm@acme.com", Instant.now());

        repository.create(release, List.of(item("CNV-1"), item("CNV-2")));

        Release loaded = repository.findById(release.id()).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Create Test");
        assertThat(loaded.version()).isEqualTo("1.0");
        assertThat(loaded.status()).isEqualTo(ReleaseStatus.OPEN);
        assertThat(loaded.createdBy()).isEqualTo("rm@acme.com");
        assertThat(loaded.jiraProjectKey()).isEqualTo("CNV");
        assertThat(loaded.items()).hasSize(2);
        ReleaseItem loadedItem = loaded.items().stream()
                .filter(i -> i.ticketKey().equals("CNV-1"))
                .findFirst().orElseThrow();
        assertThat(loadedItem.ticket().comments()).contains("He said \"ship it\"");
    }

    @Test
    void duplicateNameAndVersionIsRejectedAsReleaseDuplicateException() {
        repository.create(Release.create("Dup Test", "1.0", "CNV", "rm@acme.com", Instant.now()), List.of());

        assertThatThrownBy(() -> repository.create(
                Release.create("Dup Test", "1.0", "CNV", "rm@acme.com", Instant.now()), List.of()))
                .isInstanceOf(ReleaseDuplicateException.class);
    }

    @Test
    void sameNameDifferentVersionIsAllowed() {
        repository.create(Release.create("Multi Version", "1.0", "CNV", "rm@acme.com", Instant.now()), List.of());
        repository.create(Release.create("Multi Version", "1.1", "CNV", "rm@acme.com", Instant.now()), List.of());

        assertThat(repository.search("Multi Version"))
                .extracting(ReleaseSummary::version)
                .containsExactlyInAnyOrder("1.0", "1.1");
    }

    @Test
    void itemsComeBackSortedByTicketKeyAscending() {
        Release release = Release.create("Sort Items", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-30"), item("CNV-10"), item("CNV-20")));

        List<String> keys = repository.findById(release.id()).orElseThrow().items().stream()
                .map(ReleaseItem::ticketKey)
                .toList();
        assertThat(keys).containsExactly("CNV-10", "CNV-20", "CNV-30");
    }

    @Test
    void manualItemsComeBackSortedByTitleAscending() {
        Release release = Release.create("Sort Manual", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of());

        repository.addManualItem(release.id(),
                ManualItem.create("Zeta", "desc", ManualItemCategory.OTHER, Instant.now()));
        repository.addManualItem(release.id(),
                ManualItem.create("Alpha", "desc", ManualItemCategory.DATABASE_SCRIPT, Instant.now()));
        repository.addManualItem(release.id(),
                ManualItem.create("Mid", "desc", ManualItemCategory.OTHER, Instant.now()));

        Release loaded = repository.findById(release.id()).orElseThrow();
        assertThat(loaded.manualItems()).extracting(ManualItem::title)
                .containsExactly("Alpha", "Mid", "Zeta");
        assertThat(loaded.manualItems().get(0).category()).isEqualTo(ManualItemCategory.DATABASE_SCRIPT);
    }

    @Test
    void addItemsIsSeparateFromCreateAndAccumulates() {
        Release release = Release.create("Accumulate", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-1")));

        repository.addItems(release.id(), List.of(item("CNV-2"), item("CNV-3")));

        assertThat(repository.findById(release.id()).orElseThrow().items()).hasSize(3);
    }

    @Test
    void removeItemDeletesOnlyThatTicket() {
        Release release = Release.create("Remove Item", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-1"), item("CNV-2")));

        repository.removeItem(release.id(), "CNV-1");

        List<String> keys = repository.findById(release.id()).orElseThrow().items().stream()
                .map(ReleaseItem::ticketKey)
                .toList();
        assertThat(keys).containsExactly("CNV-2");
    }

    @Test
    void replaceItemSnapshotsOverwritesFieldsAndStampsLastRefreshedAt() {
        Release release = Release.create("Refresh Test", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-1")));
        Instant addedAt = repository.findById(release.id()).orElseThrow().items().get(0).addedAt();

        ReleaseItem updated = new ReleaseItem(new TicketDetail("CNV-1", "Summary CNV-1", "Bug", "In Progress",
                "Fixed", "Dev Person", "Reporter Person", List.of("2026.08"), "Ship it",
                List.of("Looks good", "He said \"ship it\""), 2), Instant.now());
        Instant refreshedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        repository.replaceItemSnapshots(release.id(), List.of(updated), refreshedAt);

        Release loaded = repository.findById(release.id()).orElseThrow();
        ReleaseItem storedItem = loaded.items().get(0);
        assertThat(storedItem.ticket().status()).isEqualTo("In Progress");
        assertThat(storedItem.addedAt()).isEqualTo(addedAt);
        assertThat(loaded.lastRefreshedAt()).isEqualTo(refreshedAt);
    }

    @Test
    void freezeRecordsActorAndTimestamp() {
        Release release = Release.create("Freeze Test", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of());

        Instant frozenAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        repository.freeze(release.id(), "rm@acme.com", frozenAt);

        Release loaded = repository.findById(release.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(ReleaseStatus.FROZEN);
        assertThat(loaded.frozenBy()).isEqualTo("rm@acme.com");
        assertThat(loaded.frozenAt()).isEqualTo(frozenAt);
    }

    @Test
    void deleteRemovesReleaseAndCascadesToItems() {
        Release release = Release.create("Delete Test", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-1")));
        repository.addManualItem(release.id(),
                ManualItem.create("Note", "desc", ManualItemCategory.OTHER, Instant.now()));

        repository.delete(release.id());

        assertThat(repository.findById(release.id())).isEmpty();
        assertThat(repository.search("")).extracting(ReleaseSummary::id).doesNotContain(release.id());
    }

    // The service's requireOpen() runs against a snapshot read in an earlier transaction, with a
    // paginated Jira call in between, so it cannot be what enforces the one-way door. These two
    // call the repository directly - exactly what a concurrent freeze leaves the service doing.
    @Test
    void aFrozenReleaseRefusesEveryMutationAtThePersistenceBoundary() {
        Release release = Release.create("Frozen Guard", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-1")));
        UUID id = release.id();
        repository.freeze(id, "rm@acme.com", Instant.now());
        ManualItem note = ManualItem.create("Note", "desc", ManualItemCategory.OTHER, Instant.now());

        assertThatThrownBy(() -> repository.addItems(id, List.of(item("CNV-2"))))
                .isInstanceOf(ReleaseFrozenException.class);
        assertThatThrownBy(() -> repository.removeItem(id, "CNV-1"))
                .isInstanceOf(ReleaseFrozenException.class);
        assertThatThrownBy(() -> repository.replaceItemSnapshots(id, List.of(item("CNV-1")), Instant.now()))
                .isInstanceOf(ReleaseFrozenException.class);
        assertThatThrownBy(() -> repository.addManualItem(id, note))
                .isInstanceOf(ReleaseFrozenException.class);
        assertThatThrownBy(() -> repository.removeManualItem(id, note.id()))
                .isInstanceOf(ReleaseFrozenException.class);
        assertThatThrownBy(() -> repository.delete(id))
                .isInstanceOf(ReleaseFrozenException.class);

        Release loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.items()).extracting(ReleaseItem::ticketKey).containsExactly("CNV-1");
        assertThat(loaded.manualItems()).isEmpty();
        assertThat(loaded.lastRefreshedAt()).isNull();
    }

    @Test
    void freezingAnAlreadyFrozenReleaseIsRefusedAndKeepsTheOriginalActorAndTimestamp() {
        Release release = Release.create("Double Freeze", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of());
        Instant frozenAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        repository.freeze(release.id(), "rm@acme.com", frozenAt);

        assertThatThrownBy(() -> repository.freeze(release.id(), "someone.else@acme.com", Instant.now()))
                .isInstanceOf(ReleaseFrozenException.class);

        Release loaded = repository.findById(release.id()).orElseThrow();
        assertThat(loaded.frozenBy()).isEqualTo("rm@acme.com");
        assertThat(loaded.frozenAt()).isEqualTo(frozenAt);
    }

    @Test
    void searchMatchesNameOrVersionCaseInsensitively() {
        Release sprint = Release.create("Sprint 42", "2026.08", "CNV", "rm@acme.com", Instant.now());
        Release hotfix = Release.create("Hotfix", "1.0.1", "CNV", "rm@acme.com", Instant.now());
        repository.create(sprint, List.of());
        repository.create(hotfix, List.of());

        assertThat(repository.search("sprint")).extracting(ReleaseSummary::id)
                .containsExactly(sprint.id());
        assertThat(repository.search("2026")).extracting(ReleaseSummary::id)
                .containsExactly(sprint.id());
        assertThat(repository.search("HOTFIX")).extracting(ReleaseSummary::id)
                .containsExactly(hotfix.id());
        assertThat(repository.search("")).extracting(ReleaseSummary::id)
                .contains(sprint.id(), hotfix.id());
    }

    @Test
    void searchTreatsSqlWildcardsAndQuotesAsLiteralText() {
        Release release = Release.create("Wildcard Test", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of());

        assertThat(repository.search("%")).isEmpty();
        assertThat(repository.search("_")).isEmpty();
        assertThat(repository.search("'; DROP TABLE release; --")).isEmpty();

        assertThat(repository.search("")).extracting(ReleaseSummary::id).contains(release.id());
    }

    @Test
    void summaryItemCountSumsTicketAndManualItems() {
        Release release = Release.create("Count Test", "1.0", "CNV", "rm@acme.com", Instant.now());
        repository.create(release, List.of(item("CNV-1"), item("CNV-2")));
        repository.addManualItem(release.id(),
                ManualItem.create("Note", "desc", ManualItemCategory.OTHER, Instant.now()));

        ReleaseSummary summary = repository.search("Count Test").stream()
                .filter(s -> s.id().equals(release.id()))
                .findFirst().orElseThrow();
        assertThat(summary.itemCount()).isEqualTo(3);
    }
}
