package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ProductJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ProductJpaRepository.ReleaseProductRow;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseJpaRepository;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ReleaseProductRepositoryAdapter implements ReleaseProductRepository {

    private final ReleaseJpaRepository releaseJpa;
    private final ProductJpaRepository productJpa;

    public ReleaseProductRepositoryAdapter(ReleaseJpaRepository releaseJpa, ProductJpaRepository productJpa) {
        this.releaseJpa = releaseJpa;
        this.productJpa = productJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReleaseProduct> productsOf(UUID releaseId) {
        return productsOfReleases(List.of(releaseId)).getOrDefault(releaseId, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ReleaseProduct>> productsOfReleases(Collection<UUID> releaseIds) {
        if (releaseIds.isEmpty()) {
            return Map.of();
        }
        return productJpa.productsOfReleases(releaseIds).stream().collect(Collectors.groupingBy(
                ReleaseProductRow::getReleaseId, LinkedHashMap::new,
                Collectors.mapping(r -> new ReleaseProduct(r.getId(), r.getName(), r.getArchivedAt() != null,
                        r.getHasInstalls()), Collectors.toList())));
    }

    @Override
    @Transactional
    public void setProduct(UUID releaseId, UUID productId) {
        ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
        // D-15-01: deliberately no status check - the product stays editable after FROZEN so v1.1 releases can be tagged.
        List<UUID> current = productJpa.findProductIdsOfRelease(releaseId);
        if (current.equals(List.of(productId))) {
            // An unchanged product must not stamp: it would flip the release notes to stale for nothing (WR-01).
            return;
        }
        var product = productJpa.findById(productId).orElseThrow(ProductNotFoundException::new);
        if (product.getArchivedAt() != null) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED);
        }
        if (!current.isEmpty() && !productJpa.findProductIdsWithInstalls(releaseId).isEmpty()) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_HAS_INSTALLS_ON_RELEASE);
        }
        current.forEach(old -> productJpa.deleteReleaseProduct(releaseId, old));
        productJpa.insertReleaseProduct(releaseId, productId);
        release.setItemsChangedAt(Instant.now());
    }
}
