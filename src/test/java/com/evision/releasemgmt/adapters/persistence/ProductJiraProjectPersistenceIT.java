package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductJiraKeyTakenException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ProductJiraProjectPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ProductRepository products;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE product CASCADE");
    }

    @Test
    void keysAreReplacedAndListedSorted() {
        Product a = products.add("Alpha");

        products.setJiraProjectKeys(a.id(), List.of("CNV", "ABC"));
        assertThat(products.jiraProjectKeys()).containsEntry(a.id(), List.of("ABC", "CNV"));

        products.setJiraProjectKeys(a.id(), List.of("CNV"));
        assertThat(products.jiraProjectKeys().get(a.id())).containsExactly("CNV");

        products.setJiraProjectKeys(a.id(), List.of());
        assertThat(products.jiraProjectKeys()).doesNotContainKey(a.id());
    }

    @Test
    void ownerLookupIsCaseInsensitive() {
        Product a = products.add("Alpha");
        products.setJiraProjectKeys(a.id(), List.of("CNV"));

        assertThat(products.productIdOwningJiraKey("cnv")).contains(a.id());
        assertThat(products.productIdOwningJiraKey("XYZ")).isEmpty();
    }

    @Test
    void aKeyOwnedByAnotherProductIsRejectedAndLeavesTheOwnerIntact() {
        Product a = products.add("Alpha");
        Product b = products.add("Beta");
        products.setJiraProjectKeys(a.id(), List.of("CNV"));

        assertThatThrownBy(() -> products.setJiraProjectKeys(b.id(), List.of("CNV")))
                .isInstanceOf(ProductJiraKeyTakenException.class);

        assertThat(products.jiraProjectKeys().get(a.id())).containsExactly("CNV");
        assertThat(products.jiraProjectKeys()).doesNotContainKey(b.id());
    }
}
