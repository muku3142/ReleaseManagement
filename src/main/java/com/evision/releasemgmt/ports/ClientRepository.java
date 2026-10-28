package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.Client;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {

    /** Archived included, ordered by lower(name), each with its productIds. */
    List<Client> findAll();

    Optional<Client> findById(UUID clientId);

    /** CLIENT_NAME_DUPLICATE on a case-insensitive clash. */
    Client add(String name);

    void rename(UUID clientId, String name);

    void setArchivedAt(UUID clientId, Instant archivedAt);

    /** Idempotent. */
    void link(UUID clientId, UUID productId);

    /** CLIENT_LINK_HAS_INSTALLS when any client_deployment row exists for the pair (D-15-13, D-15-15). */
    void unlink(UUID clientId, UUID productId);
}
