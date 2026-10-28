package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.repository.PortfolioJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ProductClientCountRow;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseProductPairRow;
import com.evision.releasemgmt.domain.portfolio.ClientCurrentRun;
import com.evision.releasemgmt.domain.portfolio.RecentInstall;
import com.evision.releasemgmt.ports.PortfolioReadRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PortfolioReadRepositoryAdapter implements PortfolioReadRepository {

    private final PortfolioJpaRepository jpa;

    public PortfolioReadRepositoryAdapter(PortfolioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientCurrentRun> currentRuns() {
        return jpa.currentRuns().stream()
                .map(r -> new ClientCurrentRun(r.getClientId(), r.getProductId(), r.getReleaseId(), r.getInstalledAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> runningClientCounts() {
        return jpa.runningClientCounts().stream().collect(Collectors.toUnmodifiableMap(
                ProductClientCountRow::getProductId, r -> r.getClients().intValue()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Set<UUID>> productsByRelease() {
        return jpa.releaseProducts().stream().collect(Collectors.groupingBy(ReleaseProductPairRow::getReleaseId,
                Collectors.mapping(ReleaseProductPairRow::getProductId, Collectors.toUnmodifiableSet())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentInstall> successfulInstallsSince(Instant since) {
        return jpa.successfulInstallsSince(since).stream()
                .map(r -> new RecentInstall(r.getClientId(), r.getProductId()))
                .toList();
    }
}
