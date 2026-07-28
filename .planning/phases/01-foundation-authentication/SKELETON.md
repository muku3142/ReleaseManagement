# Walking Skeleton — ReleaseManagement

**Phase:** 1 (Foundation & Authentication)
**Generated:** 2026-07-28

## Capability Proven End-to-End

A release manager opens the app served from a single jar, sees which Jira Cloud site the system reads from on the Settings page, changes it, and signs in with their own Jira email + API token — validated live against that site — landing on the application shell as an authenticated user.

## Architectural Decisions

These are a **contract**, not a scratchpad. Phases 2-7 build on them without renegotiating.

| Decision | Choice | Rationale |
|---|---|---|
| Build tool | Maven 3.9.11, single module | Maven is installed; no Gradle on this machine (RESEARCH.md Environment Availability) |
| Language/runtime | **Java 25** (`<java.version>25</java.version>`) | D-17. JDK 25 is installed to `C:\Users\ucs\.jdks\jdk-25` as an explicit prerequisite — the build must never fall back to the pre-existing 17.0.16 |
| Framework | Spring Boot 4.1.0, Spring MVC (servlet, blocking) | Project constraint; no reactive requirement exists |
| Maven coordinates | `com.evision:release-management:0.1.0-SNAPSHOT` | eVision org |
| Base package | `com.evision.releasemgmt` | RESEARCH.md structure with `<org>` = `evision` |
| Package layout | `domain/` → `ports/` → `application/` → `adapters/{jira,persistence,security,web}/` → `config/` | ARCHITECTURE.md hexagonal layout, enforced by ArchUnit |
| Boundary enforcement | ArchUnit **core** 1.4.2 with plain `@org.junit.jupiter.api.Test` methods | `archunit-junit5` does not support JUnit 6 (Boot 4.1's managed version) — TNG/ArchUnit#1556, RESEARCH.md Pitfall 1 |
| Data layer | PostgreSQL 18 + Flyway (`flyway-core` + `flyway-database-postgresql`) + Spring Data JPA / Hibernate 7, `ddl-auto: validate` | Flyway owns the schema; `validate` makes entity/schema drift a build failure |
| Local Postgres | `compose.yaml` + `spring-boot-docker-compose` (auto-starts on `mvn spring-boot:run`, skipped in tests) | Docker 29.1.3 available; no local `psql` needed |
| Test Postgres | Testcontainers 2.0.5 `postgresql` module + `@ServiceConnection` | Real Flyway + JPA verification |
| Outbound HTTP | Spring `RestClient` with explicit connect/read timeouts. Never `RestTemplate` | Project constraint; establishes the shape Phase 2's `/search/jql` client copies |
| Jira auth (upstream) | HTTP Basic `base64(email:apiToken)` against `GET {siteBaseUrl}/rest/api/3/myself` | D-15, Jira Cloud API token model |
| Jira site config | One row in `jira_site_config` (`id = 1` CHECK-enforced), seeded at startup from `app.jira.default-base-url`, viewable/editable in the UI. `base_url` pinned by regex to `https://<host>.atlassian.net` | D-15, D-16; the regex is an SSRF control because the server makes outbound calls to this value |
| App auth | Self-issued **HS256** JWT pair, secret from `RM_JWT_SECRET` env var. Decoder pins `MacAlgorithm.HS256` explicitly | D-18. One process issues and validates — asymmetric buys nothing |
| Session shape | Stateless `access_token` (10 min) + `refresh_token` (30 min idle) in httpOnly/Secure/SameSite=Strict cookies; `absolute_exp` claim copied forward unchanged, hard cap 8 h. `POST /api/auth/refresh` | D-05..D-09. **No** server-side session store, **no** token table, **no** denylist, **no** sliding expiry |
| JWT subject | `sub` = Jira `accountId` (stable); `email` carried as a separate claim | Jira email can change; accountId cannot |
| Actor recording | `updated_by` columns hold the Jira `accountId` of the acting user, resolved from `java.security.Principal` | D-03 — audit trail only; **no** role or permission model, **no** per-user visibility filtering |
| Credential storage | `jira_credential` row per Jira `accountId`; token column holds a self-contained base64 `keyVersion(1B) ‖ IV(12B) ‖ AES-256-GCM ciphertext+tag` envelope written by a JPA `AttributeConverter`. `key_version` also stored as a queryable `SMALLINT` | D-10..D-12. `AttributeConverter` cannot read sibling columns, so the envelope must be self-contained |
| Encryption key | `RM_CREDENTIAL_KEY` (base64, exactly 32 bytes) + `RM_CREDENTIAL_KEY_VERSION` env vars, read once at startup. Rotation is a future manual batch job | D-11. No cloud KMS dependency |
| Token exposure | The Jira API token appears in **no** response DTO — not masked, not partial. Settings offers replacement only | D-13, enforced by an ArchUnit rule on `*Response` types |
| CSRF | Spring Security 7.1 `http.csrf(csrf -> csrf.spa())`. SPA echoes the JS-readable `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` | D-08; RESEARCH.md Pitfall 2. `XSRF-TOKEN` is intentionally **not** httpOnly; the JWT cookies always are |
| Error shape | RFC 9457 `ProblemDetail` from a `@RestControllerAdvice`, always carrying a machine-readable `reason` extension property | Claude's discretion per CONTEXT.md; Phase 2's JIRA-06 branches on `reason` |
| Frontend | Vite 8.1.5 + React 19.2.8 + TypeScript 7.0.2 + TanStack Router 1.170.18 + TanStack Query 5.101.4 + Tailwind CSS v4 (`@tailwindcss/vite`) | STACK.md lane. **shadcn/ui is deliberately not installed in Phase 1** — no bespoke component surface exists yet; Tailwind is in place so adding it in Phase 4 is additive with zero migration |
| Frontend packaging | `frontend-maven-plugin` 2.0.1 + `maven-resources-plugin` copy `frontend/dist` → `target/classes/static`, inside a `frontend` Maven profile auto-activated by the existence of `frontend/package.json` | Single jar; the file-activated profile lets the backend build before the frontend exists |
| Local dev loop | Vite dev server on 5173 proxying `/api` + `/actuator` to `localhost:8080` (same-origin, so cookies and CSRF behave as in production) | RESEARCH.md Pitfall 4 |
| Deep links | `SpaForwardingController` forwards non-`/api`, non-`/actuator`, dotless paths to `/index.html` | PITFALLS.md "looks done but isn't" checklist |
| External calls | Every Jira call is user-triggered. No `@Scheduled`, no `@EnableScheduling`, no background revalidation anywhere | D-14, D-04 |
| Local run | `export JAVA_HOME="$HOME/.jdks/jdk-25" && mvn spring-boot:run` (Docker Compose starts Postgres 18 automatically), plus `npm run dev` in `frontend/` for the hot-reload loop | — |

## Stack Touched in Phase 1

- [x] Project scaffold — Maven + Spring Boot 4.1 + Vite/React, lint (`tsc -b` + ESLint), test runners (Surefire/JUnit 6, Vitest)
- [x] Routing — REST: `GET|PUT /api/settings/jira-site`, `POST /api/auth/login|refresh|logout`, `GET /api/auth/session`, `PUT /api/auth/credential`; SPA: `/`, `/login`, `/settings`
- [x] Database — real read (site config + credential lookup) **and** real writes (startup seed, site-config update, credential insert/replace) against Postgres 18 via Flyway `V1__init_schema.sql`
- [x] UI — Settings form saves the Jira site; login form signs in; token-replacement form writes an encrypted credential
- [x] Deployment — `mvn -Pfrontend package` produces one runnable jar serving SPA + API; documented local full-stack run command

## Out of Scope (Deferred to Later Slices)

Explicit, so no later phase re-litigates Phase 1's minimalism:

- `TicketProvider` port and any provider-selection machinery (Phase 2 defines the port; ARCHITECTURE.md Anti-Pattern 5 forbids building it now)
- Ticket search, `/search/jql`, ADF flattening, custom-field metadata resolution (Phase 2)
- Releases, templates, documents, Azure DevOps, promotions (Phases 3-7)
- Roles, permissions, per-user visibility filtering, Jira group membership gate (D-02, D-03 — deferred)
- Forced session revocation / token denylist (D-09 — accepted consequence of stateless JWT)
- Advance warning of Jira token expiry (deferred; Jira Cloud does not expose token expiry via API — RESEARCH.md Pitfall 3)
- Cloud KMS for the encryption key (D-11 — `key_version` makes a later migration tolerable)
- Rate limiting / lockout on the sign-in endpoint (accepted ASVS V2 gap; no requirement calls for it and D-01 sets no access gate)
- Resilience4j retry/circuit-breaker (timeouts only in Phase 1; the full policy lands with Phase 2's real call volume)
- shadcn/ui component library (Phase 4, first bespoke component surface)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering the decisions above:

- Phase 2: browse and filter candidate Jira tickets (adds `ports/TicketProvider`, `adapters/jira/JiraTicketProvider`; reuses the `RestClient`, `ProblemDetail` `reason` shape, and per-user credential lookup established here)
- Phase 3: assemble, freeze and manage releases
- Phase 4: author, validate and preview templates against a fixed view-model
- Phase 5: generate, preview and export documents as immutable snapshots
- Phase 6: enrich tickets with Azure DevOps commits/PRs
- Phase 7: promote releases through the environment ladder with an append-only audit trail
