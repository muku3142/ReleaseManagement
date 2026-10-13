package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.persistence.crypto.EncryptedTokenConverter;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.ports.ActorDirectory;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class JiraCredentialPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private JiraCredentialRepository repository;

    @Autowired
    private ActorDirectory directory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EncryptedTokenConverter converter;

    @Test
    void encryptedTokenConverterBeanIsWiredAndRoundTrips() {
        assertThat(converter).isNotNull();
        String stored = converter.convertToDatabaseColumn("ATATT3xFfGF0-wiring-check");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("ATATT3xFfGF0-wiring-check");
    }

    /**
     * A row encrypted under a superseded key must not block the sign-in that replaces it.
     * upsert previously did findById + save, which decrypted the stored token first, so
     * rotating RM_CREDENTIAL_KEY threw on login and locked every existing user out with no
     * recovery path — re-entering the token went through the same failing read.
     *
     * <p>Simulating rotation by writing ciphertext this key cannot decrypt is equivalent to
     * the key having changed under an existing row, and avoids rebuilding the context with a
     * different key.
     */
    @Test
    void upsertOverwritesARowEncryptedUnderASupersededKey() {
        repository.upsert("557058:rotated", "rm@acme.com", "token-under-old-key");
        // key version 1, a valid-looking IV, then bytes that will fail the GCM tag check
        byte[] undecryptable = new byte[1 + 12 + 32];
        undecryptable[0] = 1;
        jdbcTemplate.update(
                "UPDATE jira_credential SET encrypted_token = ? WHERE jira_account_id = ?",
                Base64.getEncoder().encodeToString(undecryptable), "557058:rotated");

        repository.upsert("557058:rotated", "rm@acme.com", "token-under-new-key");

        assertThat(repository.findByAccountId("557058:rotated"))
                .map(JiraCredential::apiToken)
                .contains("token-under-new-key");
    }

    @Test
    void sessionInvalidationDoesNotRequireDecryptingTheStoredToken() {
        repository.upsert("557058:invalidate", "rm@acme.com", "some-token");
        byte[] undecryptable = new byte[1 + 12 + 32];
        undecryptable[0] = 1;
        jdbcTemplate.update(
                "UPDATE jira_credential SET encrypted_token = ? WHERE jira_account_id = ?",
                Base64.getEncoder().encodeToString(undecryptable), "557058:invalidate");

        Instant at = Instant.now();
        repository.markSessionsInvalidated("557058:invalidate", at);

        assertThat(repository.findSessionsInvalidatedAt("557058:invalidate")).isPresent();
    }

    @Test
    void storedColumnIsCiphertextNotPlaintext() {
        repository.upsert("557058:abc-123", "rm@acme.com", "ATATT3xFfGF0-plain");

        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT encrypted_token FROM jira_credential WHERE jira_account_id = ?",
                String.class, "557058:abc-123");

        assertThat(rawColumn).doesNotContain("ATATT3xFfGF0-plain").isNotEqualTo("ATATT3xFfGF0-plain");
        byte[] decoded = Base64.getDecoder().decode(rawColumn);
        assertThat(decoded[0]).isEqualTo((byte) 1);
    }

    @Test
    void roundTripsThroughThePort() {
        repository.upsert("557058:round-trip", "rm2@acme.com", "ATATT3xFfGF0-plain");

        JiraCredential credential = repository.findByAccountId("557058:round-trip").orElseThrow();
        assertThat(credential.apiToken()).isEqualTo("ATATT3xFfGF0-plain");
        assertThat(credential.keyVersion()).isEqualTo(1);
    }

    @Test
    void replacingTokenPreservesRowIdentityAndOtherRecords() {
        String accountId = "557058:replace-me";
        repository.upsert(accountId, "rm3@acme.com", "ATATT3xFfGF0-original");

        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM jira_credential WHERE jira_account_id = ?",
                Instant.class, accountId);
        Instant updatedAtBefore = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM jira_credential WHERE jira_account_id = ?",
                Instant.class, accountId);

        repository.upsert(accountId, "rm3@acme.com", "ATATT3xFfGF0-replaced");

        Integer rowsForThisAccount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jira_credential WHERE jira_account_id = ?", Integer.class, accountId);
        assertThat(rowsForThisAccount).isEqualTo(1);
        Instant createdAtAfter = jdbcTemplate.queryForObject(
                "SELECT created_at FROM jira_credential WHERE jira_account_id = ?",
                Instant.class, accountId);
        Instant updatedAtAfter = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM jira_credential WHERE jira_account_id = ?",
                Instant.class, accountId);

        assertThat(createdAtAfter).isEqualTo(createdAt);
        assertThat(updatedAtAfter).isAfter(updatedAtBefore);
        assertThat(repository.findByAccountId(accountId).map(JiraCredential::apiToken))
                .contains("ATATT3xFfGF0-replaced");

        Integer siteConfigRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jira_site_config", Integer.class);
        assertThat(siteConfigRowCount).isEqualTo(1);
    }

    @Test
    void secondAccountIsIsolatedFromTheFirst() {
        repository.upsert("557058:first", "first@acme.com", "ATATT3xFfGF0-first");
        repository.upsert("557058:second", "second@acme.com", "ATATT3xFfGF0-second");

        Optional<JiraCredential> first = repository.findByAccountId("557058:first");
        assertThat(first).isPresent();
        assertThat(first.get().apiToken()).isEqualTo("ATATT3xFfGF0-first");
        assertThat(repository.findByAccountId("557058:second").orElseThrow().apiToken())
                .isEqualTo("ATATT3xFfGF0-second");
    }

    /**
     * The whole point of the ActorDirectory port. Actor display reads this table on the document,
     * release and template screens; if it hydrated JiraCredentialEntity, one row encrypted under a
     * superseded RM_CREDENTIAL_KEY would break three screens that have nothing to do with
     * credentials. A cosmetic improvement would become an outage.
     */
    @Test
    void actorDirectoryResolvesEmailWithoutDecryptingTheStoredToken() {
        repository.upsert("557058:actor-rotated", "actor@acme.com", "token-under-old-key");
        byte[] undecryptable = new byte[1 + 12 + 32];
        undecryptable[0] = 1;
        jdbcTemplate.update(
                "UPDATE jira_credential SET encrypted_token = ? WHERE jira_account_id = ?",
                Base64.getEncoder().encodeToString(undecryptable), "557058:actor-rotated");

        // Deliberately broad: the assertion that matters is that SOMETHING is thrown, proving the row
        // really is unreadable. Hibernate may or may not wrap the converter's IllegalStateException, and
        // pinning the wrapper type would make this test fail on a Hibernate upgrade for no good reason.
        assertThatThrownBy(() -> repository.findByAccountId("557058:actor-rotated"))
                .isInstanceOf(RuntimeException.class);

        assertThat(directory.findEmailsByAccountId(List.of("557058:actor-rotated")))
                .containsEntry("557058:actor-rotated", "actor@acme.com");
    }

    @Test
    void actorDirectoryOmitsAnAccountIdWithNoCredentialRow() {
        assertThat(directory.findEmailsByAccountId(List.of("557058:never-existed")))
                .doesNotContainKey("557058:never-existed");
    }

    @Test
    void actorDirectoryResolvesSeveralAccountIdsInOneCall() {
        repository.upsert("557058:actor-one", "one@acme.com", "token-one");
        repository.upsert("557058:actor-two", "two@acme.com", "token-two");

        Map<String, String> emails = directory.findEmailsByAccountId(
                List.of("557058:actor-one", "557058:actor-two"));

        assertThat(emails)
                .containsEntry("557058:actor-one", "one@acme.com")
                .containsEntry("557058:actor-two", "two@acme.com");
    }
}
