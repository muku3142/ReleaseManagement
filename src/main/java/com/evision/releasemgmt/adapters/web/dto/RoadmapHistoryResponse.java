package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.roadmap.InputChangeKind;
import com.evision.releasemgmt.domain.roadmap.RiceField;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RoadmapHistoryResponse(List<Entry> entries) {

    public record Entry(RiceField field, BigDecimal oldValue, BigDecimal newValue, Instant changedAt,
                        String changedBy, InputChangeKind kind, String reason) {
    }
}
