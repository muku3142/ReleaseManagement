---
phase: 01-foundation-authentication
plan: 04
subsystem: credential-vault
tags: [aes-256-gcm, jpa-attribute-converter, jira-credential, encryption-at-rest, testcontainers]
dependency-graph:
  requires:
    - "Flyway V1__init_schema.sql jira_credential table (plan 01)"
    - "app.credential.key / app.credential.key-version config keys (plan 01)"
  provides:
    - "domain/auth/JiraCredential record"
    - "ports/JiraCredentialRepository (findByAccountId, upsert)"
    - "adapters/persistence/crypto/EncryptedTokenConverter (AES-256-GCM AttributeConverter)"
    - "adapters/persistence/JiraCredentialRepositoryAdapter"
  affects:
    - "Plan 06 (auth endpoints): calls JiraCredentialRepository.upsert on sign-in and token replacement"
    - "Plan 07 (sign-in UI): consumes the same port indirectly via plan 06's service layer"
tech-stack:
  added:
    - "javax.crypto AES/GCM/NoPadding via a JPA AttributeConverter (no new library)"
  patterns:
    - "Self-contained envelope column: keyVersion(1 byte)||IV(12 bytes)||ciphertext+tag, base64-encoded, in a single TEXT column"
    - "upsert-only port surface (no delete, no findAll) as the structural enforcement of AUTH-04 and D-13"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverter.java
    - src/main/java/com/evision/releasemgmt/domain/auth/JiraCredential.java
    - src/main/java/com/evision/releasemgmt/ports/JiraCredentialRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/JiraCredentialEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/JiraCredentialJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/JiraCredentialRepositoryAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverterTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/JiraCredentialPersistenceIT.java
  modified: []
decisions:
  - "Assumption A4 held: Hibernate 7 / Boot 4.1 auto-wires the @Component-annotated EncryptedTokenConverter through Spring's bean container with zero extra configuration — no HibernatePropertiesCustomizer was needed, confirmed by JiraCredentialPersistenceIT's wiring-check test"
  - "IT's row-identity assertion for AUTH-04 counts rows filtered by jira_account_id rather than the whole table, since JiraCredentialPersistenceIT's five tests share one Testcontainers-backed Spring context and a table-wide jpa.count() would be order-dependent across test methods"
metrics:
  duration_minutes: 25
  tasks_completed: 2
  files_created: 8
  completed_date: "2026-07-28"
---

# Phase 01 Plan 04: Encrypted Jira Credential Vault Summary

AES-256-GCM column-level encryption of the Jira API token via a self-contained `keyVersion||IV||ciphertext` envelope in a single JPA `AttributeConverter`, with an upsert-only persistence port that structurally forbids the delete-then-recreate pattern AUTH-04 rules out.

## What Was Built

**Task 1 — `EncryptedTokenConverter`.** `@Component @Converter implements AttributeConverter<String, String>` in `adapters/persistence/crypto`. Constructor takes `app.credential.key` (base64, must decode to exactly 32 bytes for AES-256 or the app refuses to start with `IllegalStateException`) and `app.credential.key-version` (1-127). Every `convertToDatabaseColumn` call draws a fresh 12-byte IV from a single held `SecureRandom` field, encrypts with `AES/GCM/NoPadding` and a 128-bit tag, and assembles `keyVersion(1 byte) || IV(12 bytes) || ciphertext+tag`, base64-encoded as the stored string. `convertToEntityAttribute` reverses this, discarding the version byte (not used for key selection in v1 — one active key at a time, per D-11). A failed GCM tag check (tampering or wrong key) surfaces as `IllegalStateException`, never a silent null. No logging statement anywhere in the class. 8 framework-free JUnit tests cover round-trip, ciphertext-not-plaintext, fresh-IV-per-write, exact envelope byte layout, null-in/null-out, tamper detection, the 32-byte fail-fast check, and cross-key decryption failure.

**Task 2 — Credential entity, port, adapter, proof against real Postgres.** `domain/auth/JiraCredential` is a zero-import record: `(accountId, email, apiToken, keyVersion, updatedAt)`. `ports/JiraCredentialRepository` exposes exactly two methods — `findByAccountId` and `upsert` — with no `delete`, `findAll`, or masked-token accessor, so the port surface itself is the enforcement mechanism for D-13 (never return the token to a client) and AUTH-04 (replacement can never cascade into a delete). `JiraCredentialEntity` maps `jira_credential` 1:1 against plan 01's Flyway DDL (`jira_account_id` PK, `jira_email`, `encrypted_token` via `@Convert(converter = EncryptedTokenConverter.class)`, `key_version` as a plain queryable `short`, `created_at`, `updated_at`) under `ddl-auto: validate`. `JiraCredentialRepositoryAdapter.upsert` loads the existing entity by ID if present and mutates it in place (new `jiraEmail`, `token`, `keyVersion`, `updatedAt`); only when absent does it create a new entity with `createdAt` set once. `deleteById`/`deleteAll` are never called.

