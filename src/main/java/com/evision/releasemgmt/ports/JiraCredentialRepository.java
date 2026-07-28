package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraCredential;

import java.util.Optional;

public interface JiraCredentialRepository {
    Optional<JiraCredential> findByAccountId(String accountId);

    void upsert(String accountId, String email, String apiToken);
}
