package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.repository.DashboardJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.EnvironmentStageJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.PromotionJpaRepository;
import com.evision.releasemgmt.domain.dashboard.ChurnItem;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.enrichment.HistoryState;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class DashboardReadRepositoryAdapter implements DashboardReadRepository {

    private final DashboardJpaRepository jpa;
    private final EnvironmentStageJpaRepository stageJpa;
    private final PromotionJpaRepository promotionJpa;

    public DashboardReadRepositoryAdapter(DashboardJpaRepository jpa, EnvironmentStageJpaRepository stageJpa,
                                          PromotionJpaRepository promotionJpa) {
        this.jpa = jpa;
        this.stageJpa = stageJpa;
        this.promotionJpa = promotionJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardLedger loadLedger() {
        // A8 in 08-RESEARCH: the ledger is human-recorded and small, so a full load per request is cheaper than
        // restating the stage rules in SQL; restrict it after measuring if it ever grows.
        List<DashboardRelease> releases = jpa.releases().stream()
                .map(r -> new DashboardRelease(r.getId(), r.getName(), r.getVersion(), ReleaseStatus.valueOf(r.getStatus()),
                        r.getCreatedAt(), r.getFrozenAt(), r.getManualCount()))
                .toList();
        return new DashboardLedger(releases,
                stageJpa.findAllByOrderByPositionAsc().stream().map(PromotionRepositoryAdapter::toStage).toList(),
                promotionJpa.findAll().stream().map(PromotionRepositoryAdapter::toPromotion).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChurnItem> findChurnItems(Collection<UUID> releaseIds) {
        if (releaseIds.isEmpty()) {
            return List.of();
        }
        return jpa.churnItems(releaseIds).stream()
                .map(r -> new ChurnItem(r.getReleaseId(), r.getTicketKey(), EnrichmentStatus.valueOf(r.getEnrichmentStatus()),
                        r.getEnrichmentIncomplete(),
                        r.getCommitsSource() == null ? null : CommitsSource.valueOf(r.getCommitsSource()),
                        HistoryState.valueOf(r.getHistoryState()), r.getFirstDoneAt(), r.getReopenCount(),
                        r.getCommitsToDone(), r.getPostDoneCommits(), r.getCommitTotal(),
                        r.getStoryPoints(), r.getStoryPointsRead()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentRow> findDeployments(UUID stageId, Instant from, Instant to, int limit, int offset) {
        return jpa.deployments(stageId, from, to, limit, offset).stream()
                .map(r -> new DeploymentRow(r.getId(), r.getReleaseId(), r.getReleaseName(), r.getReleaseVersion(),
                        r.getStageId(), r.getStageName(), r.getStageRetiredAt() != null,
                        PromotionOutcome.valueOf(r.getOutcome()), r.getDeployedAt(), r.getRecordedBy(), r.getNote(),
                        r.getCorrected()))
                .toList();
    }
}
