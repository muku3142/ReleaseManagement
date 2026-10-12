package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.promotion.Stage;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface EnvironmentLadderRepository {

    /** Every stage, retired included, ordered by position. */
    List<Stage> findAll();

    /** Stages any promotion references (D-07-03). */
    Set<UUID> findReferencedStageIds();

    /** Appends at max position + 1; STAGE_NAME_DUPLICATE when the name exists, case-insensitively. */
    Stage add(String name);

    void rename(UUID stageId, String name);

    /** Rewrites positions 1..n in one transaction. */
    void reorder(List<UUID> orderedStageIds);

    /** A null instant restores the stage. */
    void setRetiredAt(UUID stageId, Instant retiredAt);

    /** Clears the current holder and flags toStageId in one transaction. */
    void moveProductionFlag(UUID toStageId);

    /** STAGE_IN_USE when a promotion references the stage. */
    void delete(UUID stageId);
}
