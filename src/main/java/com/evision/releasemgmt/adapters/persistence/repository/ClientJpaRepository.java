package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientJpaRepository extends JpaRepository<ClientEntity, UUID> {

    interface Link {
        UUID getClientId();

        UUID getProductId();
    }

    @Query(value = "SELECT client_id AS clientId, product_id AS productId FROM product_client", nativeQuery = true)
    List<Link> findAllLinks();

    @Query(value = "SELECT product_id FROM product_client WHERE client_id = :clientId", nativeQuery = true)
    List<UUID> findProductIdsOfClient(@Param("clientId") UUID clientId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO product_client (product_id, client_id) VALUES (:productId, :clientId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void link(@Param("clientId") UUID clientId, @Param("productId") UUID productId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM product_client WHERE client_id = :clientId AND product_id = :productId",
            nativeQuery = true)
    void unlink(@Param("clientId") UUID clientId, @Param("productId") UUID productId);

    // Any row counts, voided and superseded included (D-15-13, D-15-15).
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM client_deployment
                           WHERE client_id = :clientId AND product_id = :productId)
            """, nativeQuery = true)
    boolean existsInstall(@Param("clientId") UUID clientId, @Param("productId") UUID productId);
}
