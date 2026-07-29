# Architecture

How ReleaseManagement is put together. Written from the code as it actually exists at the
end of Phase 01 (Foundation & Authentication) — every package, class, endpoint and rule
below was read out of the tree, not copied from a plan.

Companion documents: [database.md](database.md) for the schema,
[local-postgres.md](local-postgres.md) for local infrastructure.

---

## What the system is

A web application that lets a release manager assemble a release from Jira tickets,
auto-generate the release paperwork from templates, and track that release as it is
promoted through a fixed environment ladder.

**Core value:** given a set of Jira tickets, produce accurate and consistent release
documentation plus a tracked deployment record — without anyone doing manual release
bookkeeping in Jira.

At the end of Phase 01 the delivered slice is: a single deployable jar serving a React SPA
and a REST API, where a release manager signs in with their own Jira credentials, those
credentials are stored encrypted, and every future Jira read resolves through their
identity.

---

## Runtime shape

One process. One jar. One database.

```
┌─────────────────────────────────────────────────────────────┐
│  release-management-0.1.0-SNAPSHOT.jar   (port 8080)        │
│                                                             │
│   React SPA                    Spring Boot 4.1 / MVC        │
│   BOOT-INF/classes/static/  ←  served by SpaForwarding-     │
│                                Controller (deep links)      │
│                                                             │
│   /api/**        REST API (stateless, cookie JWT)           │
│   /actuator/health                                          │
└──────────────────┬──────────────────────────────────────────┘
                   │ JDBC
          ┌────────▼─────────┐        ┌──────────────────────┐
          │  PostgreSQL 18   │        │  Jira Cloud REST v3  │
          │  (Flyway-managed)│        │  read-only, outbound │
          └──────────────────┘        └──────────────────────┘
```

The SPA and API are **same-origin by construction** — the SPA is packaged inside the jar,
and in development Vite proxies `/api` and `/actuator` to `localhost:8080`. This is load-
bearing: cookie `SameSite=Strict` and CSRF behave identically in dev and production
because there is never a cross-origin hop.

Stack: Java 25, Spring Boot 4.1.0 (Spring MVC, not WebFlux), Spring Data JPA / Hibernate 7,
Flyway, PostgreSQL 18, React + TypeScript 7 on Vite 8. Virtual threads are enabled
(`spring.threads.virtual.enabled: true`), so blocking I/O per request is the intended model
— there is no reactive stack to reason about.

---

## Hexagonal layering

The backend is a ports-and-adapters (hexagonal) architecture, and this is **mechanically
enforced**, not aspirational. `ArchitectureBoundaryTest` runs inside `mvn test` and fails
the build on violation.

```
                        ┌──────────────┐
   inbound adapters     │   domain/    │     outbound adapters
   ────────────────     │   ports/     │     ─────────────────
   adapters/web/    ──► │              │ ◄── adapters/jira/
   (REST controllers)   │  no          │     (Jira Cloud REST)
                        │  framework   │
   adapters/security/   │  imports     │ ◄── adapters/persistence/
   (JWT, cookies)       │              │     (JPA, crypto)
                        └──────▲───────┘
                               │
                        ┌──────┴───────┐
                        │ application/ │  use-case orchestration
                        └──────────────┘
```

### The packages

