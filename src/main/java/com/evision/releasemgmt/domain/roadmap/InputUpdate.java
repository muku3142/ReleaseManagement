package com.evision.releasemgmt.domain.roadmap;

import java.util.List;

public record InputUpdate(RiceInputs inputs, RiceOverrides overrides, List<InputChange> changes) {

    public boolean noChange() {
        return changes.isEmpty();
    }
}
