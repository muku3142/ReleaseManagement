package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.AdoCredentialEntity;
import com.evision.releasemgmt.adapters.persistence.repository.AdoCredentialJpaRepository;
import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.domain.ado.AdoCredentialStatus;
import com.evision.releasemgmt.ports.AdoCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class AdoCredentialRepositoryAdapter implements AdoCredentialRepository {

    private static final short SINGLETON_ID = 1;

    private final AdoCredentialJpaRepository jpa;
    private final int activeKeyVersion;

    public AdoCredentialRepositoryAdapter(AdoCredentialJpaRepository jpa,
                                          @Value("${app.credential.key-version}") int activeKeyVersion) {
        this.jpa = jpa;
        this.activeKeyVersion = activeKeyVersion;
    }

    /** JPQL update first: findById + save would decrypt the old token and fail under a rotated key. */
    @Override
    @Transactional
    public void save(String organization, String token, LocalDate expiresOn, String updatedBy) {
        Instant now = Instant.now();
        if (jpa.replace(organization, token, (short) activeKeyVersion, expiresOn, now, updatedBy) > 0) {
            return;
        }
        AdoCredentialEntity created = new AdoCredentialEntity();
        created.setId(SINGLETON_ID);
        created.setOrganization(organization);
        created.setToken(token);
        created.setKeyVersion((short) activeKeyVersion);
        created.setExpiresOn(expiresOn);
        created.setUpdatedAt(now);
        created.setUpdatedBy(updatedBy);
        jpa.save(created);
    }

    @Override
    @Transactional
    public boolean updateExpiry(String organization, LocalDate expiresOn, String updatedBy) {
        return jpa.updateExpiry(organization, expiresOn, Instant.now(), updatedBy) > 0;
    }

    @Override
    public Optional<AdoCredentialStatus> findStatus() {
        return jpa.findStatusRow()
                .map(r -> new AdoCredentialStatus(r.getOrganization(), r.getExpiresOn(), r.getUpdatedAt(), r.getUpdatedBy()));
    }

    @Override
    public Optional<AdoCredential> findForUse() {
        return jpa.findById(SINGLETON_ID)
                .map(e -> new AdoCredential(e.getOrganization(), e.getToken(), e.getExpiresOn()));
    }
}