| Package | Contains | Depends on |
|---------|----------|-----------|
| `domain/` | Pure records and enums — `JiraSiteConfig`, `JiraIdentity`, `JiraCredential`, `JiraAccessContext`, `JiraAuthFailureReason`, `JiraAuthenticationException` | nothing |
| `ports/` | Interfaces the application depends on — `JiraAuthPort`, `JiraCredentialRepository`, `JiraSiteConfigRepository`, `CurrentUserPort` | `domain/` only |
| `application/` | Use-case services — `AuthenticationService`, `JiraAccessContextService`, `JiraSiteConfigService` | `domain/`, `ports/` |
| `adapters/web/` | REST controllers, request/response DTOs, `GlobalExceptionHandler` | everything inward |
| `adapters/jira/` | `JiraRestAuthAdapter` (live), `StubJiraAuthAdapter` (profile-scoped) | `ports/`, `domain/` |
| `adapters/persistence/` | JPA entities, Spring Data repositories, port adapters, `crypto/` | `ports/`, `domain/` |
| `adapters/security/` | JWT issuance/validation, cookie transport, Spring Security glue | `ports/`, `domain/` |
| `config/` | Spring wiring — `SecurityConfig`, `JwtConfig`, `JiraRestClientConfig`, `JiraSiteConfigSeeder`, `SpaForwardingController` | everything |

### The enforced rules

Three ArchUnit rules, all in `src/test/java/.../ArchitectureBoundaryTest.java`:

1. **`domain/` imports no framework.** No Spring, no Jakarta, no Hibernate.
2. **`ports/` imports no framework.** Same constraint — a port is a plain Java interface.
3. **`application/` does not depend on `adapters.jira`, `adapters.persistence` or
   `adapters.web`.**

Rule 3 carries a **deliberate, documented exemption**: `adapters.security` is *not* banned,
because `AuthenticationService` injects `JwtTokenService` directly. There is no port
abstraction over the JWT session mechanism and none is warranted for a single-implementation
technical concern. The exemption is scoped to that one subpackage and the comment in the
test explains why.

The exemption matters to understand precisely: **the ports over Jira and over persistence
are untouched.** The project constraint that ticket-source access must go through a provider
interface from the start is still fully enforced. Only the session/token infrastructure is
reachable directly from `application/`.

### Why this shape

Jira is currently the only ticket source, but `JiraAuthPort` exists from day one because the
provider abstraction is a stated project constraint — adding a second source must not require
restructuring. `StubJiraAuthAdapter` is the immediate payoff: the whole auth flow is
integration-tested end-to-end without touching a real Jira site.

---

## Authentication and session model

The single most intricate part of the system. There is **no local user store** — identity is
Jira identity, and a user's Jira API token is also the system's credential for reading Jira
on their behalf.

### Sign-in flow

```
SPA                      AuthController          AuthenticationService
 │  POST /api/auth/login       │                         │
 │  {email, apiToken}          │                         │
 ├────────────────────────────►│                         │
 │                             ├────────────────────────►│
 │                             │                    ┌────┴─────────────────┐
 │                             │                    │ 1. read site config  │
 │                             │                    │ 2. JiraAuthPort      │
 │                             │                    │    .validate() ──────┼──► Jira /myself
 │                             │                    │ 3. upsert credential │
 │                             │                    │    (encrypted)       │
 │                             │                    │ 4. JwtTokenService   │
 │                             │                    │    issue token pair  │
 │                             │                    └────┬─────────────────┘
 │  200 + Set-Cookie ×2        │◄────────────────────────┤
 │  (token-free body)          │                         │
 │◄────────────────────────────┤                         │
```

The response body **never contains a token**. Both tokens travel only as `Set-Cookie`.

### The token pair

Two HS256 JWTs, both signed with the same secret from `RM_JWT_SECRET` (validated at startup
to decode to ≥32 bytes — HS256 requires a 256-bit key).

| | Access token | Refresh token |
|---|---|---|
| Cookie | `access_token` | `refresh_token` |
| Cookie path | `/` | `/api/auth` |
| TTL | `app.jwt.access-ttl` (PT10M) | `app.jwt.refresh-ttl` (PT30M) |
| Purpose | authorize `/api/**` | rotate the idle window |

Both cookies are `httpOnly`, `SameSite=Strict`, and `secure` — with `cookie-secure=false`
permitted **only** under the `local` profile, enforced by a startup check in
`AuthCookieWriter` that throws otherwise.

Three validators are layered on the decoder in `JwtConfig`:

