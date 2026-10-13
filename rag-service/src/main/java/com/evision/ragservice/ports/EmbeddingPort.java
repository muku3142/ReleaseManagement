package com.evision.ragservice.ports;

import com.evision.ragservice.domain.EmbeddingPurpose;
import java.util.List;

public interface EmbeddingPort {

    // must equal vector(768) in V1__rag_schema.sql
    int DIMENSIONS = 768;

    /** Throws EmbeddingUnavailableException on an outage and EmbeddingRejectedException on a content error. */
    List<float[]> embed(String model, EmbeddingPurpose purpose, List<String> inputs);

    boolean isAvailable();
}
