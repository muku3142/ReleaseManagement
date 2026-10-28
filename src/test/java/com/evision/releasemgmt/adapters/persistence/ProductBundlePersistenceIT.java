package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductBundle;
import com.evision.releasemgmt.ports.ProductBundleRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ProductBundlePersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ProductRepository products;

    @Autowired
    private ProductBundleRepository bundles;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE product CASCADE");
    }

    @Test
    void savingCreatesABundleWithSortedKeys() {
        Product p = products.add("Alpha");

        bundles.save(p.id(), "Payments", List.of("A-2", "A-1"));

        List<ProductBundle> found = bundles.findByProduct(p.id());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("Payments");
        assertThat(found.get(0).ticketKeys()).containsExactly("A-1", "A-2");
    }

    @Test
    void savingUnderTheSameNameInAnyCaseAddsToTheBundleAndKeepsItsSpelling() {
        Product p = products.add("Alpha");
        bundles.save(p.id(), "Payments", List.of("A-1", "A-2"));

        bundles.save(p.id(), "payments", List.of("A-3"));

        List<ProductBundle> found = bundles.findByProduct(p.id());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("Payments");
        assertThat(found.get(0).ticketKeys()).containsExactly("A-1", "A-2", "A-3");
    }

    @Test
    void savingIntoAnotherBundleMovesTheKeyAndDeletesAnEmptiedBundle() {
        Product p = products.add("Alpha");
        bundles.save(p.id(), "Payments", List.of("A-1", "A-2"));

        bundles.save(p.id(), "Other", List.of("A-1"));
        List<ProductBundle> afterFirstMove = bundles.findByProduct(p.id());
        assertThat(afterFirstMove).extracting(ProductBundle::name).containsExactly("Other", "Payments");
        assertThat(afterFirstMove.get(1).ticketKeys()).containsExactly("A-2");

        bundles.save(p.id(), "Other", List.of("A-2"));
        List<ProductBundle> found = bundles.findByProduct(p.id());
        assertThat(found).extracting(ProductBundle::name).containsExactly("Other");
        assertThat(found.get(0).ticketKeys()).containsExactly("A-1", "A-2");
    }

    @Test
    void theSameKeyMayBelongToABundleOfEachProduct() {
        Product p = products.add("Alpha");
        Product q = products.add("Beta");

        bundles.save(p.id(), "Payments", List.of("A-1"));
        bundles.save(q.id(), "Payments", List.of("A-1"));

        assertThat(bundles.findByProduct(p.id()).get(0).ticketKeys()).containsExactly("A-1");
        assertThat(bundles.findByProduct(q.id()).get(0).ticketKeys()).containsExactly("A-1");
    }

    @Test
    void deleteIsScopedToTheProductAndIgnoresUnknownIds() {
        Product p = products.add("Alpha");
        Product q = products.add("Beta");
        bundles.save(q.id(), "Payments", List.of("A-1"));
        UUID qBundle = bundles.findByProduct(q.id()).get(0).id();

        bundles.delete(p.id(), qBundle);
        bundles.delete(p.id(), UUID.randomUUID());
        assertThat(bundles.findByProduct(q.id())).hasSize(1);

        bundles.delete(q.id(), qBundle);
        assertThat(bundles.findByProduct(q.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_bundle_ticket", Long.class)).isZero();
    }
}
