# Phase 1: Foundation & Authentication - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-27
**Phase:** 1-Foundation & Authentication
**Areas discussed:** Who is allowed in, Session handling, Token storage & keys, Jira site configuration

Area selection offered four options; the user selected the first three. "Local dev without Jira" was offered and not selected, so it fell to Claude's discretion. "Jira site configuration" was not offered upfront — it was surfaced by Claude after analysing the answers, on discovering that AUTH-01 cannot be satisfied without JIRA-01.

---

## Who is allowed in

### Access gate

| Option | Description | Selected |
|--------|-------------|----------|
| Jira access is enough | Anyone who can log into the Jira site can use the tool. Simplest, defensible since the tool only shows data they could already see — but it also lets them create releases and generate approval documents | ✓ |
| Allowlist in the app | Token must be valid AND the account's email on an allowlist managed in the tool | |
| Jira group membership | Token must be valid AND the account in a named Jira group; access administered in Jira | |
| You decide | Pick whichever fits and note the rationale | |

**User's choice:** Jira access is enough
**Notes:** Claude flagged that this scopes reads correctly but leaves writes unscoped — any authenticated Jira user can create releases and generate approval documents. Recorded as an accepted risk (CONTEXT.md D-02) with Jira group membership named as the cheap upgrade path. Not challenged further.

### How many users

| Option | Description | Selected |
|--------|-------------|----------|
| Just me / one person | Single release manager; no ownership or per-user visibility modelling needed | |
| A handful, same role | Two to five people, all can see and edit every release, but records capture who did what | |
| Several, roles later | Multiple people now, real chance of distinct permissions later; leave room in the data model | ✓ |

**User's choice:** Several, roles later
**Notes:** Drove the decision to record the acting user on every state change now, while explicitly not building a role model (CONTEXT.md D-03).

### Revocation handling

| Option | Description | Selected |
|--------|-------------|----------|
| Fails on next Jira call | Stored credential stops working; already-stored data stays readable until session ends | ✓ |
| Revalidate periodically | Background interval check; session terminated when the credential stops working | |
| Revalidate every request | Immediate but adds a Jira round trip to every call — slow and burns rate limit | |

**User's choice:** Fails on next Jira call
**Notes:** Consistent with the later stateless-JWT choice, which cannot force-revoke a session anyway.

---

## Session handling

### Session mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side session cookie | httpOnly/Secure/SameSite cookie, server session state. Best XSS posture, standard Spring Security, needs CSRF protection | |
| Stateless JWT | Signed token after validation. No server state, but XSS-exposed if held in JS, and early revocation needs a denylist | ✓ |
| Client re-sends Jira token | No app session; SPA holds the Jira token and sends it every call. Fewest moving parts, worst blast radius | |
| You decide | Pick the best fit and record why | |

**User's choice:** Stateless JWT
**Notes:** Claude recorded two consequences rather than re-opening the choice — the JWT should ride in an httpOnly cookie rather than localStorage to remove the XSS exposure (D-08), and early force-revocation is off the table (D-09).

### Session lifetime

| Option | Description | Selected |
|--------|-------------|----------|
| Idle timeout, ~8h absolute | Rolling idle window with a hard cap around a working day | ✓ |
| Long-lived, stay signed in | Persists for days until explicit sign-out | |
| Short, ~1h | Tight window, frequent re-authentication | |

**User's choice:** Idle timeout, ~8h absolute
**Notes:** Claude flagged the tension with statelessness — an idle timeout is inherently stateful, so it needs either an access/refresh token pair or a sliding-expiry re-issue. Left to the planner as an implementation choice, not a user decision (D-07).

### Token expiry handling

| Option | Description | Selected |
|--------|-------------|----------|
| Warn in the UI ahead of time | Record expiry at sign-in, show a banner in the days before it lapses | |
| Only react on failure | Do nothing until a Jira call 401s, then prompt to re-enter | ✓ |
| Warn plus email | Banner and email reminder; needs mail infrastructure | |

**User's choice:** Only react on failure
**Notes:** Makes the structured error format load-bearing — a 401 from Jira must be distinguishable from other failures to produce an actionable prompt.

---

## Token storage & keys

### Persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Persist encrypted per user | Encrypted row keyed to the Jira account; survives restarts | ✓ |
| Session-only, in memory | Never hits the database; lost on restart and every deploy | |

**User's choice:** Persist encrypted per user
**Notes:** Effectively forced by the stateless-JWT choice — with no server session store the credential must be durable, and the JWT is not a valid place for it.

### Key source

| Option | Description | Selected |
|--------|-------------|----------|
| Env var / mounted secret | Supplied at startup by the deployment environment; no new infrastructure | ✓ |
| Cloud KMS | Key Vault or similar; strongest, but a hard infra dependency in phase 1 | |
| Database-level encryption | Rely on disk/DB encryption rather than the column; weakest reading of AUTH-03 | |
| You decide | Pick the best fit and record the rationale | |

**User's choice:** Env var / mounted secret
**Notes:** Column-level encryption still required (D-12) — the key source was the question, not whether to encrypt. Key-version awareness recommended so later rotation or a KMS migration stays tolerable.

### Background access

| Option | Description | Selected |
|--------|-------------|----------|
| No — all work is user-triggered | Every external call happens because someone clicked something | ✓ |
| Yes — scheduled refresh | Something periodically refreshes data on its own, using a stored credential with no human present | |
| Not sure yet | Design so it stays possible without committing | |

**User's choice:** No — all work is user-triggered
**Notes:** Keeps the audit trail unambiguous about who caused each external call, and applies to Azure DevOps in Phase 6 as well.

---

## Jira site configuration

Surfaced by Claude, not offered in the original area list.

| Option | Description | Selected |
|--------|-------------|----------|
| App config property | Site URL from config/env in Phase 1; Phase 2 adds the management screen. Keeps the phase boundary intact | |
| Pull JIRA-01 into Phase 1 | Move the configure-site requirement into Phase 1 so sign-in and site config ship together | ✓ |
| Derive from the user's email | Infer the site from the email domain; fragile and often wrong | |

**User's choice:** Pull JIRA-01 into Phase 1
**Notes:** Acted on immediately — ROADMAP.md Phase 1 gained JIRA-01 and a fifth success criterion, Phase 2 lost it and had its first criterion reworded, and the REQUIREMENTS.md traceability row was moved. Coverage remains 40/40.

---

## Claude's Discretion

- **Local development without a live Jira site** — offered as a discussion area, not selected. Stubbed validation adapter behind a Spring profile, WireMock for integration tests.
- **Landing screen scope** — minimal app shell, empty state, and the settings screen AUTH-04 requires. No dashboards that later phases own.
- **Hexagonal package layout specifics** — package names and the ArchUnit rule set, per `.planning/research/ARCHITECTURE.md`.
- **Error-response shape** — a consistent structured API error format, established here because Phase 2's JIRA-06 depends on distinguishing failure kinds.
- **Idle-timeout mechanism under statelessness** — access/refresh pair versus sliding-expiry re-issue; planner's call.

## Deferred Ideas

- Jira group membership check as the access gate — recorded upgrade path if write-side access ever needs restricting.
- Roles and permissions — door left open by recording the acting user; no model built.
- Advance warning of Jira token expiry (banner and/or email) — declined for v1 in favour of reacting on failure.
- Cloud KMS for the encryption key — declined to avoid a hard infra dependency; key-version field makes later migration tolerable.
- Forced session revocation — declined as a consequence of stateless JWT.
