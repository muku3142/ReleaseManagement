package com.evision.ragservice.adapters.web;

import com.evision.ragservice.adapters.web.dto.CollectionResponse;
import com.evision.ragservice.adapters.web.dto.PutCollectionRequest;
import com.evision.ragservice.adapters.web.dto.ReindexResponse;
import com.evision.ragservice.application.CollectionService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/collections")
public class CollectionController {

    public static final String NAME_PATTERN = "[A-Za-z0-9._:-]{1,200}";

    private final CollectionService collections;

    public CollectionController(CollectionService collections) {
        this.collections = collections;
    }

    @PutMapping("/{collection}")
    public ResponseEntity<CollectionResponse> put(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String collection,
            @RequestBody(required = false) PutCollectionRequest request) {
        CollectionService.PutResult result = collections.put(clientId, collection,
                request == null ? null : request.embeddingModel());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CollectionResponse.from(collections.status(clientId, collection)));
    }

    @GetMapping
    public List<CollectionResponse> list(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId) {
        return collections.list(clientId).stream().map(CollectionResponse::from).toList();
    }

    @GetMapping("/{collection}")
    public CollectionResponse get(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String collection) {
        return CollectionResponse.from(collections.status(clientId, collection));
    }

    @PostMapping("/{collection}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReindexResponse reindex(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String collection) {
        return new ReindexResponse(collections.reindex(clientId, collection));
    }
}
