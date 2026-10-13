package com.evision.ragservice.ports;

import com.evision.ragservice.domain.ClientApp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientStore {

    /** Empty when the name is taken. */
    Optional<ClientApp> create(String name, byte[] keyHash);

    /** Revoked keys never match. */
    Optional<UUID> findActiveIdByKeyHash(byte[] keyHash);

    /** At most one write per minute per client. */
    void touchLastUsed(UUID clientId);

    /** False when the client is unknown or revoked. */
    boolean replaceKey(UUID clientId, byte[] keyHash);

    /** False when the client is unknown. */
    boolean revoke(UUID clientId);

    Optional<ClientApp> find(UUID clientId);

    List<ClientApp> list();
}
