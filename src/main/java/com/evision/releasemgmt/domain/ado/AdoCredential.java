package com.evision.releasemgmt.domain.ado;

import java.time.LocalDate;

/** Token-bearing, for use at call time only. */
public record AdoCredential(String organization, String token, LocalDate expiresOn) {

    @Override
    public String toString() {
        return "AdoCredential[organization=" + organization + ", expiresOn=" + expiresOn + "]";
    }
}
