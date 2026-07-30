# Database Structure

The PostgreSQL 18 schema as it exists at the end of Phase 01, read from
`src/main/resources/db/migration/V1__init_schema.sql` and verified against the live database.

Companion documents: [architecture.md](architecture.md) for how the schema is used,
[local-postgres.md](local-postgres.md) for running the database locally.

---

## Ownership and conventions

**Flyway owns the schema. Nothing else changes it.**

`spring.jpa.hibernate.ddl-auto: validate` means Hibernate verifies that entity mappings match
the live schema and **fails startup on mismatch** — it never issues DDL. So a mapping that
drifts from a migration is caught at boot, not discovered later as corrupt data.

Conventions in force:

| Convention | Rule |
|------------|------|
| Migration naming | `V{n}__{snake_case_description}.sql` in `src/main/resources/db/migration/` |
| Migrations are immutable | Once applied anywhere, never edited — add a new `V{n+1}` instead |
| Table and column names | `snake_case` |
| Timestamps | `TIMESTAMPTZ` (never naive `TIMESTAMP`), defaulting to `now()` |
| Natural keys | Preferred where one genuinely exists (`jira_account_id`) over a surrogate |
| Schema | `public` |

`TIMESTAMPTZ` everywhere is deliberate: a release audit trail that cannot be compared across
time zones is worthless, and Postgres stores `TIMESTAMPTZ` as UTC with correct comparison
semantics. `TIMESTAMP` would silently lose the offset.

---

## Current schema

Three tables: two application tables plus Flyway's own history table.

```
┌─────────────────────────────┐   ┌──────────────────────────────┐
│ jira_site_config            │   │ jira_credential              │
│─────────────────────────────│   │──────────────────────────────│
│ id  SMALLINT PK CHECK(id=1) │   │ jira_account_id VARCHAR PK   │
│ base_url        VARCHAR(255)│   │ jira_email      VARCHAR(255) │
│ updated_at      TIMESTAMPTZ │   │ encrypted_token TEXT         │
│ updated_by      VARCHAR(255)│   │ key_version     SMALLINT     │
└─────────────────────────────┘   │ created_at      TIMESTAMPTZ  │
   exactly one row, ever          │ updated_at      TIMESTAMPTZ  │
                                  └──────────────────────────────┘
                                     one row per signed-in Jira account
```

There are **no foreign keys between them**, and that is correct rather than an omission —
they are independent concerns. Site configuration is a single global setting; credentials are
per-user. Neither depends on the other's lifecycle.

---

## `jira_site_config`

The single Jira Cloud site this installation reads from.

```sql
CREATE TABLE jira_site_config (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    base_url    VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255) NOT NULL
);
```

| Column | Type | Notes |
|--------|------|-------|
| `id` | `SMALLINT` PK | Always `1`. `CHECK (id = 1)` makes more than one row impossible. |
| `base_url` | `VARCHAR(255)` NOT NULL | e.g. `https://acme.atlassian.net`. Validated at the API boundary to the `https://<site>.atlassian.net` shape. |
| `updated_at` | `TIMESTAMPTZ` NOT NULL | Defaults to `now()`. |
| `updated_by` | `VARCHAR(255)` NOT NULL | Jira `accountId` of whoever last changed it. `anonymous` for the seeded row. |

### Why the single-row constraint

`CHECK (id = 1)` combined with `PRIMARY KEY` lets **the database** enforce "exactly one Jira
site", instead of application code hoping there is one. A bug that tries to insert a second
site row fails loudly at the constraint rather than producing two configurations and
non-deterministic behaviour depending on which row a query happens to return first.

### Seeding

`JiraSiteConfigSeeder` is an `ApplicationRunner` that inserts the row from
`app.jira.default-base-url` (`RM_JIRA_DEFAULT_BASE_URL`, default
`https://example.atlassian.net`) **only when the table is empty**. This makes a fresh
environment usable before anyone has signed in, which matters because
`GET /api/settings/jira-site` is deliberately public — the sign-in screen needs to show which
site you are authenticating against.

`updated_by` is `anonymous` on the seeded row because the seeder runs before any
authentication exists. A subsequent `PUT /api/settings/jira-site` records the real
`accountId`.

