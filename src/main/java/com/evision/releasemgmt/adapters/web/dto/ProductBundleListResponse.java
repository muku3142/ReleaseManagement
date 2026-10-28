package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.ProductBundle;

import java.util.List;
import java.util.UUID;

public record ProductBundleListResponse(List<Item> bundles) {

    public record Item(UUID id, String name, List<String> ticketKeys) {
    }

    public static ProductBundleListResponse of(List<ProductBundle> bundles) {
        return new ProductBundleListResponse(
                bundles.stream().map(b -> new Item(b.id(), b.name(), b.ticketKeys())).toList());
    }
}
