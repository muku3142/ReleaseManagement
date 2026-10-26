package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ProductEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ProductJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ProductJpaRepository.IdCount;
import com.evision.releasemgmt.adapters.persistence.repository.ProductJpaRepository.KeyRow;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductJiraKeyTakenException;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpa;

    public ProductRepositoryAdapter(ProductJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return jpa.findAll().stream()
                .sorted(Comparator.comparing(ProductEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(ProductRepositoryAdapter::toProduct).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID productId) {
        return jpa.findById(productId).map(ProductRepositoryAdapter::toProduct);
    }

    @Override
    @Transactional
    public Product add(String name) {
        ProductEntity entity = ProductEntity.create(name);
        saveRejectingDuplicateName(entity);
        return toProduct(entity);
    }

    @Override
    @Transactional
    public void rename(UUID productId, String name) {
        ProductEntity entity = load(productId);
        entity.setName(name);
        saveRejectingDuplicateName(entity);
    }

    @Override
    @Transactional
    public void setArchivedAt(UUID productId, Instant archivedAt) {
        load(productId).setArchivedAt(archivedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> clientCounts() {
        return toMap(jpa.clientCounts());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> releaseCounts() {
        return toMap(jpa.releaseCounts());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> jiraProjectKeys() {
        return jpa.jiraProjectKeys().stream().collect(Collectors.groupingBy(
                KeyRow::getProductId, Collectors.mapping(KeyRow::getProjectKey, Collectors.toList())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> productIdOwningJiraKey(String key) {
        return jpa.findProductIdsOwningJiraKey(key).stream().findFirst();
    }

    @Override
    @Transactional
    public void setJiraProjectKeys(UUID productId, List<String> keys) {
        try {
            jpa.deleteJiraKeys(productId);
            keys.forEach(key -> jpa.insertJiraKey(productId, key));
        } catch (DataIntegrityViolationException e) {
            throw new ProductJiraKeyTakenException(null, null);
        }
    }

    private ProductEntity load(UUID productId) {
        return jpa.findById(productId).orElseThrow(ProductNotFoundException::new);
    }

    private void saveRejectingDuplicateName(ProductEntity entity) {
        try {
            // saveAndFlush forces product_name_unique to fire here rather than at commit, where it would escape this catch.
            jpa.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_NAME_DUPLICATE);
        }
    }

    private static Map<UUID, Long> toMap(List<IdCount> rows) {
        return rows.stream().collect(Collectors.toMap(IdCount::getProductId, IdCount::getN));
    }

    private static Product toProduct(ProductEntity e) {
        return new Product(e.getId(), e.getName(), e.getArchivedAt());
    }
}
