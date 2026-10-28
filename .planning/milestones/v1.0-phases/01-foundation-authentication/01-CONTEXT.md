# Phase 1: Foundation & Authentication - Context

**Gathered:** 2026-07-27
**Status:** Ready for planning

<domain>
## Phase Boundary

A running Spring Boot + React application with hexagonal package boundaries and a single-jar build, in which the Jira Cloud site is configured and a release manager signs in using their own Jira email and API token.

**Requirements covered:** AUTH-01, AUTH-02, AUTH-03, AUTH-04, JIRA-01

**In scope:** project skeleton, package/architecture boundaries and their enforcement, database baseline and migration tooling, frontend build wired into the jar, Jira site configuration, sign-in and sign-out, session issuance, encrypted credential storage, token replacement screen.

**Out of scope:** ticket browsing and search (Phase 2), releases (Phase 3), templates (Phase 4), documents (Phase 5), Azure DevOps (Phase 6), promotion tracking (Phase 7). No roles or permission checks beyond authentication.

**Roadmap change made during this discussion:** JIRA-01 (configure which Jira Cloud site the system reads from) moved from Phase 2 into Phase 1. AUTH-01 requires validating a token against Jira, which cannot be done without knowing the site — the two are inseparable. ROADMAP.md and the REQUIREMENTS.md traceability table were both updated.
</domain>

<decisions>
## Implementation Decisions

### Who is allowed in

- **D-01:** A valid Jira API token for the configured site is sufficient to authenticate. No allowlist, no Jira group check, no separate user store. Anyone who can log into the Jira site can use the tool.
- **D-02:** **Accepted risk, recorded deliberately.** D-01 scopes the *read* side correctly — every Jira read uses the user's own credential, so nobody sees a ticket they could not see in Jira. It does not scope the *write* side: any authenticated Jira user can create releases and generate approval documents. This is accepted for an internal tool. The cheap upgrade path, if it is ever needed, is a required Jira group membership check at sign-in — access is then administered in Jira rather than in a second list maintained here. Do not build it now.
- **D-03:** Expect several users, all with the same effective capability, with a real chance of distinct roles later. Therefore: **record the acting user on every state change** (releases, promotions, generated documents) so an audit trail exists and roles can be introduced later without a data migration. Do **not** build a role or permission model now, and do not add per-user visibility filtering — every user sees every release.
- **D-04:** When a user's Jira access is revoked while their session is live, the next Jira-backed call fails. No background revalidation, no forced session termination. Already-stored releases and documents stay readable until the session expires. The failure must surface as a clear "your Jira credential is no longer valid" message, not a generic 500.

### Session handling

- **D-05:** Stateless JWT issued after the Jira token is validated. No server-side session store.
- **D-06:** Session lifetime is a rolling idle window with a hard absolute cap of roughly 8 hours.
- **D-07:** **RESOLVED 2026-07-27 — option (a), short-lived access token plus refresh token.** An idle timeout is inherently stateful, so with a stateless JWT it had to be either (a) a short-lived access token plus a refresh token, or (b) a sliding-expiry token re-issued on each authenticated request. The user chose (a): a short-lived access token, a longer-lived refresh token, and a refresh endpoint. The rolling idle window (D-06) is expressed as the refresh token's inactivity window; the ~8 hour hard cap is an absolute limit that refreshing cannot extend. Both tokens follow D-08 (httpOnly, Secure, SameSite cookies). Do not implement sliding expiry.
- **D-08:** **Recommended by Claude, not user-specified:** carry the JWT in an `httpOnly`, `Secure`, `SameSite` cookie rather than `localStorage`. This keeps statelessness while removing the XSS exposure that is the main argument against JWTs in a browser. Because the app is same-origin from a single jar, this needs CSRF protection — use Spring Security's standard mechanism rather than inventing one.
- **D-09:** Consequence of D-05 to accept, not solve: a session cannot be force-revoked before it expires without reintroducing server-side state (a denylist). This is consistent with D-04, which already accepts that revoked Jira access only fails on next use. Do not add a denylist.

### Token storage and keys

- **D-10:** The user's Jira API token is persisted server-side, encrypted, keyed to their Jira account. This is effectively forced by D-05 — with no server session store the credential must live somewhere durable, and putting it in the JWT is not an option.
- **D-11:** The encryption key is supplied to the application at startup from an environment variable or mounted secret. No cloud KMS dependency in Phase 1. Key rotation is a manual, planned exercise; the stored-credential schema should not make re-encryption painful (e.g. record which key version encrypted each row).
- **D-12:** Column-level encryption of the token, not reliance on database or disk encryption at rest. AUTH-03 requires that a database connection alone does not expose plaintext tokens.
- **D-13:** The stored token is never returned to the client after being saved — not in any API response, not masked, not partially. The settings screen offers replacement only, never retrieval.
- **D-14:** All Jira and Azure DevOps access is user-triggered. Nothing reaches an external system while no user is signed in. No scheduled refresh, no background enrichment jobs, in this or any current phase. This keeps the audit trail unambiguous about who caused each external call.

### Jira site configuration

- **D-15:** The tool targets a single Jira Cloud site. Sign-in validates the supplied email and token against that site.
- **D-16:** Because JIRA-01 now lands in this phase, Phase 1 must include both the stored site configuration and a way to see and set it. A sensible default is to seed it from application configuration for deployment while still exposing it in the UI, so a fresh environment is usable before anyone signs in.

