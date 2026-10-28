package com.evision.releasemgmt.domain.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ClientDeploymentHistory(UUID releaseId, boolean frozen, List<ReleaseProduct> products, List<Client> clients,
                                      List<ClientDeployment> entries) {

    // A browser clock slightly ahead of the server must not make "now" look like the future (as D-07-10).
    public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(2);

    public static final Comparator<ClientDeployment> LEDGER_ORDER =
            Comparator.comparing(ClientDeployment::installedAt).thenComparing(ClientDeployment::recordedAt);

    public ClientDeploymentHistory {
        products = List.copyOf(products);
        clients = List.copyOf(clients);
        entries = List.copyOf(entries);
    }

    public Optional<UUID> supersededBy(UUID deploymentId) {
        return entries.stream()
                .filter(e -> deploymentId.equals(e.supersedesId()))
                .map(ClientDeployment::id)
                .findFirst();
    }

    public boolean isEffective(ClientDeployment deployment) {
        return supersededBy(deployment.id()).isEmpty();
    }

    public List<ClientDeployment> record(ClientDeploymentDraft draft, List<UUID> clientIds, String actor, Instant now) {
        if (!frozen) {
            throw reject(ClientDeploymentRejection.INSTALL_RELEASE_NOT_FROZEN);
        }
        if (draft.outcome() == ClientDeploymentOutcome.VOIDED) {
            throw reject(ClientDeploymentRejection.INSTALL_OUTCOME_NOT_RECORDABLE);
        }
        // Postgres stores microseconds; truncating keeps in-memory comparisons identical to what is read back.
        Instant nowMicros = now.truncatedTo(ChronoUnit.MICROS);
        Instant installedAt = draft.installedAt() == null ? nowMicros : draft.installedAt().truncatedTo(ChronoUnit.MICROS);
        requireNotInFuture(installedAt, nowMicros);
        ReleaseProduct product = products.stream()
                .filter(p -> p.id().equals(draft.productId()))
                .findFirst()
                .orElseThrow(() -> reject(ClientDeploymentRejection.INSTALL_PRODUCT_NOT_ON_RELEASE));
        if (product.archived()) {
            throw reject(ClientDeploymentRejection.INSTALL_PRODUCT_ARCHIVED);
        }
        LinkedHashSet<UUID> distinctClientIds = new LinkedHashSet<>(clientIds);
        List<RejectedClient> offenders = new ArrayList<>();
        for (UUID clientId : distinctClientIds) {
            Optional<Client> client = clients.stream().filter(c -> c.id().equals(clientId)).findFirst();
            if (client.isEmpty() || !client.get().linkedTo(draft.productId())) {
                offenders.add(new RejectedClient(clientId, client.map(Client::name).orElse(null), RejectedClient.Reason.NOT_LINKED));
            } else if (client.get().archived()) {
                offenders.add(new RejectedClient(clientId, client.get().name(), RejectedClient.Reason.ARCHIVED));
            }
        }
        // D-15-10: all-or-nothing, so no row is built until every selected client has passed.
        if (!offenders.isEmpty()) {
            throw new ClientDeploymentRejectedException(ClientDeploymentRejection.INSTALL_CLIENTS_REJECTED, offenders);
        }
        return distinctClientIds.stream()
                .map(clientId -> new ClientDeployment(UUID.randomUUID(), releaseId, draft.productId(), clientId,
                        draft.outcome(), installedAt, nowMicros, actor, trimToNull(draft.note()), null, null))
                .toList();
    }

    // A correction passes field checks only: a wrong row of an archived or unlinked client must stay correctable (A3).
    public ClientDeployment correct(UUID originalId, ClientDeploymentOutcome outcome, Instant installedAt, String note,
                                    String reason, String actor, Instant now) {
        ClientDeployment original = entries.stream()
                .filter(e -> e.id().equals(originalId))
                .findFirst()
                .orElseThrow(ClientDeploymentNotFoundException::new);
        if (!isEffective(original)) {
            throw reject(ClientDeploymentRejection.INSTALL_ALREADY_SUPERSEDED);
        }
        Instant nowMicros = now.truncatedTo(ChronoUnit.MICROS);
        Instant correctedAt = Objects.requireNonNull(installedAt).truncatedTo(ChronoUnit.MICROS);
        requireNotInFuture(correctedAt, nowMicros);
        return new ClientDeployment(UUID.randomUUID(), original.releaseId(), original.productId(), original.clientId(),
                outcome, correctedAt, nowMicros, actor, trimToNull(note), originalId, reason.strip());
    }

    // D-15-14: no lower bound - a client may have run the build before the release was frozen.
    private static void requireNotInFuture(Instant installedAt, Instant nowMicros) {
        if (installedAt.isAfter(nowMicros.plus(FUTURE_TOLERANCE))) {
            throw reject(ClientDeploymentRejection.INSTALL_INSTALLED_AT_INVALID);
        }
    }

    private static ClientDeploymentRejectedException reject(ClientDeploymentRejection reason) {
        return new ClientDeploymentRejectedException(reason);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
