package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.JiraCredentialEntity;
import com.evision.releasemgmt.adapters.persistence.repository.JiraCredentialJpaRepository;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class JiraCredentialRepositoryAdapter implements JiraCredentialRepository {

    private final JiraCredentialJpaRepository jpa;
    private final int activeKeyVersion;

    public JiraCredentialRepositoryAdapter(JiraCredentialJpaRepository jpa,
                                            @Value("${app.credential.key-version}") int activeKeyVersion) {
        this.jpa = jpa;
        this.activeKeyVersion = activeKeyVersion;
    }

    @Override
    public Optional<JiraCredential> findByAccountId(String accountId) {
        return jpa.findById(accountId)
                .map(e -> new JiraCredential(e.getJiraAccountId(), e.getJiraEmail(), e.getToken(), e.getKeyVersion(), e.getUpdatedAt()));
    }

    /**
     * Updates via JPQL rather than findById + save. Loading the entity would decrypt the
     * stored token, so a row encrypted under a superseded key would make this throw — and
     * this is exactly the call that replaces an unreadable token, so a key rotation would
     * lock every user out with no way back in.
     *
     * <p>Clearing sessions_invalidated_at is intentional: a fresh sign-in supersedes any
     * earlier logout, so the new pair is accepted without depending on second-precision
     * ordering between the token's iat and the previous invalidation instant.
     */
    @Override
    @Transactional
    public void upsert(String accountId, String email, String apiToken) {
        Instant now = Instant.now();
        if (jpa.replaceCredential(accountId, email, apiToken, (short) activeKeyVersion, now) > 0) {
            return;
        }
        JiraCredentialEntity created = new JiraCredentialEntity();
        created.setJiraAccountId(accountId);
        created.setJiraEmail(email);
        created.setToken(apiToken);
        created.setKeyVersion((short) activeKeyVersion);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        jpa.save(created);
    }

    @Override
    public Optional<Instant> findSessionsInvalidatedAt(String accountId) {
        return jpa.findSessionsInvalidatedAt(accountId);
    }

    @Override
    @Transactional
    public void markSessionsInvalidated(String accountId, Instant at) {
        jpa.markSessionsInvalidated(accountId, at);
    }
}
