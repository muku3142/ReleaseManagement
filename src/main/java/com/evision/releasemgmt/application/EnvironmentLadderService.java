package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.promotion.LadderEditRejectedException;
import com.evision.releasemgmt.domain.promotion.LadderEditRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.promotion.StageNotFoundException;
import com.evision.releasemgmt.ports.EnvironmentLadderRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EnvironmentLadderService {

    private final EnvironmentLadderRepository ladder;
    private final Clock clock;

    public EnvironmentLadderService(EnvironmentLadderRepository ladder, Clock clock) {
        this.ladder = ladder;
        this.clock = clock;
    }

    public List<Stage> ladder() {
        return ladder.findAll();
    }

    public Set<UUID> referencedStageIds() {
        return ladder.findReferencedStageIds();
    }

    public void add(String name) {
        ladder.add(name.trim());
    }

    public void rename(UUID id, String name) {
        find(id);
        ladder.rename(id, name.trim());
    }

    public void reorder(List<UUID> ids) {
        // D-07-02: the new order must be a permutation of every stage, retired included.
        Set<UUID> current = new HashSet<>();
        ladder.findAll().forEach(s -> current.add(s.id()));
        if (ids.size() != current.size() || !current.equals(new HashSet<>(ids))) {
            throw new LadderEditRejectedException(LadderEditRejection.STAGE_ORDER_MISMATCH);
        }
        ladder.reorder(ids);
    }

    public void retire(UUID id) {
        Stage stage = find(id);
        if (stage.retired()) {
            return;
        }
        // D-07-04: Production is never retired, and the ladder keeps at least one active stage.
        if (stage.production()) {
            throw new LadderEditRejectedException(LadderEditRejection.PRODUCTION_STAGE_PROTECTED);
        }
        requireAnotherActiveStage(stage);
        ladder.setRetiredAt(id, clock.instant());
    }

    public void restore(UUID id) {
        if (find(id).retired()) {
            ladder.setRetiredAt(id, null);
        }
    }

    // D-07-04: there is deliberately no operation that clears the Production flag without setting another.
    public void moveProduction(UUID id) {
        Stage stage = find(id);
        if (stage.retired()) {
            throw new LadderEditRejectedException(LadderEditRejection.STAGE_RETIRED);
        }
        if (!stage.production()) {
            ladder.moveProductionFlag(id);
        }
    }

    public void delete(UUID id) {
        Stage stage = find(id);
        if (stage.production()) {
            throw new LadderEditRejectedException(LadderEditRejection.PRODUCTION_STAGE_PROTECTED);
        }
        // D-07-03: a stage any promotion references can only be retired.
        if (ladder.findReferencedStageIds().contains(id)) {
            throw new LadderEditRejectedException(LadderEditRejection.STAGE_IN_USE);
        }
        if (!stage.retired()) {
            requireAnotherActiveStage(stage);
        }
        ladder.delete(id);
    }

    private void requireAnotherActiveStage(Stage stage) {
        boolean another = ladder.findAll().stream().anyMatch(s -> !s.retired() && !s.id().equals(stage.id()));
        if (!another) {
            throw new LadderEditRejectedException(LadderEditRejection.LAST_ACTIVE_STAGE);
        }
    }

    private Stage find(UUID id) {
        return ladder.findAll().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(StageNotFoundException::new);
    }
}
