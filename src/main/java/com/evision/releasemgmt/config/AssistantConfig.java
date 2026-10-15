package com.evision.releasemgmt.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Deliberately declares no TaskScheduler/Executor bean: a user-defined one replaces Boot's auto-configured
// scheduler/executor. Scheduling is enabled here so it is active only when the assistant switch is on.
@Configuration
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AssistantProperties.class)
@EnableScheduling
public class AssistantConfig {
}
