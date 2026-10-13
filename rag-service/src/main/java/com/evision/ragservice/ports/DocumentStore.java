package com.evision.ragservice.ports;

import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.domain.DocumentStatus;
import com.evision.ragservice.domain.DocumentView;
import com.evision.ragservice.domain.QueueDepth;
import java.util.Optional;
import java.util.UUID;

public interface DocumentStore {

    /** Returns the resulting status. */
    DocumentStatus upsert(UUID collectionId, DocumentContent content, byte[] contentHash);

    boolean delete(UUID collectionId, String externalId);

    Optional<DocumentView> find(UUID collectionId, String externalId);

    int requeueAll(UUID collectionId);

    QueueDepth queueDepth(UUID clientId);
}
