package com.evision.releasemgmt.domain.briefing;

import java.util.List;
import java.util.Optional;

// runs are newest first; readError means the file existed but could not be used.
public record BriefingFeed(BriefingTask task, List<BriefingRun> runs, boolean readError) {

    public Optional<BriefingRun> latest() {
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }
}
