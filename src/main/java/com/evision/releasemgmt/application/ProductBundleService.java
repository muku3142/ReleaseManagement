package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductBundle;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.ProductBundleRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductBundleService {

    private final ProductRepository products;
    private final ProductBundleRepository bundles;

    public ProductBundleService(ProductRepository products, ProductBundleRepository bundles) {
        this.products = products;
        this.bundles = bundles;
    }

    public List<ProductBundle> list(UUID productId) {
        requireProduct(productId);
        return bundles.findByProduct(productId);
    }

    public void save(UUID productId, String name, List<String> ticketKeys) {
        if (requireProduct(productId).archived()) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED);
        }
        bundles.save(productId, name.strip(), ticketKeys.stream().distinct().toList());
    }

    public void delete(UUID productId, UUID bundleId) {
        requireProduct(productId);
        bundles.delete(productId, bundleId);
    }

    private Product requireProduct(UUID productId) {
        return products.findById(productId).orElseThrow(ProductNotFoundException::new);
    }
}
