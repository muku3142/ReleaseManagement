package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductJiraKeyTakenException;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProductService {

    // Keys reach JQL later, so the shape is enforced here and by a CHECK constraint.
    public static final Pattern JIRA_PROJECT_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{1,9}$");

    private final ProductRepository products;
    private final Clock clock;

    public ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    public List<Product> list() {
        return products.findAll();
    }

    public Map<UUID, Long> clientCounts() {
        return products.clientCounts();
    }

    public Map<UUID, Long> releaseCounts() {
        return products.releaseCounts();
    }

    public void add(String name) {
        products.add(name.strip());
    }

    public void rename(UUID id, String name) {
        find(id);
        products.rename(id, name.strip());
    }

    public void archive(UUID id) {
        if (!find(id).archived()) {
            products.setArchivedAt(id, clock.instant());
        }
    }

    public void unarchive(UUID id) {
        if (find(id).archived()) {
            products.setArchivedAt(id, null);
        }
    }

    public Map<UUID, List<String>> jiraProjectKeys() {
        return products.jiraProjectKeys();
    }

    public void setJiraProjectKeys(UUID id, List<String> keys) {
        if (find(id).archived()) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED);
        }
        List<String> normalized = keys.stream().map(k -> k.strip().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        if (!normalized.stream().allMatch(k -> JIRA_PROJECT_KEY.matcher(k).matches())) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_JIRA_KEY_INVALID);
        }
        for (String key : normalized) {
            Optional<UUID> owner = products.productIdOwningJiraKey(key);
            if (owner.isPresent() && !owner.get().equals(id)) {
                throw new ProductJiraKeyTakenException(key, find(owner.get()).name());
            }
        }
        products.setJiraProjectKeys(id, normalized);
    }

    private Product find(UUID id) {
        return products.findById(id).orElseThrow(ProductNotFoundException::new);
    }
}
