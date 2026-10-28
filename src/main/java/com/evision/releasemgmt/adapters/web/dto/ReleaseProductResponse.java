package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;

import java.util.UUID;

public record ReleaseProductResponse(UUID id, String name, boolean archived, boolean hasInstalls) {

    public static ReleaseProductResponse from(ReleaseProduct product) {
        return new ReleaseProductResponse(product.id(), product.name(), product.archived(),
                product.hasInstalls());
    }
}
