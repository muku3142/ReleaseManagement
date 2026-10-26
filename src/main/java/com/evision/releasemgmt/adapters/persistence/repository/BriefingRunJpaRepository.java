package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.BriefingRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BriefingRunJpaRepository extends JpaRepository<BriefingRunEntity, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO briefing_run (id, task, product_id, slot_at, run_trigger, status, reason, attention,
                                      headline, summary, body, started_at, finished_at)
            VALUES (:id, :task, :productId, :slotAt, :trigger, :status, :reason, :attention,
                    :headline, :summary, :body, :startedAt, :finishedAt)
            ON CONFLICT (task, product_id, slot_at) WHERE slot_at IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("task") String task, @Param("productId") UUID productId,
            @Param("slotAt") Instant slotAt, @Param("trigger") String trigger, @Param("status") String status,
            @Param("reason") String reason, @Param("attention") Integer attention,
            @Param("headline") String headline, @Param("summary") String summary, @Param("body") String body,
            @Param("startedAt") Instant startedAt, @Param("finishedAt") Instant finishedAt);

    boolean existsByTaskAndProductIdAndSlotAt(String task, UUID productId, Instant slotAt);

    @Query(value = """
            SELECT max(finished_at) FROM briefing_run
            WHERE task = :task AND product_id = :productId AND status IN ('ok', 'attention', 'quiet')
            """, nativeQuery = true)
    Optional<Instant> lastSuccessfulFinishedAt(@Param("task") String task, @Param("productId") UUID productId);

    List<BriefingRunEntity> findByTaskOrderByFinishedAtDesc(String task);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM briefing_run
            WHERE task = :task AND product_id = :productId
              AND id NOT IN (SELECT id FROM briefing_run WHERE task = :task AND product_id = :productId
                             ORDER BY finished_at DESC LIMIT :keep)
            """, nativeQuery = true)
    void prune(@Param("task") String task, @Param("productId") UUID productId, @Param("keep") int keep);

    @Query(value = "SELECT status FROM briefing_run WHERE status <> 'skipped' ORDER BY finished_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<String> latestStatus();
}
