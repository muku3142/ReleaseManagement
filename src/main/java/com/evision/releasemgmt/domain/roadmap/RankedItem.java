package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;

public record RankedItem(RoadmapItem item, RiceInputs calculated, RiceInputs effective, BigDecimal score,
                         Integer rank) {}
