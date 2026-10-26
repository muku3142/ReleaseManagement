package com.evision.releasemgmt.domain.briefing;

import java.time.Instant;

public record BriefingRun(Instant runAt, BriefingStatus status, String headline, Integer attention,
                          String h1, String h2, String h3) {
}
