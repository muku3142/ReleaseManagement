package com.evision.releasemgmt.domain.ado;

import java.time.LocalDate;

public enum AdoCredentialState {
    OK, EXPIRING, EXPIRED;

    public static final int WARNING_DAYS = 14;

    // Computed on read; no scheduler is needed for a state that only changes with the calendar.
    public static AdoCredentialState of(LocalDate expiresOn, LocalDate today) {
        if (today.isAfter(expiresOn)) {
            return EXPIRED;
        }
        if (!expiresOn.isAfter(today.plusDays(WARNING_DAYS))) {
            return EXPIRING;
        }
        return OK;
    }
}
