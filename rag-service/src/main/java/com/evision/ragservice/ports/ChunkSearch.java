package com.evision.ragservice.ports;

import com.evision.ragservice.domain.MetadataFilter;
import com.evision.ragservice.domain.SearchHit;
import java.util.List;
import java.util.UUID;

public interface ChunkSearch {

    List<SearchHit> search(UUID collectionId, String embeddingModel, float[] queryVector, MetadataFilter filter, int k);
}
