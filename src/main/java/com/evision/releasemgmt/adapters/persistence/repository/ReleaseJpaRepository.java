package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReleaseJpaRepository extends JpaRepository<ReleaseEntity, UUID> {

    // REL-05. Parameterised throughout - `:query` is a bound parameter concatenated by Postgres,
    // never by Java string building, so a value containing % _ ' or -- is matched literally as
    // data and cannot alter the statement. itemCount is the two item tables summed, which is what
    // the release library's Items column shows.
    @Query(value = """
            SELECT r.id                AS id,
                   r.name              AS name,
                   r.version           AS version,
                   r.status            AS status,
                   r.jira_project_key  AS jiraProjectKey,
                   r.created_at        AS createdAt,
                   r.created_by        AS createdBy,
                   r.frozen_at         AS frozenAt,
                   r.frozen_by         AS frozenBy,
                   r.last_refreshed_at AS lastRefreshedAt,
                   (SELECT count(*) FROM release_ticket_item t WHERE t.release_id = r.id)
                 + (SELECT count(*) FROM release_manual_item m WHERE m.release_id = r.id)
                                       AS itemCount
              FROM release r
             WHERE :query = ''
                OR r.name ILIKE '%' || :query || '%'
                OR r.version ILIKE '%' || :query || '%'
             ORDER BY r.created_at DESC
            """, nativeQuery = true)
    List<ReleaseSummaryRow> search(@Param("query") String query);

    // Only OPEN releases are valid targets for the Add-to-release picker (D-03-02).
    List<ReleaseEntity> findByStatusOrderByCreatedAtDesc(ReleaseStatus status);
}
