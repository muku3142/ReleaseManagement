package com.evision.releasemgmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("app.briefings")
public record BriefingsProperties(Path dir, Path config, Duration initialDelay, Duration tickInterval,
                                  Duration catchUp, Headline headline) {

    public record Headline(Duration timeout, String instruction) {
    }
}
