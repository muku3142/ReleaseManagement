package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.PortfolioNameRequest;
import com.evision.releasemgmt.adapters.web.dto.ProductJiraProjectsRequest;
import com.evision.releasemgmt.adapters.web.dto.ProductListResponse;
import com.evision.releasemgmt.application.ProductService;
import com.evision.releasemgmt.domain.portfolio.Product;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Products are archived, never deleted, so history keeps its labels (D-15-07). No actor is stored for edits.
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Comparator<Product> ARCHIVED_LAST_THEN_NAME =
            Comparator.comparing(Product::archived).thenComparing(Product::name, String.CASE_INSENSITIVE_ORDER);

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ProductListResponse list() {
        return respond();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductListResponse add(@Valid @RequestBody PortfolioNameRequest request) {
        service.add(request.name());
        return respond();
    }

    @PutMapping("/{productId}")
    public ProductListResponse rename(@PathVariable UUID productId, @Valid @RequestBody PortfolioNameRequest request) {
        service.rename(productId, request.name());
        return respond();
    }

    @PostMapping("/{productId}/archive")
    public ProductListResponse archive(@PathVariable UUID productId) {
        service.archive(productId);
        return respond();
    }

    @PostMapping("/{productId}/unarchive")
    public ProductListResponse unarchive(@PathVariable UUID productId) {
        service.unarchive(productId);
        return respond();
    }

    @PutMapping("/{productId}/jira-projects")
    public ProductListResponse setJiraProjects(@PathVariable UUID productId,
                                               @Valid @RequestBody ProductJiraProjectsRequest request) {
        service.setJiraProjectKeys(productId, request.keys());
        return respond();
    }

    private ProductListResponse respond() {
        Map<UUID, Long> clientCounts = service.clientCounts();
        Map<UUID, Long> releaseCounts = service.releaseCounts();
        Map<UUID, List<String>> jiraKeys = service.jiraProjectKeys();
        return new ProductListResponse(service.list().stream()
                .sorted(ARCHIVED_LAST_THEN_NAME)
                .map(p -> new ProductListResponse.Item(p.id(), p.name(), p.archived(),
                        clientCounts.getOrDefault(p.id(), 0L), releaseCounts.getOrDefault(p.id(), 0L),
                        jiraKeys.getOrDefault(p.id(), List.of())))
                .toList());
    }
}