- **Algorithm pinning** — `macAlgorithm(MacAlgorithm.HS256)`. The decoder will not accept a
  token claiming a different algorithm. This closes the classic algorithm-confusion attack.
- **`TokenTypeValidator`** — separate `accessTokenDecoder` and `refreshTokenDecoder` beans
  each require a matching `typ` claim, so a refresh token cannot be replayed as an access
  token or vice versa.
- **`AbsoluteExpiryValidator`** — enforces an immutable absolute session cap
  (`app.jwt.absolute-ttl`, PT8H). Refresh rotates the idle window but **cannot** extend this
  ceiling; the absolute expiry is carried in the token and re-checked every time.

So there are two independent clocks: a rolling 30-minute idle window and a hard 8-hour
session lifetime. Both must hold.

### Reading the token from a cookie

`CookieBearerTokenResolver` replaces Spring Security's default `Authorization`-header
resolver. This is what makes an httpOnly cookie session work with
`oauth2ResourceServer().jwt()` — the SPA never handles a token in JavaScript, so XSS cannot
exfiltrate one.

### ⚠ CSRF: a framework behaviour you must not undo

`oauth2ResourceServer()` unconditionally registers a CSRF-ignore rule for any request a
bearer token can be resolved from. That default assumes the token arrives in an
`Authorization` header — immune to browser auto-attachment. **Ours arrives in a cookie,
which browsers do attach automatically.** There is no public API to disable that ignore rule.

`SecurityConfig` mitigates it by explicitly restoring the default matcher:

```java
.csrf(csrf -> csrf.spa().requireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER))
```

The residual gap is **not currently exploitable, and the reason is specifically
`SameSite=Strict` on `access_token`** — a genuine cross-site request never gets the cookie
attached in the first place, so there is nothing to forge with.

**This means `SameSite=Strict` on `access_token` is a security control, not a preference.**
If anyone ever relaxes it — to support an OAuth redirect, an embedded context, a third-party
integration — the CSRF exposure becomes real and this must be re-reviewed before the change
ships. Recorded in `01-05-SUMMARY.md` threat flags.

### Authorization matrix

From `SecurityConfig`, in order:

| Matcher | Access |
|---------|--------|
| `POST /api/auth/login`, `/refresh`, `/logout` | permit all |
| `GET /api/settings/jira-site` | permit all — a fresh environment must be readable before anyone can sign in |
| `/actuator/health` | permit all |
| `/api/**` | authenticated |
| anything else | permit all — SPA static assets and deep links |

Stateless throughout: `SessionCreationPolicy.STATELESS`, no server-side session store.

### Per-user credential resolution (AUTH-02)

`JiraAccessContextService.currentContext()` is **the single supported way** any code in this
project obtains Jira credentials for an outbound call:

```java
public JiraAccessContext currentContext() {
    String accountId = currentUserPort.currentAccountId()
            .orElseThrow(() -> new JiraAuthenticationException(INVALID_CREDENTIALS));
    JiraCredential credential = credentialRepository.findByAccountId(accountId)
            .orElseThrow(() -> new JiraAuthenticationException(INVALID_CREDENTIALS));
    String siteBaseUrl = jiraSiteConfigService.get().baseUrl();
    return new JiraAccessContext(siteBaseUrl, accountId, credential.email(), credential.apiToken());
}
```

The design constraint is in what is *absent*: there is no overload taking an `accountId`, and
therefore no code path to any credential other than the caller's own signed-in identity. A
future phase cannot accidentally read Jira as the wrong user, because the API gives it no way
to ask. Every later phase's Jira read goes through this method.

---

## Credential encryption at rest

The Jira API token is encrypted by `EncryptedTokenConverter`, a JPA `AttributeConverter`, so
encryption is transparent to everything above the persistence adapter — no service can
accidentally persist a plaintext token.

**Envelope format** (base64-encoded into a single `TEXT` column):