### Resolved after research (2026-07-28)

- **D-17:** The build targets **Java 25**, per the stack table — not the JDK 17 that happens to be installed on the dev machine. Installing a JDK 25 distribution is an explicit prerequisite of the first plan; the build must not silently fall back to 17.
- **D-18:** The self-issued JWT is signed with **HS256 using a shared secret supplied from an environment variable**, mirroring D-11's key-from-env pattern. Not RS256 — one process both issues and validates, so an asymmetric keypair buys nothing here. The decoder must pin the expected algorithm explicitly rather than trusting the token's own `alg` header.

### Claude's Discretion

The user explicitly left these to me:

- **Local development without a live Jira site.** Not discussed. Provide a way to develop and test sign-in without real credentials — a stubbed `TicketProvider`/validation adapter behind a Spring profile, with WireMock for integration tests, per the stack research.
- **Landing screen scope.** What the app shows after sign-in when no releases exist yet. Keep it minimal: an application shell with navigation, an empty state, and the settings screen required by AUTH-04. Do not build dashboards or summaries that later phases own.
- **Hexagonal package layout specifics.** Exact package names and the ArchUnit rule set. Follow the structure in `.planning/research/ARCHITECTURE.md`.
- **Error-response shape.** A consistent structured error format for the API, since JIRA-06 in Phase 2 depends on distinguishing failure kinds. Establishing it here is cheaper than retrofitting.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project scope and decisions
- `.planning/PROJECT.md` — constraints, Key Decisions table, resolved and open questions. Note the Constraints section's two hard external deadlines.
- `.planning/REQUIREMENTS.md` — AUTH-01..04 and JIRA-01 wording is authoritative; do not paraphrase it into something weaker.
- `.planning/ROADMAP.md` §"Phase 1: Foundation & Authentication" — goal, mode and success criteria.

### Architecture and stack
- `.planning/research/ARCHITECTURE.md` — hexagonal package structure (`domain/` → `ports/` → `adapters/`), the ArchUnit rule banning framework imports from `domain/`, the anti-corruption-layer boundary for Jira, and the explicit rejection of microservices/CQRS/event sourcing. Read before choosing any package layout.
- `.planning/research/STACK.md` — Spring Boot 4.1 / Java 25, PostgreSQL 18 + Flyway, Spring Data JPA, `RestClient` rather than `RestTemplate`, Resilience4j, Vite/React/TypeScript toolchain, WireMock + Testcontainers. Contains the Jira Cloud authentication specifics (API token as HTTP Basic against `/rest/api/3/`).
- `.planning/research/PITFALLS.md` — Spring Boot + React SPA pitfalls, session handling in an SPA, CORS, and the Jira API traps. Read the Jira section even though ticket search lands in Phase 2, because the credential-validation call is made here.
- `.planning/research/SUMMARY.md` — cross-cutting synthesis; the fastest way to load the two big risks and the resolved decisions.

### Notes
- No external ADRs or specs exist outside `.planning/` — this is a greenfield repository with no prior code.
- `CLAUDE.md` at the repository root holds generated project conventions.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

None. The repository contains no source code — only `.planning/` and `CLAUDE.md`. There are no codebase maps in `.planning/codebase/` because there is nothing to map yet.

### Established Patterns

None from code. Every pattern this phase establishes becomes the precedent for Phases 2-7, which is the main reason the architecture boundaries and the error-response shape matter more here than the feature surface does.

### Integration Points

- **Jira Cloud REST v3** — the only external system this phase touches, and only for credential validation (`/myself` or equivalent) against the configured site.
- **PostgreSQL** — schema baseline via Flyway. The stored-credential table is the first real table and should be designed with key-version awareness per D-11.
- The `TicketProvider` port is *defined* in Phase 2, not here. Phase 1 should not pre-emptively create provider-selection machinery — research explicitly flags that as speculative infrastructure while only one implementation exists.
</code_context>

<specifics>
## Specific Ideas

- The settings screen exists in Phase 1 specifically to satisfy AUTH-04: replacing an expired or revoked Jira token without losing releases, templates or history. Since none of those exist yet, the testable assertion is that replacing the token does not cascade into deleting the user's row or any owned records.
- On token expiry, the user chose to react only on failure — no advance warning banner, no email. So a `401` from Jira must be translated into an actionable prompt to re-enter the token, and must be distinguishable from other failure kinds. This makes the error-response shape (Claude's discretion above) load-bearing rather than cosmetic.
</specifics>

<deferred>
## Deferred Ideas

- **Jira group membership check as the access gate** — the recorded upgrade path for D-02 if write-side access ever needs restricting. Not a new phase; a small change to the sign-in path when wanted.
- **Roles and permissions** — D-03 keeps the door open by recording the acting user, but no role model is built. Belongs in its own phase if it ever becomes real.
- **Advance warning of Jira token expiry** (UI banner and/or email) — considered and explicitly declined for v1 in favour of reacting on failure. Revisit if unannounced mid-release failures become a real annoyance; the expiry date is available at sign-in, so the data needed is already there.
- **Cloud KMS for the encryption key** — declined for Phase 1 to avoid a hard infrastructure dependency. D-11's key-version field is what makes a later migration tolerable.
- **Forced session revocation** — declined as a consequence of stateless JWT (D-09). Would require reintroducing server-side state.
</deferred>

---

*Phase: 1-Foundation & Authentication*
*Context gathered: 2026-07-27*
