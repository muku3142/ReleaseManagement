package com.evision.ragservice.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SearchRequest(@NotBlank @Size(max = SearchRequest.MAX_QUERY_CHARS) String query,
                            Map<String, Object> filter, Integer k) {

    public static final int MAX_QUERY_CHARS = 2000;
}
