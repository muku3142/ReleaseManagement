package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductBundleRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters") String name,
        @NotEmpty
        @Size(max = 500, message = "at most 500 tickets can be bundled in one request")
        List<@Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$",
                      message = "must be a Jira issue key such as CNV-1234") String> ticketKeys) {
}
