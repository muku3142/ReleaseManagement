package com.evision.releasemgmt.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayConfigTest {

    @Test
    void flywayRunsOutOfOrder() {
        FluentConfiguration configuration = new FluentConfiguration();

        new FlywayConfig().flywayOutOfOrder().customize(configuration);

        assertThat(configuration.isOutOfOrder()).isTrue();
    }
}
