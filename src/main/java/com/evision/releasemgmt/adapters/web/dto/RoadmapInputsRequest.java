package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.roadmap.RiceInputs;

import java.math.BigDecimal;

public record RoadmapInputsRequest(BigDecimal reach, BigDecimal impact, BigDecimal confidence, BigDecimal effort) {

    public RiceInputs toInputs() {
        return RiceInputs.validated(reach, impact, confidence, effort);
    }
}
