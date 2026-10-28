package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ProductBundleEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ProductBundleJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ProductBundleJpaRepository.TicketRow;
import com.evision.releasemgmt.domain.portfolio.ProductBundle;
import com.evision.releasemgmt.ports.ProductBundleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductBundleRepositoryAdapter implements ProductBundleRepository {

    private final ProductBundleJpaRepository jpa;

    public ProductBundleRepositoryAdapter(ProductBundleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductBundle> findByProduct(UUID productId) {
        Map<UUID, List<String>> keysByBundle = jpa.ticketsOfProduct(productId).stream().collect(
                Collectors.groupingBy(TicketRow::getBundleId, Collectors.mapping(TicketRow::getTicketKey, Collectors.toList())));
        return jpa.findByProduct(productId).stream()
                .map(b -> new ProductBundle(b.getId(), b.getName(), keysByBundle.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void save(UUID productId, String name, List<String> ticketKeys) {
        ProductBundleEntity bundle = jpa.findByProductAndName(productId, name)
                .orElseGet(() -> jpa.saveAndFlush(ProductBundleEntity.create(productId, name)));
        jpa.removeTickets(productId, ticketKeys);
        ticketKeys.forEach(key -> jpa.insertTicket(bundle.getId(), productId, key));
        jpa.deleteEmpty(productId);
    }

    @Override
    @Transactional
    public void delete(UUID productId, UUID bundleId) {
        jpa.deleteOne(productId, bundleId);
    }
}
