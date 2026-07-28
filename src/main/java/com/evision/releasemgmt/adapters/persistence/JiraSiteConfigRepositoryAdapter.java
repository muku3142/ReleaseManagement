package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.JiraSiteConfigEntity;
import com.evision.releasemgmt.adapters.persistence.repository.JiraSiteConfigJpaRepository;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraSiteConfigRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JiraSiteConfigRepositoryAdapter implements JiraSiteConfigRepository {

    private static final short SINGLETON_ID = 1;

    private final JiraSiteConfigJpaRepository jpa;

    public JiraSiteConfigRepositoryAdapter(JiraSiteConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<JiraSiteConfig> find() {
        return jpa.findById(SINGLETON_ID)
                .map(e -> new JiraSiteConfig(e.getBaseUrl(), e.getUpdatedAt(), e.getUpdatedBy()));
    }

    @Override
    public void save(JiraSiteConfig config) {
        JiraSiteConfigEntity entity = jpa.findById(SINGLETON_ID).orElseGet(JiraSiteConfigEntity::new);
        entity.setId(SINGLETON_ID);
        entity.setBaseUrl(config.baseUrl());
        entity.setUpdatedAt(config.updatedAt());
        entity.setUpdatedBy(config.updatedBy());
        jpa.save(entity);
    }
}
