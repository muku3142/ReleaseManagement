package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.ado.AdoCredentialState;
import com.evision.releasemgmt.domain.ado.AdoCredentialStatus;
import com.evision.releasemgmt.domain.ado.AdoCredentialTokenRequiredException;
import com.evision.releasemgmt.ports.AdoCredentialRepository;
import com.evision.releasemgmt.ports.AdoRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class AdoCredentialService {

    public record View(boolean configured, String organization, LocalDate expiresOn, AdoCredentialState state,
                       Instant updatedAt, String updatedBy) {
    }

    private final AdoCredentialRepository repository;
    private final AdoRepositoryPort adoRepositoryPort;

    public AdoCredentialService(AdoCredentialRepository repository, AdoRepositoryPort adoRepositoryPort) {
        this.repository = repository;
        this.adoRepositoryPort = adoRepositoryPort;
    }

    public View status() {
        return repository.findStatus().map(this::toView).orElse(new View(false, null, null, null, null, null));
    }

    // Any signed-in user may set the shared credential (there is no role model); updated_by is recorded.
    public View save(String organization, String tokenOrNull, LocalDate expiresOn, String actor) {
        if (tokenOrNull == null || tokenOrNull.isBlank()) {
            if (!repository.updateExpiry(organization, expiresOn, actor)) {
                throw new AdoCredentialTokenRequiredException();
            }
        } else {
            adoRepositoryPort.validate(organization, tokenOrNull);
            repository.save(organization, tokenOrNull, expiresOn, actor);
        }
        return status();
    }

    private View toView(AdoCredentialStatus s) {
        return new View(true, s.organization(), s.expiresOn(),
                AdoCredentialState.of(s.expiresOn(), LocalDate.now(ZoneOffset.UTC)), s.updatedAt(), s.updatedBy());
    }
}
