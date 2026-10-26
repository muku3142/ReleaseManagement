package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RoadmapReleaseAddRequest(
        @NotNull UUID releaseId,
        @NotEmpty
        @Size(max = 500)
        List<@Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$",
                      message = "must be a Jira issue key such as CNV-1234") String> ticketKeys) {
}
