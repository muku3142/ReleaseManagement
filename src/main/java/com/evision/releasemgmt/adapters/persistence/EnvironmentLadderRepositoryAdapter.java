package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.EnvironmentStageEntity;
import com.evision.releasemgmt.adapters.persistence.repository.EnvironmentStageJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.PromotionJpaRepository;
import com.evision.releasemgmt.domain.promotion.LadderEditRejectedException;
import com.evision.releasemgmt.domain.promotion.LadderEditRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.promotion.StageNotFoundException;
import com.evision.releasemgmt.ports.EnvironmentLadderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EnvironmentLadderRepositoryAdapter implements EnvironmentLadderRepository {

    private final EnvironmentStageJpaRepository jpa;
    private final PromotionJpaRepository promotionJpa;

    public EnvironmentLadderRepositoryAdapter(EnvironmentStageJpaRepository jpa, PromotionJpaRepository promotionJpa) {
        this.jpa = jpa;
        this.promotionJpa = promotionJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stage> findAll() {
        return jpa.findAllByOrderByPositionAsc().stream().map(EnvironmentLadderRepositoryAdapter::toStage).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findReferencedStageIds() {
        return promotionJpa.findReferencedStageIds();
    }

    @Override
    @Transactional
    public Stage add(String name) {
        int next = jpa.findAllByOrderByPositionAsc().stream()
                .mapToInt(EnvironmentStageEntity::getPosition).max().orElse(0) + 1;
        EnvironmentStageEntity entity = EnvironmentStageEntity.create(name, next);
        saveRejectingDuplicateName(entity);
        return toStage(entity);
    }

    @Override
    @Transactional
    public void rename(UUID stageId, String name) {
        EnvironmentStageEntity entity = load(stageId);
        entity.setName(name);
        saveRejectingDuplicateName(entity);
    }

    @Override
    @Transactional
    public void reorder(List<UUID> orderedStageIds) {
        // UNIQUE (position) is deferred to commit, so the intermediate states of this rewrite never collide.
        Map<UUID, EnvironmentStageEntity> byId = jpa.findAll().stream()
                .collect(Collectors.toMap(EnvironmentStageEntity::getId, Function.identity()));
        for (int i = 0; i < orderedStageIds.size(); i++) {
            byId.get(orderedStageIds.get(i)).setPosition(i + 1);
        }
    }

    @Override
    @Transactional
    public void setRetiredAt(UUID stageId, Instant retiredAt) {
        load(stageId).setRetiredAt(retiredAt);
    }

    @Override
    @Transactional
    public void moveProductionFlag(UUID toStageId) {
        EnvironmentStageEntity target = load(toStageId);
        jpa.findAllByOrderByPositionAsc().stream()
                .filter(EnvironmentStageEntity::isProduction)
                .forEach(holder -> holder.setProduction(false));
        // D-07-04: the partial unique index is not deferrable, so the clear must reach the database before the set.
        jpa.flush();
        target.setProduction(true);
    }

    @Override
    @Transactional
    public void delete(UUID stageId) {
        EnvironmentStageEntity entity = load(stageId);
        try {
            jpa.delete(entity);
            jpa.flush();
        } catch (DataIntegrityViolationException e) {
            // ON DELETE RESTRICT backstop for a promotion committed after the service's in-use check (D-07-03).
            throw new LadderEditRejectedException(LadderEditRejection.STAGE_IN_USE);
        }
    }

    private EnvironmentStageEntity load(UUID stageId) {
        return jpa.findById(stageId).orElseThrow(StageNotFoundException::new);
    }

    private void saveRejectingDuplicateName(EnvironmentStageEntity entity) {
        try {
            jpa.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // environment_stage_name_unique is case-insensitive and spans retired stages (D-07-03).
            throw new LadderEditRejectedException(LadderEditRejection.STAGE_NAME_DUPLICATE);
        }
    }

    private static Stage toStage(EnvironmentStageEntity e) {
        return new Stage(e.getId(), e.getName(), e.getPosition(), e.isProduction(), e.getRetiredAt());
    }
}
