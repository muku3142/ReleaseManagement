package com.evision.ragservice.adapters.persistence;

import com.evision.ragservice.domain.MetadataFilter;
import com.evision.ragservice.domain.SearchHit;
import com.evision.ragservice.ports.ChunkSearch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class ChunkSearchRepositoryAdapter implements ChunkSearch {

    private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    ChunkSearchRepositoryAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SearchHit> search(List<UUID> collectionIds, String embeddingModel, float[] queryVector,
                                  MetadataFilter filter, int k) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.collection_id, d.external_id, d.title, d.url, d.metadata, c.ordinal, c.content,
                       1 - (c.embedding <=> CAST(:q AS vector)) AS score
                FROM chunk c JOIN document d ON d.id = c.document_id
                WHERE c.collection_id IN (:collectionIds) AND c.embedding_model = :model
                """);
        Map<String, Object> filterParams = new LinkedHashMap<>();
        List<MetadataFilter.Clause> clauses = filter.clauses();
        for (int i = 0; i < clauses.size(); i++) {
            MetadataFilter.Clause clause = clauses.get(i);
            sql.append(" AND (");
            for (int j = 0; j < clause.anyOf().size(); j++) {
                String name = "f" + i + "_" + j;
                sql.append(j > 0 ? " OR " : "").append("d.metadata @> CAST(:").append(name).append(" AS jsonb)");
                // jsonb containment is type-sensitive: string "3" does not match number 3 (D-09-07)
                filterParams.put(name, objectMapper.writeValueAsString(Map.of(clause.key(), clause.anyOf().get(j))));
            }
            sql.append(')');
        }
        sql.append(" ORDER BY c.embedding <=> CAST(:q AS vector) LIMIT :k");

        return jdbc.sql(sql.toString())
                .param("q", VectorLiteral.of(queryVector))
                .param("collectionIds", collectionIds)
                .param("model", embeddingModel)
                .param("k", k)
                .params(filterParams)
                .query((rs, row) -> new SearchHit(
                        rs.getObject("collection_id", UUID.class),
                        rs.getString("external_id"),
                        rs.getString("title"),
                        rs.getString("url"),
                        objectMapper.readValue(rs.getString("metadata"), METADATA),
                        rs.getInt("ordinal"),
                        rs.getString("content"),
                        rs.getDouble("score")))
                .list();
    }
}
