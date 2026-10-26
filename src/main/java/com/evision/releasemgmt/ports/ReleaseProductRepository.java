package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ReleaseProductRepository {

    /** Ordered by lower(name). */
    List<ReleaseProduct> productsOf(UUID releaseId);

    /** Releases without products are absent from the map. */
    Map<UUID, List<ReleaseProduct>> productsOfReleases(Collection<UUID> releaseIds);

    /**
     * Locks the release row without a status check (D-15-01), rejects an empty set (RELEASE_LAST_PRODUCT, D-15-18),
     * newly added archived products (PRODUCT_ARCHIVED, D-15-07) and removal of a product with any install row
     * (PRODUCT_HAS_INSTALLS_ON_RELEASE, D-15-15), stamps items_changed_at only when the set changed (D-15-02).
     */
    void replace(UUID releaseId, Set<UUID> productIds);
}
