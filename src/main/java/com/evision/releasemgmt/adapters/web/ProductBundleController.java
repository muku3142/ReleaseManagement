package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.ProductBundleListResponse;
import com.evision.releasemgmt.adapters.web.dto.ProductBundleRequest;
import com.evision.releasemgmt.application.ProductBundleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/bundles")
public class ProductBundleController {

    private final ProductBundleService service;

    public ProductBundleController(ProductBundleService service) {
        this.service = service;
    }

    @GetMapping
    public ProductBundleListResponse list(@PathVariable UUID productId) {
        return respond(productId);
    }

    @PostMapping
    public ProductBundleListResponse save(@PathVariable UUID productId, @Valid @RequestBody ProductBundleRequest request) {
        service.save(productId, request.name(), request.ticketKeys());
        return respond(productId);
    }

    @DeleteMapping("/{bundleId}")
    public ProductBundleListResponse delete(@PathVariable UUID productId, @PathVariable UUID bundleId) {
        service.delete(productId, bundleId);
        return respond(productId);
    }

    private ProductBundleListResponse respond(UUID productId) {
        return ProductBundleListResponse.of(service.list(productId));
    }
}
