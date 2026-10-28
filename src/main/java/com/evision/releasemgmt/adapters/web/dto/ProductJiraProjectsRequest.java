package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductJiraProjectsRequest(@NotNull @Size(max = 10) List<@NotNull @Size(max = 20) String> keys) {
}
