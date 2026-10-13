package com.evision.ragservice.ports;

import com.evision.ragservice.domain.CollectionStatus;
import com.evision.ragservice.domain.DocumentCollection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionStore {

    Optional<DocumentCollection> find(UUID clientId, String name);

    /** Empty on a (client_id, name) conflict. */
    Optional<DocumentCollection> create(UUID clientId, String name, String embeddingModel);

    List<CollectionStatus> listStatus(UUID clientId);

    Optional<CollectionStatus> findStatus(UUID clientId, String name);
}
