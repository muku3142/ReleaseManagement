package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.portfolio.Product;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    /** Archived included, ordered by lower(name). */
    List<Product> findAll();

    Optional<Product> findById(UUID productId);

    /** PRODUCT_NAME_DUPLICATE on a case-insensitive clash. */
    Product add(String name);

    /** PRODUCT_NAME_DUPLICATE; ProductNotFoundException. */
    void rename(UUID productId, String name);

    /** A null archivedAt unarchives. */
    void setArchivedAt(UUID productId, Instant archivedAt);

    /** Linked clients per product, archived clients included. */
    Map<UUID, Long> clientCounts();

    /** Releases covering each product. */
    Map<UUID, Long> releaseCounts();

    /** Jira project keys per product, upper-case, sorted; products without keys are absent. */
    Map<UUID, List<String>> jiraProjectKeys();

    /** Product owning this key (case-insensitive), if any. */
    Optional<UUID> productIdOwningJiraKey(String key);

    /** Replaces the product's keys. ProductJiraKeyTakenException(null, null) when the unique index fires (race). */
    void setJiraProjectKeys(UUID productId, List<String> keys);
}
