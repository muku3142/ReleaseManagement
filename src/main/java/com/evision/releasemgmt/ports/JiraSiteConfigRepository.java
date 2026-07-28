package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.jira.JiraSiteConfig;

import java.util.Optional;

public interface JiraSiteConfigRepository {

    Optional<JiraSiteConfig> find();

    void save(JiraSiteConfig config);
}
