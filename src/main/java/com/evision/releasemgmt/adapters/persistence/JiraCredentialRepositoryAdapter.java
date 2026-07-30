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

    @Override
    @Transactional
    public void upsert(String accountId, String email, String apiToken) {
        Instant now = Instant.now();
        JiraCredentialEntity entity = jpa.findById(accountId).orElseGet(() -> {
            JiraCredentialEntity created = new JiraCredentialEntity();
            created.setJiraAccountId(accountId);
            created.setCreatedAt(now);
            return created;
        });
        entity.setJiraEmail(email);
        entity.setToken(apiToken);
        entity.setKeyVersion((short) activeKeyVersion);
        entity.setUpdatedAt(now);
        // a fresh sign-in supersedes any earlier invalidation, so the new pair is accepted
        // without depending on second-precision iat vs. invalidated-at ordering
        entity.setSessionsInvalidatedAt(null);
        jpa.save(entity);
    }

    @Override
    public Optional<Instant> findSessionsInvalidatedAt(String accountId) {
        return jpa.findById(accountId).map(JiraCredentialEntity::getSessionsInvalidatedAt);
    }

    @Override
    @Transactional
    public void markSessionsInvalidated(String accountId, Instant at) {
        jpa.findById(accountId).ifPresent(entity -> {
            entity.setSessionsInvalidatedAt(at);
            jpa.save(entity);
        });
    }
}
