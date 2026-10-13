package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.domain.SearchHit;
import java.util.List;
import java.util.Map;

public record SearchResponse(List<Hit> results) {

    public record Hit(String documentId, String title, String url, Map<String, Object> metadata, int chunkIndex,
                      String text, double score) {

        static Hit from(SearchHit hit) {
            return new Hit(hit.documentId(), hit.title(), hit.url(), hit.metadata(), hit.chunkIndex(), hit.text(),
                    hit.score());
        }
    }

    public static SearchResponse from(List<SearchHit> hits) {
        return new SearchResponse(hits.stream().map(Hit::from).toList());
    }
}
