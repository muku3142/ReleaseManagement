package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ClientEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ClientJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ClientJpaRepository.Link;
import com.evision.releasemgmt.adapters.persistence.repository.ProductJpaRepository;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientNotFoundException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.ClientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ClientRepositoryAdapter implements ClientRepository {

    private final ClientJpaRepository jpa;
    private final ProductJpaRepository productJpa;

    public ClientRepositoryAdapter(ClientJpaRepository jpa, ProductJpaRepository productJpa) {
        this.jpa = jpa;
        this.productJpa = productJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Client> findAll() {
        Map<UUID, Set<UUID>> links = jpa.findAllLinks().stream().collect(
                Collectors.groupingBy(Link::getClientId, Collectors.mapping(Link::getProductId, Collectors.toSet())));
        return jpa.findAll().stream()
                .sorted(Comparator.comparing(ClientEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(e -> toClient(e, links.getOrDefault(e.getId(), Set.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findById(UUID clientId) {
        return jpa.findById(clientId)
                .map(e -> toClient(e, new HashSet<>(jpa.findProductIdsOfClient(clientId))));
    }

    @Override
    @Transactional
    public Client add(String name) {
        ClientEntity entity = ClientEntity.create(name);
        saveRejectingDuplicateName(entity);
        return toClient(entity, Set.of());
    }

    @Override
    @Transactional
    public void rename(UUID clientId, String name) {
        ClientEntity entity = load(clientId);
        entity.setName(name);
        saveRejectingDuplicateName(entity);
    }

    @Override
    @Transactional
    public void setArchivedAt(UUID clientId, Instant archivedAt) {
        load(clientId).setArchivedAt(archivedAt);
    }

    @Override
    @Transactional
    public void link(UUID clientId, UUID productId) {
        ClientEntity client = load(clientId);
        var product = productJpa.findById(productId).orElseThrow(ProductNotFoundException::new);
        // D-15-07: no new links to archived entities.
        if (client.getArchivedAt() != null) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.CLIENT_ARCHIVED);
        }
        if (product.getArchivedAt() != null) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED);
        }
        jpa.link(clientId, productId);
    }

    @Override
    @Transactional
    public void unlink(UUID clientId, UUID productId) {
        // The client_deployment_client_has_product FK is the backstop for an install committed after this check.
        if (jpa.existsInstall(clientId, productId)) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.CLIENT_LINK_HAS_INSTALLS);
        }
        jpa.unlink(clientId, productId);
    }

    private ClientEntity load(UUID clientId) {
        return jpa.findById(clientId).orElseThrow(ClientNotFoundException::new);
    }

    private void saveRejectingDuplicateName(ClientEntity entity) {
        try {
            // saveAndFlush forces client_name_unique to fire here rather than at commit, where it would escape this catch.
            jpa.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.CLIENT_NAME_DUPLICATE);
        }
    }

    private static Client toClient(ClientEntity e, Set<UUID> productIds) {
        return new Client(e.getId(), e.getName(), e.getArchivedAt(), productIds);
    }
}
