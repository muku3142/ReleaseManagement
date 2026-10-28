package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.AppBriefingRun;

/** productName is null only if the run's product no longer exists. */
public record AppRunView(AppBriefingRun run, String productName) {
}