```
keyVersion (1 byte) || IV (12 bytes) || ciphertext + GCM tag
```

- **AES-256-GCM**, 128-bit tag, 12-byte nonce (the GCM standard length).
- **A fresh random IV per write**, from `SecureRandom`. Never reused — IV reuse under GCM is
  catastrophic, so this is the property to protect in any future change.
- **Key from the environment** (`RM_CREDENTIAL_KEY`), validated at construction to decode to
  exactly 32 bytes. The app refuses to start otherwise rather than failing later at runtime.
- **`keyVersion` is recorded metadata only.** It is written into the envelope and the
  `key_version` column, but is *deliberately not used to select a decryption key* — exactly
  one key is active at a time. It exists so a future manual rotation batch can identify which
  rows still need re-encryption. Read the byte, discard it.

Because the envelope is self-contained, a row can be decrypted with nothing but the key —
no external IV table, no ordering dependency.

---

## Error contract

Every API failure is an RFC 9457 `ProblemDetail` carrying a machine-readable `reason`
extension property. This is the project's permanent error shape, established in Phase 01 and
extended — never replaced — by later phases. `GlobalExceptionHandler` is the single place it
is produced.

A `reason` is what clients branch on; `title` and `detail` are for humans.

| `reason` | Status | Meaning |
|----------|--------|---------|
| `INVALID_CREDENTIALS` | 401 | Jira rejected the email/token |
| `SESSION_EXPIRED` | 401 | Session past its absolute cap |
| `INVALID_SESSION` | 401 | Token malformed, wrong type, or unverifiable |
| `JIRA_FORBIDDEN` | 403 | Authenticated, but not permitted on this site |
| `JIRA_RATE_LIMITED` | 429 | Jira is throttling |
| `JIRA_UNREACHABLE` | 503 | Site could not be reached |
| `JIRA_ERROR` | 502 | Jira returned an unexpected error |
| `SITE_NOT_CONFIGURED` | 409 | No Jira site configured yet |
| `ACCOUNT_MISMATCH` | 409 | Token authenticates as a different Jira account |
| `VALIDATION_FAILED` | 400 | Bean Validation failure — adds a `fieldErrors` map |
| `INTERNAL_ERROR` | 500 | Catch-all |

Two properties worth preserving:

- **The failure taxonomy is distinguishable.** "Jira is down" (503), "your token is bad"
  (401) and "you lack permission" (403) are separate reasons, so the UI can tell the user
  something actionable instead of a generic "login failed".
- **The catch-all never leaks internals.** `handleUnexpected` logs the exception
  server-side and returns a fixed message — it never echoes `ex.getMessage()` to the client.

`ProblemDetailAuthenticationEntryPoint` ensures Spring Security's own 401/403 responses use
the same shape, so authentication failures are not a formatting exception to the contract.

---

## REST API surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/auth/login` | public | Validate Jira credentials, store encrypted, issue session |
| `POST` | `/api/auth/refresh` | public (refresh cookie) | Rotate idle window, absolute cap unchanged |
| `POST` | `/api/auth/logout` | public | Clear both cookies |
| `GET` | `/api/auth/session` | authenticated | Who is signed in |
| `PUT` | `/api/auth/credential` | authenticated | Replace the stored API token (revalidates first) |
| `GET` | `/api/settings/jira-site` | public | Configured Jira site base URL |
| `PUT` | `/api/settings/jira-site` | authenticated | Change it; records the acting user |
| `GET` | `/actuator/health` | public | Liveness/readiness |

`SpaForwardingController` forwards everything that is not `/api`, not `/actuator`, and has no
file extension to `index.html`, so SPA deep links survive a hard refresh. The regex
`^(?!api|actuator)[^.]*` is what keeps it from swallowing API calls or asset requests.

### Outbound: Jira Cloud

Read-only, always. No write scope is requested or used — a hard project constraint.

