package com.evision.ragservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// No Executor/TaskScheduler beans here: they would make Boot's auto-configured scheduler back off and change how IndexWorker.tick() runs
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class GenerationConfig {
}
