package com.evision.releasemgmt.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Unconditional: the briefing fallback must run with the assistant off.
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BriefingsProperties.class)
public class BriefingsConfig {
}
