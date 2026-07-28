package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraSiteConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JiraSiteConfigService {

    private static final String SEED_ACTOR = "application-config-seed";

    private final JiraSiteConfigRepository repository;

    public JiraSiteConfigService(JiraSiteConfigRepository repository) {
        this.repository = repository;
    }

    public JiraSiteConfig get() {
        return repository.find().orElseThrow(JiraSiteNotConfiguredException::new);
    }

    public JiraSiteConfig updateBaseUrl(String baseUrl, String actor) {
        JiraSiteConfig config = new JiraSiteConfig(baseUrl, Instant.now(), actor);
        repository.save(config);
        return config;
    }

    public void seedIfAbsent(String baseUrl) {
        if (repository.find().isEmpty()) {
            repository.save(new JiraSiteConfig(baseUrl, Instant.now(), SEED_ACTOR));
        }
    }

    public static class JiraSiteNotConfiguredException extends RuntimeException {
    }
}
