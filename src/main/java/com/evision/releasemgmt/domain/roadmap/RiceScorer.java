package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

public final class RiceScorer {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RiceScorer() {}

    // The score is never stored; rounding happens only for display (D-17-17).
    public static Optional<BigDecimal> score(RiceInputs inputs) {
        if (!inputs.complete()) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(inputs.reach())
                .multiply(inputs.impact())
                .multiply(BigDecimal.valueOf(inputs.confidence()))
                .divide(HUNDRED, MC)
                .divide(inputs.effort(), MC));
    }
}