`JiraRestAuthAdapter` calls `/rest/api/3/myself` with HTTP Basic
(`base64(email:apiToken)`) to validate a credential and learn the caller's `accountId`.
The `RestClient` is built in `JiraRestClientConfig` on `JdkClientHttpRequestFactory` over
`java.net.http.HttpClient`, with explicit connect (PT5S) and read (PT10S) timeouts —
**an outbound call with no timeout is an outage waiting to happen**, so these are set
explicitly rather than left to defaults.

---

## Frontend

React SPA in `frontend/`, built by Vite 8 and embedded into the jar.

| Concern | Choice |
|---------|--------|
| Build | Vite 8 |
| Language | TypeScript 7 |
| Routing | TanStack Router |
| Server state | TanStack Query |
| Tests | Vitest 4 |

Layout:

```
frontend/src/
  main.tsx            entry
  router.tsx          route tree
  index.css
  api/                typed API client + per-resource modules
  components/         AppShell and shared UI
  routes/             one file per route
```

The Maven `frontend` profile **self-activates** once `frontend/package.json` exists, so a
plain `mvn package` runs the SPA build and embeds the output at
`BOOT-INF/classes/static/`. No extra flag, and no way to accidentally ship a jar without
the UI.

Two dependency constraints found the hard way and worth not re-litigating:

- **Vitest must be 4.x, not 3.x.** Vitest 3.2.7's peer range excludes Vite 8, which causes
  npm to silently nest a conflicting Vite 7 and break the type-check in a confusing way.
- **`@types/node` is required** for `tsc -b` to resolve the Vite config under TypeScript 7.

---

## Configuration

All config lives in `application.yml` with env-var overrides. The three secrets have **no
defaults** — the app fails to start without them, which is the correct behaviour for a
credential-handling service.

| Env var | Purpose | Default |
|---------|---------|---------|
| `RM_CREDENTIAL_KEY` | AES-256 key, base64, exactly 32 bytes | **required** |
| `RM_CREDENTIAL_KEY_VERSION` | Envelope key version | `1` |
| `RM_JWT_SECRET` | HS256 secret, base64, ≥32 bytes | **required** |
| `RM_DB_URL` / `RM_DB_USER` / `RM_DB_PASSWORD` | Datastore | local compose values |
| `RM_JIRA_DEFAULT_BASE_URL` | Seeded Jira site | `https://example.atlassian.net` |

`app.jwt.access-ttl` PT10M · `refresh-ttl` PT30M · `absolute-ttl` PT8H ·
`app.jira.connect-timeout` PT5S · `read-timeout` PT10S · `app.security.cookie-secure` true.

Profiles: `local` relaxes `cookie-secure` and turns up JDBC logging. A profile-scoped
`StubJiraAuthAdapter` stands in for live Jira.

> **Gotcha that has bitten twice:** `src/test/resources/application.yml` **shadows**
> `src/main/resources/application.yml` rather than merging with it. Any new property your
> code reads must be added to *both*, or full-context integration tests fail on unresolved
> placeholders.

---

## Persistence

Spring Data JPA over PostgreSQL 18, schema owned by Flyway.

- `spring.jpa.hibernate.ddl-auto: validate` — **Hibernate never modifies the schema.**
  Flyway is the only thing that does. A mapping that drifts from the migration fails at
  startup instead of silently corrupting data.
- `spring.jpa.open-in-view: false` — no lazy loading in the view layer; boundaries stay
  explicit.
- `JiraSiteConfigSeeder` is an `ApplicationRunner` that writes the default site row when the
  table is empty, so a fresh environment is usable before anyone signs in.

Schema detail is in [database.md](database.md).

---

## Testing strategy

**94 tests** across four layers, all in `mvn test` (Surefire is configured to include
`**/*IT.java` — this project has no Failsafe plugin, so integration tests run in the normal
test phase).

