package com.evision.releasemgmt.domain.portfolio;

import static com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome.FAILED;
import static com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome.SUCCESS;
import static com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome.VOIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientDeploymentHistoryTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00.123456789Z");
    private static final UUID RELEASE = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    private final Client active = client("Alpha", null, PRODUCT);
    private final Client other = client("Beta", null, PRODUCT);
    private final Client archived = client("Gamma", NOW.minus(Duration.ofDays(1)), PRODUCT);
    private final Client unlinked = client("Delta", null);

    private static Client client(String name, Instant archivedAt, UUID... products) {
        return new Client(UUID.randomUUID(), name, archivedAt, Set.of(products));
    }

    private static ReleaseProduct product(UUID id, boolean archived) {
        return new ReleaseProduct(id, "P", archived, false);
    }

    private ClientDeploymentHistory history(boolean frozen, List<ClientDeployment> entries) {
        return new ClientDeploymentHistory(RELEASE, frozen, List.of(product(PRODUCT, false)),
                List.of(active, other, archived, unlinked), entries);
    }

    private ClientDeploymentHistory frozen() {
        return history(true, List.of());
    }

    private static ClientDeploymentDraft draft(ClientDeploymentOutcome outcome, Instant installedAt, String note) {
        return new ClientDeploymentDraft(PRODUCT, outcome, installedAt, note);
    }

    private static ClientDeployment row(Client c, ClientDeploymentOutcome outcome, UUID supersedes) {
        return new ClientDeployment(UUID.randomUUID(), RELEASE, PRODUCT, c.id(), outcome, NOW.minus(Duration.ofDays(2)),
                NOW.minus(Duration.ofDays(2)), "acc-1", null, supersedes, supersedes == null ? null : "fix");
    }

    private static ClientDeploymentRejection rejection(Throwable t) {
        return ((ClientDeploymentRejectedException) t).reason();
    }

    @Test
    void recordOnOpenReleaseIsRejected() {
        assertThatThrownBy(() -> history(false, List.of()).record(draft(SUCCESS, null, null), List.of(active.id()), "a", NOW))
                .isInstanceOf(ClientDeploymentRejectedException.class)
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_RELEASE_NOT_FROZEN));
    }

    @Test
    void recordVoidedIsRejected() {
        assertThatThrownBy(() -> frozen().record(draft(VOIDED, null, null), List.of(active.id()), "a", NOW))
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_OUTCOME_NOT_RECORDABLE));
    }

    @Test
    void recordInstalledAtBeyondToleranceIsRejected() {
        Instant future = NOW.plus(ClientDeploymentHistory.FUTURE_TOLERANCE).plusSeconds(1);
        assertThatThrownBy(() -> frozen().record(draft(SUCCESS, future, null), List.of(active.id()), "a", NOW))
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_INSTALLED_AT_INVALID));
    }

    @Test
    void recordInstalledAtWithinToleranceIsAccepted() {
        Instant slightlyAhead = NOW.plusSeconds(60);
        assertThat(frozen().record(draft(SUCCESS, slightlyAhead, null), List.of(active.id()), "a", NOW)).hasSize(1);
    }

    @Test
    void recordHasNoLowerBound() {
        Instant longBefore = Instant.parse("2025-10-12T10:00:00Z");
        List<ClientDeployment> rows = frozen().record(draft(SUCCESS, longBefore, null), List.of(active.id()), "a", NOW);
        assertThat(rows).singleElement().satisfies(r -> assertThat(r.installedAt()).isEqualTo(longBefore));
    }

    @Test
    void recordForProductNotOnReleaseIsRejected() {
        ClientDeploymentDraft foreign = new ClientDeploymentDraft(UUID.randomUUID(), SUCCESS, null, null);
        assertThatThrownBy(() -> frozen().record(foreign, List.of(active.id()), "a", NOW))
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_PRODUCT_NOT_ON_RELEASE));
    }

    @Test
    void recordForArchivedProductIsRejected() {
        ClientDeploymentHistory h = new ClientDeploymentHistory(RELEASE, true, List.of(product(PRODUCT, true)),
                List.of(active), List.of());
        assertThatThrownBy(() -> h.record(draft(SUCCESS, null, null), List.of(active.id()), "a", NOW))
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_PRODUCT_ARCHIVED));
    }

    @Test
    void recordNamesEveryOffendingClientInRequestOrder() {
        UUID unknown = UUID.randomUUID();
        List<UUID> ids = List.of(active.id(), archived.id(), unlinked.id(), unknown);
        assertThatThrownBy(() -> frozen().record(draft(SUCCESS, null, null), ids, "a", NOW))
                .isInstanceOf(ClientDeploymentRejectedException.class)
                .satisfies(t -> {
                    ClientDeploymentRejectedException ex = (ClientDeploymentRejectedException) t;
                    assertThat(ex.reason()).isEqualTo(ClientDeploymentRejection.INSTALL_CLIENTS_REJECTED);
                    assertThat(ex.clients()).containsExactly(
                            new RejectedClient(archived.id(), "Gamma", RejectedClient.Reason.ARCHIVED),
                            new RejectedClient(unlinked.id(), "Delta", RejectedClient.Reason.NOT_LINKED),
                            new RejectedClient(unknown, null, RejectedClient.Reason.NOT_LINKED));
                });
    }

    @Test
    void recordForOneValidClientProducesOneRow() {
        assertThat(frozen().record(draft(SUCCESS, null, null), List.of(active.id()), "a", NOW)).hasSize(1);
    }

    @Test
    void recordCollapsesDuplicatesAndNormalisesFields() {
        List<ClientDeployment> rows = frozen().record(draft(FAILED, null, "  boom  "),
                List.of(active.id(), active.id(), other.id()), "acc-9", NOW);
        Instant nowMicros = Instant.parse("2026-10-12T10:00:00.123456Z");
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(ClientDeployment::clientId).containsExactly(active.id(), other.id());
        assertThat(rows).extracting(ClientDeployment::id).doesNotHaveDuplicates();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.recordedAt()).isEqualTo(nowMicros);
            assertThat(r.installedAt()).isEqualTo(nowMicros);
            assertThat(r.note()).isEqualTo("boom");
            assertThat(r.recordedBy()).isEqualTo("acc-9");
            assertThat(r.releaseId()).isEqualTo(RELEASE);
            assertThat(r.productId()).isEqualTo(PRODUCT);
            assertThat(r.outcome()).isEqualTo(FAILED);
            assertThat(r.supersedesId()).isNull();
            assertThat(r.correctionReason()).isNull();
        });
    }

    @Test
    void recordStoresBlankNoteAsNull() {
        assertThat(frozen().record(draft(SUCCESS, null, "   "), List.of(active.id()), "a", NOW))
                .singleElement().satisfies(r -> assertThat(r.note()).isNull());
    }

    @Test
    void correctOfUnknownIdIsNotFound() {
        assertThatThrownBy(() -> frozen().correct(UUID.randomUUID(), SUCCESS, NOW, null, "why", "a", NOW))
                .isInstanceOf(ClientDeploymentNotFoundException.class);
    }

    @Test
    void correctOfSupersededRowIsRejected() {
        ClientDeployment original = row(active, SUCCESS, null);
        ClientDeployment fix = row(active, SUCCESS, original.id());
        assertThatThrownBy(() -> history(true, List.of(original, fix)).correct(original.id(), FAILED, NOW, null, "why", "a", NOW))
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_ALREADY_SUPERSEDED));
    }

    @Test
    void correctToVoidedKeepsIdentityOfOriginal() {
        ClientDeployment original = row(active, SUCCESS, null);
        ClientDeployment fix = history(true, List.of(original)).correct(original.id(), VOIDED, NOW, null, "  typo  ", "acc-2", NOW);
        assertThat(fix.outcome()).isEqualTo(VOIDED);
        assertThat(fix.supersedesId()).isEqualTo(original.id());
        assertThat(fix.releaseId()).isEqualTo(original.releaseId());
        assertThat(fix.productId()).isEqualTo(original.productId());
        assertThat(fix.clientId()).isEqualTo(original.clientId());
        assertThat(fix.correctionReason()).isEqualTo("typo");
        assertThat(fix.id()).isNotEqualTo(original.id());
    }

    @Test
    void correctOfArchivedClientsRowIsStillAccepted() {
        ClientDeployment original = row(archived, SUCCESS, null);
        assertThat(history(true, List.of(original)).correct(original.id(), FAILED, NOW, null, "why", "a", NOW)).isNotNull();
    }

    @Test
    void correctWithFutureInstalledAtIsRejected() {
        ClientDeployment original = row(active, SUCCESS, null);
        Instant future = NOW.plus(ClientDeploymentHistory.FUTURE_TOLERANCE).plusSeconds(1);
        assertThatThrownBy(() -> history(true, List.of(original)).correct(original.id(), SUCCESS, future, null, "why", "a", NOW))
                .satisfies(t -> assertThat(rejection(t)).isEqualTo(ClientDeploymentRejection.INSTALL_INSTALLED_AT_INVALID));
    }
}
