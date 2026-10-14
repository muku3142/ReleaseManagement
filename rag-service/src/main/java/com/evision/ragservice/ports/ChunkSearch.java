package com.evision.ragservice.ports;

import com.evision.ragservice.domain.MetadataFilter;
import com.evision.ragservice.domain.SearchHit;
import java.util.List;
import java.util.UUID;

public interface ChunkSearch {

    /** collectionIds must already be scoped to the caller; scores are comparable only because all share one embedding model. */
    List<SearchHit> search(List<UUID> collectionIds, String embeddingModel, float[] queryVector, MetadataFilter filter, int k);
}
