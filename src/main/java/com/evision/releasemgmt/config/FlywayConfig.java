package com.evision.releasemgmt.config;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    // Versions are pre-assigned per phase (V15..V19) but phases execute out of numeric order.
    @Bean
    FlywayConfigurationCustomizer flywayOutOfOrder() {
        return configuration -> configuration.outOfOrder(true);
    }
}
