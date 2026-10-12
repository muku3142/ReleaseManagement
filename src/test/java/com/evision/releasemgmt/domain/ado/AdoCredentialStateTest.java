package com.evision.releasemgmt.domain.ado;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdoCredentialStateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Test
    void expiredWhenExpiryIsInThePast() {
        assertThat(AdoCredentialState.of(TODAY.minusDays(1), TODAY)).isEqualTo(AdoCredentialState.EXPIRED);
    }

    @Test
    void expiringOnTheExpiryDay() {
        assertThat(AdoCredentialState.of(TODAY, TODAY)).isEqualTo(AdoCredentialState.EXPIRING);
    }

    @Test
    void expiringAtTheWarningBoundary() {
        assertThat(AdoCredentialState.of(TODAY.plusDays(14), TODAY)).isEqualTo(AdoCredentialState.EXPIRING);
    }

    @Test
    void okBeyondTheWarningWindow() {
        assertThat(AdoCredentialState.of(TODAY.plusDays(15), TODAY)).isEqualTo(AdoCredentialState.OK);
    }
}