### Validation is at the boundary, not in the column

`VARCHAR(255)` is only a length bound. The `https://<site>.atlassian.net` shape is enforced by
Bean Validation on `UpdateJiraSiteConfigRequest`, which is an **SSRF control**: without it, a
release manager (or an attacker who reached the endpoint) could point the system's outbound
Jira client at an arbitrary internal host. That check lives at the system boundary where
untrusted input arrives, per the project's validate-at-boundaries principle.

---

## `jira_credential`

One row per Jira account that has signed in, holding that user's encrypted Jira API token.

```sql
CREATE TABLE jira_credential (
    jira_account_id  VARCHAR(255) PRIMARY KEY,
    jira_email       VARCHAR(255) NOT NULL,
    encrypted_token  TEXT NOT NULL,
    key_version      SMALLINT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

| Column | Type | Notes |
|--------|------|-------|
| `jira_account_id` | `VARCHAR(255)` PK | Jira's own immutable `accountId`. The natural key. |
| `jira_email` | `VARCHAR(255)` NOT NULL | Needed for HTTP Basic auth against Jira (`base64(email:token)`). |
| `encrypted_token` | `TEXT` NOT NULL | AES-256-GCM envelope, base64. **Never plaintext.** |
| `key_version` | `SMALLINT` NOT NULL | Which encryption key wrote this row. Metadata only — see below. |
| `created_at` | `TIMESTAMPTZ` NOT NULL | First sign-in. |
| `updated_at` | `TIMESTAMPTZ` NOT NULL | Last token replacement. |
| `sessions_invalidated_at` | `TIMESTAMPTZ` NULL | Added in `V2`. Set on logout; refresh tokens issued before it are rejected. Cleared on a fresh login. |

### Why `accountId` is the primary key, not email

Jira's `accountId` is immutable; a user's email address is not. Keying on email would orphan
the credential the moment someone's address changed. `accountId` is also what
`JiraAccessContextService` has available from the JWT subject, so the lookup is a direct
primary-key hit.

### What is actually in `encrypted_token`

A self-contained AES-256-GCM envelope, base64-encoded:

```
keyVersion (1 byte) || IV (12 bytes) || ciphertext + GCM tag (128-bit)
```

Written and read by `EncryptedTokenConverter`, a JPA `AttributeConverter`, so encryption is
transparent above the persistence adapter — **no service can accidentally persist a plaintext
token**, because there is no code path that writes the column directly.

Properties that matter for any future change:

- **A fresh random IV per write**, from `SecureRandom`. IV reuse under GCM is catastrophic —
  it leaks plaintext relationships and can enable forgery. Never make the IV deterministic or
  derive it from the row key.
- **`TEXT`, not `BYTEA`** — the envelope is base64, so it is a text value. This keeps it
  readable in `psql` output and avoids driver-level bytea escaping concerns. The cost is ~33%
  storage overhead on a short string, which is irrelevant at one row per user.
- **Self-contained** — decryptable with only the key. No external IV table, no ordering
  dependency, no row-to-row coupling.

### `key_version`: recorded, deliberately not used for key selection

`key_version` is written into both the envelope's first byte and this column, but the
converter **reads that byte and discards it**. Exactly one key is active at a time
(`RM_CREDENTIAL_KEY` / `RM_CREDENTIAL_KEY_VERSION`).

It exists so that a future manual rotation batch can answer "which rows still hold tokens
encrypted under the old key?" with an index-friendly `WHERE key_version = 1` instead of
attempting to decrypt every row to find out.

This is a deliberate choice not to build rotation machinery nobody needs yet, while still
paying the one-column cost that makes rotation possible later without a migration under
pressure. Both the column and the envelope byte are validated to be in `1..127` at startup.

### Sign-out invalidates sessions but keeps the credential

`POST /api/auth/logout` stamps `sessions_invalidated_at` and clears cookies. It does **not**
delete the credential row — the stored token is what lets the system read Jira on the user's
behalf, and signing out of the UI is not a statement that the credential should be revoked.
Token replacement stays an explicit action: `PUT /api/auth/credential`.

`sessions_invalidated_at` exists because the session is stateless: without it, clearing
cookies would leave a captured refresh token usable for its full 30-minute TTL and chainable
up to the 8-hour absolute cap, so logout would be a client-side gesture rather than a
session teardown. Refresh compares the token's `iat` against this column and rejects
anything older. A fresh login resets it to `NULL`, which also avoids depending on
second-precision ordering between a new token's `iat` and the previous logout instant.

This is the one piece of server-side session state in an otherwise stateless design. It is
read only on refresh, never on ordinary authenticated requests, so it costs nothing on the
hot path.

---

## `flyway_schema_history`

Flyway's own bookkeeping table, created and managed by Flyway. Do not modify it by hand.

Current state on a migrated database:

```
 version | description | success
