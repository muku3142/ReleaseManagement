package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReleaseProductRepository {

    /** Ordered by lower(name). */
    List<ReleaseProduct> productsOf(UUID releaseId);

    /** Releases without products are absent from the map. */
    Map<UUID, List<ReleaseProduct>> productsOfReleases(Collection<UUID> releaseIds);

    /** Locks the release without a status check (D-15-01), rejects an archived product (PRODUCT_ARCHIVED) and a swap away from a product with installs (PRODUCT_HAS_INSTALLS_ON_RELEASE, D-15-15), stamps items_changed_at only on change (D-15-02). */
    void setProduct(UUID releaseId, UUID productId);
}
