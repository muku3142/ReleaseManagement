package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// 500 is the hard payload guard; the service's cap of 100 gives the specific ROADMAP_TOO_MANY_ITEMS reason.
public record RoadmapJiraAddRequest(
        @NotEmpty
        @Size(max = 500)
        List<@Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$",
                      message = "must be a Jira issue key such as CNV-1234") String> keys) {
}
