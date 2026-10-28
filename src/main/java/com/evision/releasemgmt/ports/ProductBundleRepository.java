package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.ProductBundle;

import java.util.List;
import java.util.UUID;

public interface ProductBundleRepository {

    /** Ordered by lower(name), ticket keys sorted. */
    List<ProductBundle> findByProduct(UUID productId);

    /**
     * Puts the keys into the bundle with this name (case-insensitive, stored spelling kept; created if absent),
     * moving them out of any other bundle of the product. Bundles left empty are deleted.
     */
    void save(UUID productId, String name, List<String> ticketKeys);

    /** A no-op when the bundle is unknown or belongs to another product. */
    void delete(UUID productId, UUID bundleId);
}
