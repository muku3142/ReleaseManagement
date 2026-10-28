package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ProductBundleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductBundleJpaRepository extends JpaRepository<ProductBundleEntity, UUID> {

    interface TicketRow {
        UUID getBundleId();

        String getTicketKey();
    }

    @Query(value = "SELECT * FROM product_bundle WHERE product_id = :productId ORDER BY lower(name)", nativeQuery = true)
    List<ProductBundleEntity> findByProduct(@Param("productId") UUID productId);

    @Query(value = "SELECT * FROM product_bundle WHERE product_id = :productId AND lower(name) = lower(:name)",
            nativeQuery = true)
    Optional<ProductBundleEntity> findByProductAndName(@Param("productId") UUID productId, @Param("name") String name);

    @Query(value = """
            SELECT bundle_id AS bundleId, ticket_key AS ticketKey
            FROM product_bundle_ticket WHERE product_id = :productId ORDER BY ticket_key
            """, nativeQuery = true)
    List<TicketRow> ticketsOfProduct(@Param("productId") UUID productId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM product_bundle_ticket WHERE product_id = :productId AND ticket_key IN (:keys)",
            nativeQuery = true)
    void removeTickets(@Param("productId") UUID productId, @Param("keys") Collection<String> keys);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO product_bundle_ticket (bundle_id, product_id, ticket_key)
            VALUES (:bundleId, :productId, :key)
            """, nativeQuery = true)
    void insertTicket(@Param("bundleId") UUID bundleId, @Param("productId") UUID productId, @Param("key") String key);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM product_bundle b WHERE b.product_id = :productId
              AND NOT EXISTS (SELECT 1 FROM product_bundle_ticket t WHERE t.bundle_id = b.id)
            """, nativeQuery = true)
    void deleteEmpty(@Param("productId") UUID productId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM product_bundle WHERE id = :bundleId AND product_id = :productId", nativeQuery = true)
    void deleteOne(@Param("productId") UUID productId, @Param("bundleId") UUID bundleId);
}