| Layer | Mechanism | Covers |
|-------|-----------|--------|
| Architecture | ArchUnit, plain `@Test` | The three boundary rules |
| Unit | JUnit 5 + AssertJ + Mockito | Crypto envelope, JWT service, cookie writer, services |
| HTTP slice | `@WebMvcTest` | Controllers and the `ProblemDetail` contract |
| Integration | Testcontainers (real Postgres 18) | Flyway migrations, JPA mappings, full filter chain, end-to-end auth |
| Contract | WireMock | The Jira adapter against canned responses including error codes |

Plus 4 Vitest tests on the frontend API client.

Two deliberate properties:

- **Integration tests use a real PostgreSQL 18 container**, not H2. Flyway migrations and
  Postgres-specific DDL are verified against the engine that actually runs in production.
  This requires Docker Desktop to be running for `mvn test` to pass.
- **The Jira adapter is tested against WireMock**, including 401/403/429/5xx and timeouts,
  because the failure taxonomy is a feature — each `reason` needs a test proving it is
  produced for the right upstream condition.

WireMock is `org.wiremock:wiremock-standalone`, not `org.wiremock:wiremock`. The plain
artifact bundles no HTTP server factory, and `wiremock-jetty12` collides with Boot's Jetty
version management (`WriteFlusher$Listener` was removed between Jetty 12.0.30 and 12.1.10).
The standalone artifact shades its own Jetty tree and sidesteps the conflict.

---

## Design decisions worth knowing

| Decision | Rationale |
|----------|-----------|
| Spring MVC, not WebFlux | No streaming or high-concurrency fan-out requirement. Virtual threads cover blocking I/O. WebFlux would add Mono/Flux and non-blocking-JDBC complexity for no benefit. |
| No local user store | Identity is Jira identity. A separate user table would be a second source of truth to keep in sync, and the Jira token is needed anyway. |
| JWT in httpOnly cookies, not localStorage | A token in JS-readable storage is exfiltratable by any XSS. The SPA never sees a token. |
| Separate access and refresh tokens with a `typ` claim | Lets the short-lived credential be narrow while the idle window rolls, and makes cross-replay a validation failure rather than a design hole. |
| Absolute cap independent of refresh | Refresh alone would allow an indefinitely-extended session from one sign-in. |
| Encryption in an `AttributeConverter` | Makes plaintext persistence structurally impossible above the persistence adapter, rather than a rule people must remember. |
| `key_version` stored but unused for key selection | Records what a future rotation batch needs without building rotation machinery no one needs yet. |
| Single-row config table (`CHECK (id = 1)`) | The database enforces "exactly one Jira site" rather than application code hoping so. |
| Flyway, `ddl-auto: validate` | One owner of schema truth. Drift fails fast. |
| SPA in the same jar | One artifact, one deploy, same-origin cookies, no CORS. |
| `reason` on every `ProblemDetail` | Clients branch on a stable machine-readable code, not on prose that will be reworded. |

---

## Where Phase 01 stops

Delivered: the walking skeleton, sign-in, session management, encrypted credential storage,
per-user credential resolution, and the Jira site configuration slice.

Not yet built: ticket search and selection, release assembly, the template engine and
authoring UI, document generation and export, the environment ladder and promotion tracking,
and Azure DevOps enrichment.

Two forward-looking constraints already honoured in this phase:

- **`JiraAuthPort` exists although Jira is the only source**, so a second ticket source needs
  a new adapter rather than a restructure.
- **`key_version` is in the schema from V1**, so credential-key rotation is a batch job later
  rather than a migration under pressure.

One open dependency: Phase 02 includes a Jira→Azure DevOps link-discovery spike whose outcome
determines Phase 06's scope (the `dev-status` mechanism vs. commit/PR key-parsing, or both).
Azure DevOps enrichment is sequenced *after* document generation precisely so documents work
Jira-only first — ADO being unlinked, unreachable or unauthorised must degrade gracefully and
must never gate a release.
