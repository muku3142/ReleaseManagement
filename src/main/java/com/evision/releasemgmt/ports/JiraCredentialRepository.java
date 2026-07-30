package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraCredential;

import java.time.Instant;
import java.util.Optional;

public interface JiraCredentialRepository {
    Optional<JiraCredential> findByAccountId(String accountId);

    void upsert(String accountId, String email, String apiToken);

    /** Empty when this account has never invalidated its sessions. */
    Optional<Instant> findSessionsInvalidatedAt(String accountId);

    /** Refresh tokens issued before {@code at} stop being accepted for this account. */
    void markSessionsInvalidated(String accountId, Instant at);
}
