package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.BriefingFeed;

import java.util.List;

// appLatest: what the card shows per active product, by product name; appRuns: all app runs, newest first.
public record BriefingOverview(BriefingFeed claude, List<AppRunView> appLatest, List<AppRunView> appRuns) {
}
