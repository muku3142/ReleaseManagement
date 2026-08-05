package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReleaseRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String version,
        @Size(max = 64) String jiraProjectKey,
        @Size(max = 500, message = "at most 500 tickets can be added in one request")
        List<@Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$",
                      message = "must be a Jira issue key such as CNV-1234") String> ticketKeys) {
}
