package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BriefingSettingsRequest(
        @Min(1) @Max(240) int graceMinutes,
        @NotEmpty List<@NotBlank String> workDays,
        @Min(10) @Max(500) int keepRuns,
        @NotNull @Size(min = 3, max = 3) List<@Valid Task> tasks) {

    public record Task(
            @NotBlank String id,
            boolean enabled,
            @NotNull List<@NotBlank String> days,
            @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String time,
            @Min(1) @Max(30) int staleAfterWorkingDays,
            @Min(1) @Max(60) int dueSoonDays,
            @Min(1) @Max(100) int backlogItems) { }
}
