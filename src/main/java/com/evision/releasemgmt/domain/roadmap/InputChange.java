package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InputChange(
        UUID itemId, RiceField field, BigDecimal oldValue, BigDecimal newValue, Instant changedAt, String changedBy) {}
