package com.evision.releasemgmt.config;

import com.evision.releasemgmt.application.JiraSiteConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class JiraSiteConfigSeeder implements ApplicationRunner {

    private final JiraSiteConfigService service;
    private final String defaultBaseUrl;

    public JiraSiteConfigSeeder(JiraSiteConfigService service,
                                 @Value("${app.jira.default-base-url}") String defaultBaseUrl) {
        this.service = service;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.seedIfAbsent(defaultBaseUrl);
    }
}
