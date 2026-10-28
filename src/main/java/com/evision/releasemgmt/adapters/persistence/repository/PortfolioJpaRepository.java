package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Read-only: extends the bare Repository marker so no save/delete is inherited. Every value is a bound parameter.
public interface PortfolioJpaRepository extends Repository<ReleaseEntity, UUID> {

    @Query(value = """
            SELECT c.client_id    AS clientId,
                   c.product_id   AS productId,
                   c.release_id   AS releaseId,
                   c.installed_at AS installedAt
              FROM current_client_release c
            """, nativeQuery = true)
    List<ClientCurrentRunRow> currentRuns();

    @Query(value = """
            SELECT c.product_id AS productId, COUNT(DISTINCT c.client_id) AS clients
              FROM current_client_release c JOIN client cl ON cl.id = c.client_id
             WHERE cl.archived_at IS NULL
             GROUP BY c.product_id
            """, nativeQuery = true)
    List<ProductClientCountRow> runningClientCounts();

    @Query(value = "SELECT rp.release_id AS releaseId, rp.product_id AS productId FROM release_product rp",
            nativeQuery = true)
    List<ReleaseProductPairRow> releaseProducts();

    @Query(value = """
            SELECT d.client_id AS clientId, d.product_id AS productId
              FROM effective_client_deployment d
             WHERE d.outcome = 'SUCCESS' AND d.installed_at >= :since
            """, nativeQuery = true)
    List<RecentInstallRow> successfulInstallsSince(@Param("since") Instant since);
}
