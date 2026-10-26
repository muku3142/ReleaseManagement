package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    interface IdCount {
        UUID getProductId();

        long getN();
    }

    interface ReleaseProductRow {
        UUID getReleaseId();

        UUID getId();

        String getName();

        Instant getArchivedAt();

        boolean getHasInstalls();
    }

    interface KeyRow {
        UUID getProductId();

        String getProjectKey();
    }

    @Query(value = "SELECT product_id AS productId, project_key AS projectKey FROM product_jira_project ORDER BY project_key",
            nativeQuery = true)
    List<KeyRow> jiraProjectKeys();

    @Query(value = "SELECT product_id FROM product_jira_project WHERE upper(project_key) = upper(:key)", nativeQuery = true)
    List<UUID> findProductIdsOwningJiraKey(@Param("key") String key);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM product_jira_project WHERE product_id = :productId", nativeQuery = true)
    void deleteJiraKeys(@Param("productId") UUID productId);

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO product_jira_project (product_id, project_key) VALUES (:productId, :key)", nativeQuery = true)
    void insertJiraKey(@Param("productId") UUID productId, @Param("key") String key);

    @Query(value = "SELECT product_id AS productId, count(*) AS n FROM product_client GROUP BY product_id",
            nativeQuery = true)
    List<IdCount> clientCounts();

    @Query(value = "SELECT product_id AS productId, count(*) AS n FROM release_product GROUP BY product_id",
            nativeQuery = true)
    List<IdCount> releaseCounts();

    @Query(value = """
            SELECT rp.release_id AS releaseId, p.id AS id, p.name AS name, p.archived_at AS archivedAt,
                   EXISTS (SELECT 1 FROM client_deployment d
                           WHERE d.release_id = rp.release_id AND d.product_id = p.id) AS hasInstalls
            FROM release_product rp JOIN product p ON p.id = rp.product_id
            WHERE rp.release_id IN (:releaseIds)
            ORDER BY lower(p.name)
            """, nativeQuery = true)
    List<ReleaseProductRow> productsOfReleases(@Param("releaseIds") Collection<UUID> releaseIds);

    @Query(value = "SELECT product_id FROM release_product WHERE release_id = :releaseId", nativeQuery = true)
    List<UUID> findProductIdsOfRelease(@Param("releaseId") UUID releaseId);

    // Any row counts, voided and superseded included (D-15-15).
    @Query(value = "SELECT DISTINCT product_id FROM client_deployment WHERE release_id = :releaseId", nativeQuery = true)
    List<UUID> findProductIdsWithInstalls(@Param("releaseId") UUID releaseId);

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO release_product (release_id, product_id) VALUES (:releaseId, :productId)",
            nativeQuery = true)
    void insertReleaseProduct(@Param("releaseId") UUID releaseId, @Param("productId") UUID productId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM release_product WHERE release_id = :releaseId AND product_id = :productId",
            nativeQuery = true)
    void deleteReleaseProduct(@Param("releaseId") UUID releaseId, @Param("productId") UUID productId);
}