`JiraCredentialPersistenceIT` runs against a real `postgres:18` Testcontainers instance and proves: (1) the `@Component` converter bean auto-wires through Hibernate with no extra Spring config (Assumption A4 confirmed, no `HibernatePropertiesCustomizer` needed); (2) the raw `encrypted_token` column read via `JdbcTemplate` is base64 whose first decoded byte is `1` and does not contain or equal the plaintext token; (3) the port round-trips a stored token and its `keyVersion`; (4) replacing a token via a second `upsert` call leaves the row count for that `jira_account_id` at 1, leaves `created_at` unchanged, bumps `updated_at`, returns the new token, and leaves the pre-existing `jira_site_config` row (seeded by plan 01) untouched; (5) two different accounts are stored and read back independently.

## Interfaces for Later Plans

Plan 06 (auth endpoints) calls:
```java
public interface JiraCredentialRepository {
    Optional<JiraCredential> findByAccountId(String accountId);
    void upsert(String accountId, String email, String apiToken);
}
```
`upsert` is the only write path — insert on first sign-in, in-place update on token replacement. `key_version` is stamped from the currently active configured version on every write.

Envelope byte layout on the wire (base64-decoded): byte 0 = key version, bytes 1-12 = GCM IV, remaining bytes = ciphertext + 16-byte GCM tag.

## Verification

- `mvn test -Dtest=EncryptedTokenConverterTest` — 8/8 passing.
- `mvn test -Dtest=JiraCredentialPersistenceIT` — 5/5 passing against a real `postgres:18` Testcontainers instance.
- `mvn test` (whole suite) — 24/24 passing: 8 converter unit tests, 5 credential IT, 3 site-config IT, 5 site-config controller tests, 3 ArchUnit boundary rules. `ddl-auto: validate` accepted the new mapping without any schema mismatch.
- `grep -rn "getToken()" src/main/java/ | grep -v adapters/persistence` — no output; the decrypted token never escapes the persistence package.
- All plan acceptance-criteria greps (envelope constants, `@Convert`, absence of `delete`/`findAll`/`mask` on the port, absence of `deleteById`/`deleteAll` on the adapter, absence of logging in the converter, zero framework imports in domain/port) verified directly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fail-fast key-version error message accidentally matched the wrong-length-key grep pattern**
- **Found during:** Task 1 acceptance-criteria verification
- **Issue:** The initial `IllegalStateException` message for an out-of-range `key-version` read `"app.credential.key-version must be between 1 and 127"`, which contains the substring `app.credential.key` and inflated the `grep -c 'app.credential.key'` count to 3 instead of the plan's expected 2.
- **Fix:** Reworded the message to `"Configured credential key version must be between 1 and 127"` — same information, no accidental substring collision.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverter.java`
- **Commit:** ccfcbbb

**2. [Rule 1 - Bug] Table-wide row count in the AUTH-04 replacement test was order-dependent**
- **Found during:** Task 2 first `JiraCredentialPersistenceIT` run (`replacingTokenPreservesRowIdentityAndOtherRecords` failed: expected 1, was 4)
- **Issue:** `JiraCredentialPersistenceIT`'s five test methods share one `@SpringBootTest` context and one Testcontainers Postgres instance across the whole class (no per-test transaction rollback or truncation is configured, matching plan 01's `JiraSiteConfigPersistenceIT` precedent). The plan's own test description asks for `jpa.count() == 1` after a replacement, which only holds if this is the only test that has ever written a `jira_credential` row — an assumption that breaks under JUnit's default (non-deterministic) method-execution order once the isolation test (`secondAccountIsIsolatedFromTheFirst`) or the round-trip test have already inserted rows.
- **Fix:** Changed the assertion from a table-wide `jpaRepository.count()` to `SELECT COUNT(*) FROM jira_credential WHERE jira_account_id = ?` scoped to the specific account under test, which still proves the AUTH-04 property (replacement never produces a second row or a delete for that account) without depending on execution order across sibling tests. Removed the now-unused `JiraCredentialJpaRepository` autowiring from the test class.
- **Files modified:** `src/test/java/com/evision/releasemgmt/adapters/persistence/JiraCredentialPersistenceIT.java`
- **Commit:** 1dcf321

Neither deviation required an architectural decision (Rule 4) — both were test-assertion/message-wording corrections discovered during verification, not design changes.

## Known Stubs

None. The converter, port, entity, and adapter are fully wired and proven against a real Postgres instance; no placeholder or mock data path exists in the production code delivered by this plan.

## Threat Flags

None beyond what this plan's own `<threat_model>` already covers (T-04-01 through T-04-08) — no new network endpoints, auth paths, or schema surfaces were introduced beyond what the plan specified.

## Self-Check: PASSED

All 8 created files verified present on disk. Both commits (ccfcbbb, 1dcf321) verified in git log.
