package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.RoadmapItemEntity;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemSource;
import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoadmapItemJpaRepository extends JpaRepository<RoadmapItemEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoadmapItemEntity r WHERE r.id = :id")
    Optional<RoadmapItemEntity> findByIdForUpdate(@Param("id") UUID id);

    List<RoadmapItemEntity> findByJiraKeyIn(Collection<String> keys);

    // Names only Jira-owned columns so a refresh can never write RICE inputs, product or status (ROAD-04).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoadmapItemEntity r set r.title = :title, r.jiraKey = :key, r.jiraStatus = :jstatus, "
            + "r.jiraUpdatedAt = :updated, r.jiraSyncedAt = :synced, r.jiraUnavailableSince = null where r.id = :id")
    int applyJiraSnapshot(@Param("id") UUID id, @Param("title") String title, @Param("key") String key,
                          @Param("jstatus") String jiraStatus, @Param("updated") Instant updated,
                          @Param("synced") Instant synced);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoadmapItemEntity r set r.jiraUnavailableSince = :since "
            + "where r.id = :id and r.jiraUnavailableSince is null")
    int markJiraUnavailable(@Param("id") UUID id, @Param("since") Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoadmapItemEntity r set r.status = :status, r.productId = :productId, "
            + "r.targetQuarter = :quarter, r.owner = :owner, r.notes = :notes where r.id = :id")
    int updateDetails(@Param("id") UUID id, @Param("status") RoadmapStatus status,
                      @Param("productId") UUID productId, @Param("quarter") String quarter,
                      @Param("owner") String owner, @Param("notes") String notes);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoadmapItemEntity r set r.title = :title where r.id = :id and r.source = :manual")
    int updateManualTitle(@Param("id") UUID id, @Param("title") String title,
                          @Param("manual") RoadmapItemSource manual);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoadmapItemEntity r set r.archivedAt = :at where r.id = :id")
    int setArchivedAt(@Param("id") UUID id, @Param("at") Instant at);
}
