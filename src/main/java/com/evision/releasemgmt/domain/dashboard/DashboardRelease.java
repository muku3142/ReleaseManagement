package com.evision.releasemgmt.domain.dashboard;

import com.evision.releasemgmt.domain.release.ReleaseStatus;
import java.time.Instant;
import java.util.UUID;

public record DashboardRelease(UUID id, String name, String version, ReleaseStatus status, Instant createdAt,
                               Instant frozenAt, long manualCount) {
}
