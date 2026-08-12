# Database Structure

The PostgreSQL 18 schema as it exists after `V6` (end of Phase 05.1), read from
`src/main/resources/db/migration/` and verified against the live database.

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
| Enum-like columns | `VARCHAR` + a `CHECK` constraint, never a Postgres `ENUM` type — adding a value to a native enum needs DDL, a `CHECK` needs a one-line migration |
| Schema | `public` |

`TIMESTAMPTZ` everywhere is deliberate: a release audit trail that cannot be compared across
time zones is worthless, and Postgres stores `TIMESTAMPTZ` as UTC with correct comparison
semantics. `TIMESTAMP` would silently lose the offset.

---

## Migration history

| Version | File | What it added |
|---------|------|---------------|
| `V1` | `init_schema` | `jira_site_config`, `jira_credential` |
| `V2` | `session_invalidation` | `jira_credential.sessions_invalidated_at` |
| `V3` | `release_assembly` | `release`, `release_ticket_item`, `release_manual_item` |
| `V4` | `template_authoring` | `template`, `template_logo` |
| `V5` | `document_generation` | `document_snapshot`, `document_snapshot_logo`; `release_ticket_item.component`; `release.items_changed_at`; `template.grouping_dimension` |
| `V6` | `logo_render_height` | `logo_render_height_mm` on both `template` and `document_snapshot` |

---

## Current schema

Nine application tables plus Flyway's own history table, in three independent groups.

```
  ── Foundation (V1, V2) ───────────────────────────────────────────────────

  ┌─────────────────────────────┐   ┌──────────────────────────────────┐
  │ jira_site_config            │   │ jira_credential                  │
  │ id SMALLINT PK CHECK(id=1)  │   │ jira_account_id VARCHAR PK       │
  │ exactly one row, ever       │   │ one row per signed-in account    │
  └─────────────────────────────┘   └──────────────────────────────────┘

  ── Releases (V3, V5) ─────────────────────────────────────────────────────

  ┌───────────────────────┐
  │ release               │
  │ id UUID PK            │
  └───────────┬───────────┘
              │ ON DELETE CASCADE
      ┌───────┴────────┬─────────────────────────┐
      │                │                         │
  ┌───▼──────────────┐ ┌▼───────────────────┐ ┌──▼───────────────────┐
  │release_ticket_   │ │release_manual_item │ │ document_snapshot    │
  │item              │ │                    │ │ (append-only)        │
  │UNIQUE(release_id,│ │                    │ │UNIQUE(release_id,    │
  │       ticket_key)│ │                    │ │  document_type, rev) │
  └──────────────────┘ └────────────────────┘ └──────────┬───────────┘
                                                         │ CASCADE
                                              ┌──────────▼───────────┐
                                              │document_snapshot_logo│
                                              │ snapshot_id PK/FK    │
                                              └──────────────────────┘

  ── Templates (V4, V5, V6) ────────────────────────────────────────────────

  ┌───────────────────────┐
  │ template              │        NO foreign key to document_snapshot.
  │ id UUID PK            │        Templates are freely deletable; the
  │ one active per type   │        snapshot COPIES what it needs.
  └───────────┬───────────┘
              │ ON DELETE CASCADE
  ┌───────────▼───────────┐
  │ template_logo         │
  │ template_id PK/FK     │
  └───────────────────────┘
```

**The deliberate missing foreign key.** `document_snapshot.template_id` is a plain `UUID` with
no `REFERENCES` clause. Templates are freely deletable (`D-04-13`), so an FK would dangle. The
column exists only so the API can answer *"does the source template still exist?"* — never to
join for content. Everything a generated document needs (`template_body`, the resolved chrome
strings, the logo bytes) is **copied onto the snapshot at generation time**. This is what makes
`DOC-07` immutability real rather than aspirational: deleting a template cannot alter, blank, or
break any document already generated from it.

The FK to `release` **does** cascade, which is the opposite choice and equally intentional:
deleting a release deletes its paperwork with it.

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

## `release`

The first entity this system **owns** rather than reads from Jira.

```sql
CREATE TABLE release (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(200)  NOT NULL,
    version            VARCHAR(100)  NOT NULL,
    status             VARCHAR(16)   NOT NULL CHECK (status IN ('OPEN', 'FROZEN')),
    jira_project_key   VARCHAR(64),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(255)  NOT NULL,
    frozen_at          TIMESTAMPTZ,
    frozen_by          VARCHAR(255),
    last_refreshed_at  TIMESTAMPTZ,
    items_changed_at   TIMESTAMPTZ,          -- V5
    CONSTRAINT release_name_version_unique UNIQUE (name, version)
);
```

