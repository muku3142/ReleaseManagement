package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.repository.JiraSiteConfigJpaRepository;
import com.evision.releasemgmt.application.JiraSiteConfigService;
import com.evision.releasemgmt.ports.JiraSiteConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class JiraSiteConfigPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private JiraSiteConfigRepository repository;

    @Autowired
    private JiraSiteConfigService service;

    @Autowired
    private JiraSiteConfigJpaRepository jpaRepository;

    @Value("${app.jira.default-base-url}")
    private String defaultBaseUrl;

    @Test
    void seedsDefaultSiteOnStartup() {
        var config = repository.find().orElseThrow();
        assertThat(config.baseUrl()).isEqualTo(defaultBaseUrl);
        assertThat(config.updatedBy()).isEqualTo("application-config-seed");
    }

    @Test
    void updateBaseUrlPersistsWithActingUser() {
        service.updateBaseUrl("https://acme-test.atlassian.net", "557058:abc-123");

        var config = repository.find().orElseThrow();
        assertThat(config.baseUrl()).isEqualTo("https://acme-test.atlassian.net");
        assertThat(config.updatedBy()).isEqualTo("557058:abc-123");
    }

    @Test
    void secondUpdateDoesNotCreateSecondRow() {
        service.updateBaseUrl("https://acme-test.atlassian.net", "557058:abc-123");
        service.updateBaseUrl("https://acme-test-2.atlassian.net", "557058:def-456");

        assertThat(jpaRepository.count()).isEqualTo(1);
    }
}
