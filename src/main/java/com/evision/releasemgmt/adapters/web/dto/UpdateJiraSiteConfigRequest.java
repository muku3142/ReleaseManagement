package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateJiraSiteConfigRequest(
        @NotBlank
        @Pattern(regexp = "^https://[A-Za-z0-9][A-Za-z0-9-]*\\.atlassian\\.net$",
                 message = "must be a Jira Cloud site URL of the form https://<site>.atlassian.net with no trailing slash")
        String baseUrl) {
}