---------+-------------+---------
 1       | init schema | t
```

---

## Indexes

Only the primary-key indexes exist, and no others are warranted yet:

| Table | Index | Reason |
|-------|-------|--------|
| `jira_site_config` | PK on `id` | Single row — any access is trivially a full scan of one row |
| `jira_credential` | PK on `jira_account_id` | The only lookup is by `accountId`, which is the PK |

There are no secondary indexes because there are no secondary access patterns.
`jira_email` is never queried by — it is read from a row already located by `accountId`.
Adding an index on it would cost writes and buy nothing.

Per the project's optimisation principle: no index without a measured need. When later phases
add releases, tickets, environments and promotions, index those against their actual query
patterns rather than pre-emptively.

---

## Local access

Full detail in [local-postgres.md](local-postgres.md). The short version:

```bash
docker compose up -d
docker exec -it releasemanagement-postgres-1 psql -U releasemgmt -d releasemgmt
```

Connection defaults, all overridable by env var:

| Setting | Value | Override |
|---------|-------|----------|
| JDBC URL | `jdbc:postgresql://localhost:5432/releasemgmt` | `RM_DB_URL` |
| User | `releasemgmt` | `RM_DB_USER` |
| Password | `releasemgmt` | `RM_DB_PASSWORD` |

The local compose service declares **no named volume**, so the data directory is anonymous
and `docker compose down -v` discards it. That is deliberate for development: the schema is
disposable and fully reproducible from the migrations, which makes resetting a broken local
database a two-command operation rather than a debugging session.

Useful inspection queries:

```sql
-- migration state
select version, description, success from flyway_schema_history;

-- the single site config row
select * from jira_site_config;

-- credentials, without exposing envelopes
select jira_account_id, jira_email, key_version, created_at, updated_at
from jira_credential;

-- rows still on an old key (for a future rotation batch)
select count(*) from jira_credential where key_version <> 1;
```

Note the third query deliberately omits `encrypted_token`. There is no reason to pull
envelopes into terminal scrollback or shell history.

---

## Adding a migration

1. Create `src/main/resources/db/migration/V{next}__{description}.sql`. Never edit an applied
   migration.
2. Update the corresponding JPA entity in `adapters/persistence/entity/`. Because
   `ddl-auto: validate` is on, a mismatch fails at startup — treat that as the schema
   contract working, not as an obstacle.
3. Add or extend a Testcontainers integration test (`*PersistenceIT`). These run against a
   **real PostgreSQL 18 container**, not H2, so migrations and Postgres-specific DDL are
   verified against the engine that runs in production. Docker must be running.
4. Run `export JAVA_HOME="$HOME/.jdks/jdk-25" && mvn test`.

---

## Planned growth

Phase 01 covers only foundation and authentication. Later phases add the domain proper —
releases, selected tickets, generated documents, the environment ladder, and promotion
records with their audit trail.

Two shape decisions already taken that will matter then:

- **JSONB for raw upstream payloads.** Jira and Azure DevOps response fragments (a ticket's
  custom fields, a dev-status snapshot) belong in `JSONB` columns alongside relational tables,
  rather than being shredded into columns that must track every upstream schema change.
- **Relational tables for the domain.** Releases, environments, promotions and their audit
  trail are relational with real foreign keys — this is exactly the workload where referential
  integrity is worth having the database enforce.
