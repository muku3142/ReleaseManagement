package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ClientDeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientDeploymentJpaRepository extends JpaRepository<ClientDeploymentEntity, UUID> {

    List<ClientDeploymentEntity> findByReleaseIdOrderByRecordedAtAsc(UUID releaseId);

    boolean existsByReleaseId(UUID releaseId);

    @Query(value = """
            SELECT d.id AS id, d.release_id AS releaseId, d.product_id AS productId, d.client_id AS clientId,
                   d.outcome AS outcome, d.installed_at AS installedAt, d.recorded_at AS recordedAt,
                   d.recorded_by AS recordedBy, d.note AS note, d.supersedes_id AS supersedesId,
                   d.correction_reason AS correctionReason, r.name AS releaseName, r.version AS releaseVersion
              FROM client_deployment d
              JOIN release r ON r.id = d.release_id
             WHERE d.client_id = :clientId
             ORDER BY d.installed_at DESC, d.recorded_at DESC
            """, nativeQuery = true)
    List<ClientInstallRow> historyOfClient(@Param("clientId") UUID clientId);

    @Query(value = """
            SELECT c.product_id AS productId, c.release_id AS releaseId, r.name AS releaseName,
                   r.version AS releaseVersion, c.installed_at AS installedAt
              FROM current_client_release c
              JOIN release r ON r.id = c.release_id
             WHERE c.client_id = :clientId
            """, nativeQuery = true)
    List<CurrentRunRow> currentRunsOfClient(@Param("clientId") UUID clientId);
}
