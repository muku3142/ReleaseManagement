package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.assistant.IndexCollection;

import java.time.Instant;
import java.util.Optional;

// Read-only view of index state for the UI, kept apart from RagIndexPort so web code never holds a write-capable port.
// Throws RagServiceException on any failure.
public interface RagStatusPort {

    // Empty when the collection does not exist or nothing has been indexed yet.
    Optional<Instant> lastIndexedAt(IndexCollection collection);
}
