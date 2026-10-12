package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.EnvironmentStageEntity;
import com.evision.releasemgmt.adapters.persistence.entity.PromotionEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import com.evision.releasemgmt.adapters.persistence.repository.EnvironmentStageJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.PromotionJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseJpaRepository;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Component
public class PromotionRepositoryAdapter implements PromotionRepository {

    private final PromotionJpaRepository jpa;
    private final EnvironmentStageJpaRepository stageJpa;
    private final ReleaseJpaRepository releaseJpa;

    public PromotionRepositoryAdapter(PromotionJpaRepository jpa, EnvironmentStageJpaRepository stageJpa,
                                      ReleaseJpaRepository releaseJpa) {
        this.jpa = jpa;
        this.stageJpa = stageJpa;
        this.releaseJpa = releaseJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionHistory load(UUID releaseId) {
        return history(releaseJpa.findById(releaseId).orElseThrow(ReleaseNotFoundException::new));
    }

    @Override
    @Transactional
    public Promotion appendUnderLock(UUID releaseId, Function<PromotionHistory, Promotion> decide) {
        // D-07-07: "next stage" only holds if no other recording for this release can commit between the
        // check and the insert; same reasoning as ReleaseRepositoryAdapter.lockOpen. Taking the lock first
        // also serialises two corrections of one entry, so the unique index on supersedes_id is a backstop
        // whose violation would indicate a bug - hence DataIntegrityViolationException is not caught.
        ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
        Promotion promotion = decide.apply(history(release));
        jpa.saveAndFlush(toEntity(promotion));
        return promotion;
    }

    private PromotionHistory history(ReleaseEntity release) {
        List<Stage> stages = stageJpa.findAllByOrderByPositionAsc().stream()
                .map(PromotionRepositoryAdapter::toStage)
                .toList();
        List<Promotion> entries = jpa.findByReleaseIdOrderByRecordedAtAsc(release.getId()).stream()
                .map(PromotionRepositoryAdapter::toPromotion)
                .toList();
        return new PromotionHistory(release.getId(), release.getStatus() == ReleaseStatus.FROZEN,
                release.getFrozenAt(), stages, entries);
    }

    private static Stage toStage(EnvironmentStageEntity e) {
        return new Stage(e.getId(), e.getName(), e.getPosition(), e.isProduction(), e.getRetiredAt());
    }

    private static Promotion toPromotion(PromotionEntity e) {
        return new Promotion(e.getId(), e.getReleaseId(), e.getStageId(), e.getOutcome(), e.getDeployedAt(),
                e.getRecordedAt(), e.getRecordedBy(), e.getNote(), e.getSupersedesId(), e.getCorrectionReason());
    }

    private static PromotionEntity toEntity(Promotion p) {
        return new PromotionEntity(p.id(), p.releaseId(), p.stageId(), p.outcome(), p.deployedAt(), p.recordedAt(),
                p.recordedBy(), p.note(), p.supersedesId(), p.correctionReason());
    }
}
