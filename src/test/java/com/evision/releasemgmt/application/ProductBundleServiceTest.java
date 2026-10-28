package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductBundle;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.ProductBundleRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductBundleServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductBundleRepository bundles = mock(ProductBundleRepository.class);
    private final ProductBundleService service = new ProductBundleService(products, bundles);

    private final UUID id = UUID.randomUUID();

    @Test
    void unknownProductIsNotFound() {
        when(products.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(id)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.save(id, "x", List.of("A-1"))).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.delete(id, UUID.randomUUID())).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void savingOnAnArchivedProductIsRejectedButListingWorks() {
        when(products.findById(id)).thenReturn(Optional.of(new Product(id, "Old", Instant.parse("2026-01-01T00:00:00Z"))));
        ProductBundle bundle = new ProductBundle(UUID.randomUUID(), "B", List.of("A-1"));
        when(bundles.findByProduct(id)).thenReturn(List.of(bundle));

        assertThat(service.list(id)).containsExactly(bundle);
        assertThatThrownBy(() -> service.save(id, "B", List.of("A-1")))
                .isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.PRODUCT_ARCHIVED));
        verify(bundles, never()).save(id, "B", List.of("A-1"));
    }

    @Test
    void saveStripsTheNameAndDeduplicatesKeys() {
        when(products.findById(id)).thenReturn(Optional.of(new Product(id, "P", null)));

        service.save(id, "  Payments ", List.of("A-1", "A-1", "A-2"));

        verify(bundles).save(id, "Payments", List.of("A-1", "A-2"));
    }
}
