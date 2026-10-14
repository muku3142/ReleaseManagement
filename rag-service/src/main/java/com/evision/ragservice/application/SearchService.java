package com.evision.ragservice.application;

import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.domain.EmbeddingPurpose;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.MetadataFilter;
import com.evision.ragservice.domain.MetadataRules;
import com.evision.ragservice.domain.SearchHit;
import com.evision.ragservice.ports.ChunkSearch;
import com.evision.ragservice.ports.EmbeddingPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final CollectionService collections;
    private final EmbeddingPort embeddings;
    private final ChunkSearch chunks;
    private final int defaultK;
    private final int maxK;

    public SearchService(CollectionService collections, EmbeddingPort embeddings, ChunkSearch chunks,
                         @Value("${rag.search.default-k}") int defaultK,
                         @Value("${rag.search.max-k}") int maxK) {
        this.collections = collections;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.defaultK = defaultK;
        this.maxK = maxK;
    }

    public List<SearchHit> search(UUID clientId, String collection, String query, Map<String, Object> filter,
                                  Integer k) {
        DocumentCollection target = collections.require(clientId, collection);
        MetadataRules.rejectNul("query", query);
        int limit = k == null ? defaultK : k;
        if (limit < 1 || limit > maxK) {
            throw new InvalidContentException("k must be between 1 and " + maxK);
        }
        MetadataFilter parsed = MetadataRules.parseFilter(filter);
        float[] vector = embeddings.embed(target.embeddingModel(), EmbeddingPurpose.QUERY, List.of(query)).getFirst();
        return chunks.search(List.of(target.id()), target.embeddingModel(), vector, parsed, limit);
    }
}
