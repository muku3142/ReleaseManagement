package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.domain.ado.AdoCredentialStatus;

import java.time.LocalDate;
import java.util.Optional;

public interface AdoCredentialRepository {

    /** Insert or replace the singleton row without loading (and so without decrypting) the existing one. */
    void save(String organization, String token, LocalDate expiresOn, String updatedBy);

    /** Expiry-only change for the stored organization; false when no row exists for that organization. Never decrypts. */
    boolean updateExpiry(String organization, LocalDate expiresOn, String updatedBy);

    /** Projection only: never decrypts, so Settings loads even under a rotated key. */
    Optional<AdoCredentialStatus> findStatus();

    /** Decrypts. Throws IllegalStateException when the stored token cannot be decrypted under the current key. */
    Optional<AdoCredential> findForUse();
}
