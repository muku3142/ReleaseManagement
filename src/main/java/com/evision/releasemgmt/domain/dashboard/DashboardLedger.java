package com.evision.releasemgmt.domain.dashboard;

import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.Stage;
import java.util.List;

// Everything the matrix needs from the release and promotion tables, loaded once (no N+1).
public record DashboardLedger(List<DashboardRelease> releases, List<Stage> stages, List<Promotion> promotions) {
}
