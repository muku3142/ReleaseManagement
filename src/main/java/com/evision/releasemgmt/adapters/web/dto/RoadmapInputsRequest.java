package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.roadmap.RiceInputs;

import java.math.BigDecimal;

// Reach is always calculated, so it is changed only through an override.
public record RoadmapInputsRequest(BigDecimal impact, BigDecimal confidence, BigDecimal effort) {

    public RiceInputs toInputs() {
        return RiceInputs.validated(null, impact, confidence, effort);
    }
}
