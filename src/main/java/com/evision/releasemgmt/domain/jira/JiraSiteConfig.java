package com.evision.releasemgmt.domain.jira;

import java.time.Instant;

public record JiraSiteConfig(String baseUrl, Instant updatedAt, String updatedBy) {
}
