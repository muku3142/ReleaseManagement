package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;

public record RankedItem(RoadmapItem item, BigDecimal score, Integer rank) {}
