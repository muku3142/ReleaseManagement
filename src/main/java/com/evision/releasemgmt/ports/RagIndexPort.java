package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.assistant.DocRef;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;

// Every method throws RagServiceException on any failure.
public interface RagIndexPort {

    // Idempotent.
    void ensureCollection(IndexCollection collection);

    void put(IndexDocument document);

    // An absent document counts as success.
    void delete(DocRef ref);
}
