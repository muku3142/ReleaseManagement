package com.evision.ragservice.adapters.web;

import com.evision.ragservice.adapters.web.dto.SearchRequest;
import com.evision.ragservice.adapters.web.dto.SearchResponse;
import com.evision.ragservice.application.SearchService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @PostMapping("/v1/collections/{collection}/search")
    public SearchResponse search(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = CollectionController.NAME_PATTERN) String collection,
            @Valid @RequestBody SearchRequest request) {
        return SearchResponse.from(search.search(clientId, collection, request.query(), request.filter(), request.k()));
    }
}