| Column | Notes |
|--------|-------|
| `status` | `OPEN` or `FROZEN`. A frozen release rejects item mutation at the service layer (`Release.requireOpen()`). |
| `created_by` / `frozen_by` | Jira `accountId`. Resolved to an email **at read time** by Phase 05.3 — nothing stored changes. |
| `last_refreshed_at` | Last time ticket data was re-pulled from Jira (`D-03-07`). |
| `items_changed_at` | Added in `V5`. Stamped on **every** item mutation. |

### Why `items_changed_at` exists separately from `last_refreshed_at`

Document staleness (`D-05-09`) compares a snapshot's `generated_at` against the last time the
release changed. `last_refreshed_at` covers refreshes but not adds, and critically **not
removals** — removing an item leaves no timestamp anywhere, so `max(added_at)` across the items
cannot see it. Without this column, deleting a ticket from a release would leave its generated
documents silently claiming to be up to date.

It is stamped in `ReleaseRepositoryAdapter`, *below* the service layer, so a new entry point
(such as Phase 05.2's add-from-release dialog) cannot bypass it by forgetting to call something.

---

## `release_ticket_item`

A **full materialised copy** of each Jira ticket on the release (`D-03-06`).

```sql
CREATE TABLE release_ticket_item (
    id             UUID PRIMARY KEY,
    release_id     UUID          NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    ticket_key     VARCHAR(64)   NOT NULL,
    summary        VARCHAR(1000) NOT NULL,
    issue_type     VARCHAR(100)  NOT NULL,
    status         VARCHAR(100)  NOT NULL,
    resolution     VARCHAR(100),
    assignee       VARCHAR(255),
    reporter       VARCHAR(255),
    fix_versions   TEXT          NOT NULL,
    description    TEXT          NOT NULL,
    comments       TEXT          NOT NULL,
    comment_total  INTEGER       NOT NULL,
    component      VARCHAR(255),             -- V5
    added_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT release_ticket_item_key_unique UNIQUE (release_id, ticket_key)
);
```

**Why a copy and not a ticket key plus a live lookup.** Opening a release must never issue a
Jira call, and a frozen release must keep the field values it had **at freeze** rather than
whatever Jira says today. A release from six months ago is an audit record; it would be
worthless if its ticket summaries silently re-wrote themselves.

**The uniqueness constraint is per release, not global.** `UNIQUE (release_id, ticket_key)`
deliberately allows one ticket to appear in several releases — a hotfix legitimately ships in
two. Phase 05.2's "hide already-added tickets" filter is therefore scoped to the *target*
release only; a global filter would make that legitimate state unreachable through the UI while
leaving it reachable in the data.

**`component` was added in `V5` with no backfill.** An existing row keeps `NULL`, an `OPEN`
release picks the value up on its next refresh, and a `FROZEN` release is never touched —
backfilling one would mutate an audit record. A migration-time Jira call was also declined:
nothing reaches an external system while no user is signed in (`D-14`).

---

## `release_manual_item`

Release content that has no Jira ticket behind it — a config change, a database script, an
infrastructure step (`REL-08`).

```sql
CREATE TABLE release_manual_item (
    id           UUID         PRIMARY KEY,
    release_id   UUID         NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NOT NULL,
    category     VARCHAR(32)  NOT NULL
                 CHECK (category IN ('CONFIG_CHANGE', 'DATABASE_SCRIPT',
                                     'INFRASTRUCTURE', 'OTHER')),
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX release_manual_item_release_id_idx ON release_manual_item (release_id);
```

No uniqueness constraint: two genuinely distinct manual steps can share a title. This is the one
child table of `release` with an explicit secondary index, because it has no composite unique
constraint whose leading column would already serve the `WHERE release_id = ?` lookup.

---

## `template`

Many named templates per document type, with **exactly one active** (`D-04-12`).

```sql
CREATE TABLE template (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    document_type         VARCHAR(32)  NOT NULL
                          CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    body                  TEXT         NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT false,
    cover_enabled         BOOLEAN      NOT NULL DEFAULT false,
    cover_title           VARCHAR(500) NOT NULL DEFAULT '',
    cover_subtitle        VARCHAR(500) NOT NULL DEFAULT '',
    header_text           VARCHAR(500) NOT NULL DEFAULT '',
    footer_text           VARCHAR(500) NOT NULL DEFAULT '',
    page_numbers_enabled  BOOLEAN      NOT NULL DEFAULT false,
    grouping_dimension    VARCHAR(16)  NOT NULL DEFAULT 'ISSUE_TYPE'      -- V5
                          CHECK (grouping_dimension IN ('ISSUE_TYPE', 'COMPONENT')),
    logo_render_height_mm INTEGER      NOT NULL DEFAULT 30                -- V6
                          CHECK (logo_render_height_mm BETWEEN 5 AND 120),
    warnings              TEXT         NOT NULL DEFAULT '[]',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(255) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            VARCHAR(255) NOT NULL,
    CONSTRAINT template_type_name_unique UNIQUE (document_type, name)
);

CREATE UNIQUE INDEX template_one_active_per_type_idx ON template (document_type) WHERE active;
```

### "Exactly one active" is a partial unique index, not a service check

`template_one_active_per_type_idx` is a **partial** unique index — unique on `document_type`, but
only over rows where `active` is true. A convention that lives only in Java is one concurrent
request away from being false; this one is enforced by the engine.

`TemplateRepositoryAdapter.setActive()` deactivates the current holder before activating the new
one, in the same transaction, so the index is never transiently violated.

Note that `active` is what `DocumentGenerationService` uses to pick a template today, via
`findActive(documentType)`. Phase 05.4 makes that choice explicit at generation time while
keeping `active` as the preselected default, so this index stays.

### Chrome fields sit on the row; the logo does not

The six chrome fields (`TMPL-08`/`D-04-15`) are structured columns rather than a second free-form
template, so no new expression surface is introduced — each one accepts the same `{{ }}`
placeholders as the body and is resolved through the same bounded renderer.

The logo (`D-04-16`) lives in its own table specifically so that **listing templates never reads
image bytes**.

### `grouping_dimension` (V5)

`ISSUE_TYPE` is the default precisely so every template authored in Phase 4 keeps rendering
`{{#groups}}` exactly as it did. The section shape and `{{groupName}}` do not change — only which
field keys the grouping.

### `logo_render_height_mm` (V6)

Millimetres, because the output is a printed page: openhtmltopdf accepts `mm` natively in CSS,
1mm = 36000 EMU exactly for docx4j, and CSS `mm` works in the browser. One stored integer serves
all three renderers with no lossy conversion.

`DEFAULT 30` equals the PDF's previous hard-coded `max-height: 3cm`, so no existing template and
no existing document changed appearance. The one intentional visible change was DOCX, which had
**no clamp at all** and rendered a 512×512 logo 5.3in tall on an 11.7in page.

The bounds `5..120` must stay identical to `ChromeFieldsRequest`'s `@Min`/`@Max`. The upper bound
is derived, not guessed: A4 is 297mm, `@page margin: 2.5cm 2cm` leaves 247mm printable, `.cover`
spends 60mm on `padding-top`, and the cover title and subtitle need about 26mm below the logo.
60 + 120 + 26 = 206mm, 41mm inside the printable area — so no permitted value can push cover
content off page 1.

---

## `template_logo`

```sql
CREATE TABLE template_logo (
    template_id  UUID PRIMARY KEY REFERENCES template (id) ON DELETE CASCADE,
    bytes        BYTEA        NOT NULL,
    content_type VARCHAR(64)  NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
    filename     VARCHAR(255) NOT NULL,
    byte_size    INTEGER      NOT NULL,
    width        INTEGER      NOT NULL,
    height       INTEGER      NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

`template_id` is both PK and FK — one logo per template, enforced structurally rather than by a
uniqueness rule someone could forget. Raster formats only, validated by `LogoValidator` at the
boundary (content type, byte size, and pixel dimensions up to 2000×2000).

---

## `document_snapshot`

A generated document, stored immutably (`DOC-07`).

```sql
CREATE TABLE document_snapshot (
    id                    UUID PRIMARY KEY,
    release_id            UUID         NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    document_type         VARCHAR(32)  NOT NULL
                          CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    revision              INTEGER      NOT NULL CHECK (revision >= 1),
    markdown              TEXT         NOT NULL,
    template_id           UUID,                       -- no REFERENCES, deliberately
    template_name         VARCHAR(200) NOT NULL,
    template_body         TEXT         NOT NULL,
    cover_enabled         BOOLEAN      NOT NULL,
    cover_title           TEXT         NOT NULL,
    cover_subtitle        TEXT         NOT NULL,
    header_text           TEXT         NOT NULL,
    footer_text           TEXT         NOT NULL,
    page_numbers_enabled  BOOLEAN      NOT NULL,
    logo_render_height_mm INTEGER      NOT NULL DEFAULT 30                -- V6
                          CHECK (logo_render_height_mm BETWEEN 5 AND 120),
    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_by          VARCHAR(255) NOT NULL,
    CONSTRAINT document_snapshot_revision_unique UNIQUE (release_id, document_type, revision)
);

CREATE INDEX document_snapshot_release_type_idx
    ON document_snapshot (release_id, document_type, revision DESC);
```

### Append-only, structurally

There is deliberately **no `updated_at`, no `updated_by`, and no soft-delete flag**. Nothing is
ever overwritten or pruned — regenerating creates revision *n+1*, and Phase 7's `AUDIT-02`
surfaces superseded revisions, which requires them to still be there.

### What is copied, and why each one

| Column | Why it is a copy, not a reference |
|--------|-----------------------------------|
| `markdown` | The post-Mustache, **pre-flexmark** body. Storing HTML would force both exporters to re-parse it as Markdown. |
| `template_body` | The raw template body, for provenance — what this document's template looked like at the time. |
| `cover_*`, `header_text`, `footer_text` | The **resolved** chrome text. Re-resolving later would read *today's* release data into *yesterday's* document. |
| `logo_render_height_mm` | So re-exporting revision 1 reproduces the size it was generated with, not today's template setting. |

### The one column that carries a `DEFAULT`

`logo_render_height_mm` is a deliberate exception to this table's "no defaults, generation always
supplies them" convention: rows generated before `V6` also need a value, and it has to be the one
that reproduces the old PDF behaviour.

The `CHECK` is repeated here on purpose. The snapshot is the immutable audit record, and repeating
the constraint makes a forgotten setter in the persistence mapper **fail loudly** — an unset Java
`int` is `0`, which the check rejects — rather than silently writing `0` and rendering an invisible
logo forever.

### The descending index

`(release_id, document_type, revision DESC)` serves the dominant query — "the latest revision of
this document for this release" — as a backward index scan stopping at the first row, and the same
index covers the full revision-history listing.

---

## `document_snapshot_logo`

```sql
CREATE TABLE document_snapshot_logo (
    snapshot_id  UUID PRIMARY KEY REFERENCES document_snapshot (id) ON DELETE CASCADE,
    bytes        BYTEA        NOT NULL,
    content_type VARCHAR(64)  NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
    filename     VARCHAR(255) NOT NULL,
    byte_size    INTEGER      NOT NULL,
    width        INTEGER      NOT NULL,
    height       INTEGER      NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL
);
```

The logo **bytes are copied too**, not pointed at. `template_logo` cascades away with its template,
and a generated document whose cover shows a logo must keep showing it after that template is
deleted or its logo replaced.

The column set mirrors `template_logo` exactly, so `LogoImage`/`LogoMetadata` round-trip without a
second mapping shape. One difference is deliberate: `uploaded_at` has **no default** here — it
carries the original upload instant forward from `template_logo` rather than recording the copy
instant.

---

## `flyway_schema_history`

Flyway's own bookkeeping table, created and managed by Flyway. Do not modify it by hand.

Current state on a fully migrated database:

```
 version |     description      | success
---------+----------------------+---------
 1       | init schema          | t
 2       | session invalidation | t
 3       | release assembly     | t
 4       | template authoring   | t
 5       | document generation  | t
 6       | logo render height   | t
```

---

## Indexes

| Table | Index | Kind | Why it exists |
|-------|-------|------|---------------|
| `jira_site_config` | PK on `id` | primary key | Single row — any access is trivially one row |
| `jira_credential` | PK on `jira_account_id` | primary key | The only lookup is by `accountId`, which is the PK |
| `release` | PK on `id` | primary key | |
| `release` | `release_name_version_unique (name, version)` | unique constraint | Enforces the business rule; also serves lookup by name+version |
| `release_ticket_item` | `release_ticket_item_key_unique (release_id, ticket_key)` | unique constraint | Enforces per-release uniqueness, and its leading column serves every `WHERE release_id = ?` read |
| `release_manual_item` | `release_manual_item_release_id_idx (release_id)` | plain index | The only child table with no composite unique constraint to lean on |
| `template` | `template_type_name_unique (document_type, name)` | unique constraint | Names are unique within a document type |
| `template` | `template_one_active_per_type_idx (document_type) WHERE active` | **partial** unique | Makes "exactly one active per type" a database guarantee |
| `template_logo` | PK on `template_id` | primary key + FK | One logo per template |
| `document_snapshot` | `document_snapshot_revision_unique (release_id, document_type, revision)` | unique constraint | No two rows can claim the same revision number |
| `document_snapshot` | `document_snapshot_release_type_idx (release_id, document_type, revision DESC)` | plain index | "Latest revision" as a backward scan stopping at row one |
| `document_snapshot_logo` | PK on `snapshot_id` | primary key + FK | One logo per snapshot |

Two patterns worth keeping as the schema grows:

- **Most indexes here are a side effect of a constraint**, not a separate optimisation decision.
  A composite unique constraint whose leading column matches the dominant filter gives you the
  lookup index for free.
- **No index without a need.** `jira_email` is never queried by — it is read from a row already
  located by `accountId`. Indexing it would cost writes and buy nothing.

### Not indexed, deliberately

`jira_credential (jira_account_id, jira_email)` is read as a **scalar projection** by Phase 05.3's
`ActorDirectory`, batched across a whole list rather than one query per row. The PK already covers
the `WHERE jira_account_id IN (...)` lookup; the projection exists to avoid hydrating the entity
(and therefore avoid decrypting `encrypted_token`) on a display path, not to avoid an index scan.

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

The local compose service declares **no named volume**, so the data directory is anonymous and
`docker compose down -v` discards it. That is deliberate for development: the schema is disposable
and fully reproducible from the migrations, which makes resetting a broken local database a
two-command operation rather than a debugging session.

Useful inspection queries:

```sql
-- migration state
select version, description, success from flyway_schema_history order by installed_rank;

-- the single site config row
select * from jira_site_config;

-- credentials, without exposing envelopes
select jira_account_id, jira_email, key_version, created_at, updated_at
from jira_credential;

-- rows still on an old key (for a future rotation batch)
select count(*) from jira_credential where key_version <> 1;

-- releases with their item counts
select r.name, r.version, r.status,
       (select count(*) from release_ticket_item t where t.release_id = r.id) as tickets,
       (select count(*) from release_manual_item m where m.release_id = r.id) as manual_items
from release r order by r.created_at desc;

-- which template is active for each document type
select document_type, name, logo_render_height_mm
from template where active;

-- document revision history for a release
select document_type, revision, template_name, generated_at, generated_by
from document_snapshot
where release_id = '<uuid>'
order by document_type, revision desc;

-- snapshots whose source template has since been deleted
select s.id, s.document_type, s.revision, s.template_name
from document_snapshot s
where s.template_id is not null
  and not exists (select 1 from template t where t.id = s.template_id);
```

Two of these deliberately omit large columns. `encrypted_token` has no business in terminal
scrollback or shell history, and `bytes`/`markdown`/`template_body` would flood it.

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

The domain proper is now in place: releases and their contents, templates, and generated
documents. What is still to come, and the shape already decided for it:

| Phase | Adds | Shape |
|-------|------|-------|
| 6 — Azure DevOps Enrichment | Commit and pull-request detail per ticket | Read-only enrichment, reached only by following a link found on a Jira ticket. Raw ADO payload fragments belong in `JSONB` alongside relational columns rather than being shredded into columns that must track every upstream schema change. |
| 7 — Promotion Tracking & Audit Trail | The environment ladder and promotion records | Relational with real foreign keys, and **append-only**, following `document_snapshot`'s precedent — `AUDIT-02` needs superseded state to still exist. |
| 8 — Release Insights Dashboard | No new tables expected | A read/reporting surface over what phases 5–7 already store. If a query proves too slow, the escape hatch is a native query or a JPA projection, not a second persistence stack. |

Two shape decisions taken early that still hold:

- **`JSONB` for raw upstream payloads**, relational tables for the domain this system owns.
- **`VARCHAR` + `CHECK` over native Postgres `ENUM`** for every enum-like column. Adding a value
  to a native enum requires DDL and is awkward to reverse; a `CHECK` is a one-line migration.

A third has been learned since and is worth stating explicitly, because three tables now depend
on it: **when a value must survive the deletion of the thing it came from, copy it.** The
snapshot copies its template body, its resolved chrome, its logo bytes and its logo height for
exactly this reason. Reach for a foreign key when the lifecycles are genuinely coupled
(`release` → its items, which cascade), and for a copy when they are not (`template` →
`document_snapshot`, which does not).
