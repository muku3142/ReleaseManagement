package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ClientDeploymentEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ClientDeploymentJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ClientInstallRow;
import com.evision.releasemgmt.adapters.persistence.repository.CurrentRunRow;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseJpaRepository;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Component
public class ClientDeploymentRepositoryAdapter implements ClientDeploymentRepository {

    private final ClientDeploymentJpaRepository jpa;
    private final ReleaseJpaRepository releaseJpa;
    private final ReleaseProductRepository releaseProducts;
    private final ClientRepository clients;

    public ClientDeploymentRepositoryAdapter(ClientDeploymentJpaRepository jpa, ReleaseJpaRepository releaseJpa,
                                             ReleaseProductRepository releaseProducts, ClientRepository clients) {
        this.jpa = jpa;
        this.releaseJpa = releaseJpa;
        this.releaseProducts = releaseProducts;
        this.clients = clients;
    }

    @Override
    @Transactional(readOnly = true)
    public ClientDeploymentHistory load(UUID releaseId) {
        return history(releaseJpa.findById(releaseId).orElseThrow(ReleaseNotFoundException::new));
    }

    @Override
    @Transactional
    public List<ClientDeployment> appendUnderLock(UUID releaseId,
                                                  Function<ClientDeploymentHistory, List<ClientDeployment>> decide) {
        // Same lock as freeze, product replace and promotions: serialises install, correction, freeze and
        // product removal. A domain exception from decide rolls the whole bulk back (D-15-10). The unique
        // index on supersedes_id is a backstop whose violation would indicate a bug - hence
        // DataIntegrityViolationException is not caught.
        ReleaseEntity release = releaseJpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
        List<ClientDeployment> rows = decide.apply(history(release));
        rows.forEach(r -> jpa.saveAndFlush(toEntity(r)));
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientInstall> historyOfClient(UUID clientId) {
        return jpa.historyOfClient(clientId).stream()
                .map(ClientDeploymentRepositoryAdapter::toInstall)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurrentRun> currentRunsOfClient(UUID clientId) {
        return jpa.currentRunsOfClient(clientId).stream()
                .map(ClientDeploymentRepositoryAdapter::toRun)
                .toList();
    }

    private ClientDeploymentHistory history(ReleaseEntity release) {
        List<ClientDeployment> entries = jpa.findByReleaseIdOrderByRecordedAtAsc(release.getId()).stream()
                .map(ClientDeploymentRepositoryAdapter::toDomain)
                .toList();
        return new ClientDeploymentHistory(release.getId(), release.getStatus() == ReleaseStatus.FROZEN,
                releaseProducts.productsOf(release.getId()), clients.findAll(), entries);
    }

    static ClientDeployment toDomain(ClientDeploymentEntity e) {
        return new ClientDeployment(e.getId(), e.getReleaseId(), e.getProductId(), e.getClientId(), e.getOutcome(),
                e.getInstalledAt(), e.getRecordedAt(), e.getRecordedBy(), e.getNote(), e.getSupersedesId(),
                e.getCorrectionReason());
    }

    static ClientDeploymentEntity toEntity(ClientDeployment d) {
        return new ClientDeploymentEntity(d.id(), d.releaseId(), d.productId(), d.clientId(), d.outcome(),
                d.installedAt(), d.recordedAt(), d.recordedBy(), d.note(), d.supersedesId(), d.correctionReason());
    }

    private static ClientInstall toInstall(ClientInstallRow r) {
        ClientDeployment entry = new ClientDeployment(r.getId(), r.getReleaseId(), r.getProductId(), r.getClientId(),
                ClientDeploymentOutcome.valueOf(r.getOutcome()), r.getInstalledAt(), r.getRecordedAt(),
                r.getRecordedBy(), r.getNote(), r.getSupersedesId(), r.getCorrectionReason());
        return new ClientInstall(entry, r.getReleaseName(), r.getReleaseVersion());
    }

    private static CurrentRun toRun(CurrentRunRow r) {
        return new CurrentRun(r.getProductId(), r.getReleaseId(), r.getReleaseName(), r.getReleaseVersion(),
                r.getInstalledAt());
    }
}
