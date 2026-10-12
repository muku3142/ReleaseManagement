package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.ports.AdoCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AdoCredentialPersistenceIT {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 12, 1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private AdoCredentialRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ado_credential");
    }

    @Test
    void savedTokenRoundTripsAndColumnIsCiphertext() {
        repository.save("example-org", "ado-plain-token", EXPIRY, "557058:abc");

        assertThat(repository.findForUse()).map(AdoCredential::token).contains("ado-plain-token");
        String raw = jdbcTemplate.queryForObject("select encrypted_token from ado_credential", String.class);
        assertThat(raw).doesNotContain("ado-plain-token");
    }

    @Test
    void secondSaveReplacesTheSingletonRow() {
        repository.save("example-org", "first", EXPIRY, "557058:abc");
        repository.save("other-org", "second", EXPIRY.plusDays(30), "557058:def");

        assertThat(jdbcTemplate.queryForObject("select count(*) from ado_credential", Integer.class)).isEqualTo(1);
        AdoCredential stored = repository.findForUse().orElseThrow();
        assertThat(stored.organization()).isEqualTo("other-org");
        assertThat(stored.token()).isEqualTo("second");
        assertThat(repository.findStatus().orElseThrow().updatedBy()).isEqualTo("557058:def");
    }

    @Test
    void updateExpiryOnlyAppliesToTheStoredOrganization() {
        repository.save("example-org", "tok", EXPIRY, "557058:abc");

        assertThat(repository.updateExpiry("example-org", EXPIRY.plusDays(90), "557058:def")).isTrue();
        assertThat(repository.findStatus().orElseThrow().expiresOn()).isEqualTo(EXPIRY.plusDays(90));
        assertThat(repository.updateExpiry("other-org", EXPIRY.plusDays(5), "557058:def")).isFalse();
        assertThat(repository.findStatus().orElseThrow().expiresOn()).isEqualTo(EXPIRY.plusDays(90));
        assertThat(repository.findForUse()).map(AdoCredential::token).contains("tok");
    }

    @Test
    void statusAndSaveSurviveAKeyRotation() {
        repository.save("example-org", "token-under-old-key", EXPIRY, "557058:abc");
        byte[] undecryptable = new byte[1 + 12 + 32];
        undecryptable[0] = 1;
        jdbcTemplate.update("update ado_credential set encrypted_token = ?",
                Base64.getEncoder().encodeToString(undecryptable));

        assertThat(repository.findStatus()).isPresent();
        assertThat(repository.updateExpiry("example-org", EXPIRY.plusDays(1), "557058:abc")).isTrue();
        assertThatThrownBy(() -> repository.findForUse()).isInstanceOf(RuntimeException.class);

        repository.save("example-org", "token-under-new-key", EXPIRY, "557058:abc");

        assertThat(repository.findForUse()).map(AdoCredential::token).contains("token-under-new-key");
    }
}
