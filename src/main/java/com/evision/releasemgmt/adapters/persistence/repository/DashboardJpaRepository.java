package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Read-only: extends the bare Repository marker so no save/delete is inherited. Every value is a bound parameter.
public interface DashboardJpaRepository extends Repository<ReleaseEntity, UUID> {

    @Query(value = """
            SELECT r.id          AS id,
                   r.name        AS name,
                   r.version     AS version,
                   r.status      AS status,
                   r.created_at  AS createdAt,
                   r.frozen_at   AS frozenAt,
                   (SELECT count(*) FROM release_manual_item m WHERE m.release_id = r.id) AS manualCount
              FROM release r
             ORDER BY r.created_at DESC, r.id
            """, nativeQuery = true)
    List<DashboardReleaseRow> releases();

    // D-08-05: one definition of the split. Development = authored at or before first_done_at, post-Done = strictly after.
    @Query(value = """
            SELECT i.release_id AS releaseId, i.ticket_key AS ticketKey,
                   i.enrichment_status AS enrichmentStatus, i.enrichment_incomplete AS enrichmentIncomplete,
                   i.commits_source AS commitsSource, i.history_state AS historyState,
                   i.first_done_at AS firstDoneAt, i.reopen_count AS reopenCount,
                   count(c.id) FILTER (WHERE i.first_done_at IS NOT NULL AND c.authored_at <= i.first_done_at) AS commitsToDone,
                   count(c.id) FILTER (WHERE i.first_done_at IS NOT NULL AND c.authored_at >  i.first_done_at) AS postDoneCommits,
                   count(c.id) AS commitTotal
              FROM release_ticket_item i
              LEFT JOIN release_item_commit c ON c.item_id = i.id
             WHERE i.release_id IN (:releaseIds)
             GROUP BY i.id
             ORDER BY i.release_id, i.ticket_key
            """, nativeQuery = true)
    List<ChurnItemRow> churnItems(@Param("releaseIds") Collection<UUID> releaseIds);

    // The only SQL restatement of "effective" (D-07-11) and "not VOIDED" (D-07-15); DashboardPersistenceIT pins it to PromotionHistory.
    @Query(value = """
            SELECT p.id AS id, p.release_id AS releaseId, r.name AS releaseName, r.version AS releaseVersion,
                   p.stage_id AS stageId, s.name AS stageName, s.retired_at AS stageRetiredAt,
                   p.outcome AS outcome, p.deployed_at AS deployedAt, p.recorded_by AS recordedBy, p.note AS note,
                   (p.supersedes_id IS NOT NULL) AS corrected
              FROM promotion p
              JOIN release r           ON r.id = p.release_id
              JOIN environment_stage s ON s.id = p.stage_id
             WHERE p.outcome <> 'VOIDED'
               AND NOT EXISTS (SELECT 1 FROM promotion c WHERE c.supersedes_id = p.id)
               AND (CAST(:stageId AS uuid) IS NULL OR p.stage_id = CAST(:stageId AS uuid))
               AND p.deployed_at >= :from AND p.deployed_at < :to
             ORDER BY p.deployed_at DESC, p.recorded_at DESC, p.id DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DeploymentHistoryRow> deployments(@Param("stageId") UUID stageId, @Param("from") Instant from,
                                           @Param("to") Instant to, @Param("limit") int limit,
                                           @Param("offset") int offset);
}
