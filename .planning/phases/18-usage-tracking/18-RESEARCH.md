# Phase 18: Usage Tracking - Research

**Researched:** 2026-10-22

> **SUPERSEDED 2026-10-26:** Phase 18 scope changed to a read-only GA4/Umami integration (see 18-CONTEXT.md). This research covers the dropped in-app counter design; re-run research before planning.
**Domain:** Identity-free first-party usage counters (Spring Boot ingest + React/TanStack Router capture + dashboard matrix)
**Confidence:** MEDIUM-HIGH (codebase facts verified by grep/read; browser behaviours cited from training knowledge are tagged ASSUMED)

## Summary

The phase is small and fully first-party: one table `usage_counter(day, event_key, count)` (Flyway V17), one ingest endpoint that never reads the principal, one read endpoint for the matrix, a frontend `track()` module with an in-memory buffer, a router subscription for page views, and a "Usage" view on the dashboard. No new libraries are needed (milestone research ruled out analytics SaaS, chart libraries, Micrometer).

Codebase findings that change planning: (1) `frontend/src/main.tsx` does NOT wrap the app in `<StrictMode>` today, so "StrictMode must not double-count" is a forward-looking and test-level requirement (frontend tests already use `StrictMode` wrappers), not a current dev symptom; the capture design must still be idempotent under double effect invocation. (2) There is no request-logging filter, no access-log config, no MDC use and no `getRemoteAddr` use in `src/main`; the only logging config is `application-local.yml` (`org.springframework.jdbc.core: DEBUG`, which would log SQL parameters at debug in the local profile). (3) `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so a malformed usage body would be logged by Spring's default handling; the log-capture test must cover rejected payloads too. (4) CSRF: the SPA sends `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie only through `apiFetch`/`buildInit`; the existing `SecurityConfig` comment records that bearer-cookie-authenticated writes are in practice exempt from CSRF (SameSite=Strict is the actual control). A keepalive `fetch` must still go through `buildInit`-equivalent headers to be safe.

**Primary recommendation:** Add `POST /api/feature-usage` (neutral name) taking `{events:[{key,count}]}`, validated against a code-level allowlist (`UsageEventRegistry`) mirrored in a frontend constant with a contract test; upsert with `INSERT ... ON CONFLICT (day, event_key) DO UPDATE SET count = usage_counter.count + EXCLUDED.count` via `JdbcClient`/`JdbcTemplate`; frontend buffers in memory, flushes on a timer and `visibilitychange`, deduplicates page views by pathname-change in a router `onResolved` subscription (module-level, not a React effect), and the matrix is a shaded HTML table on a dashboard tab.

## Project Constraints (from CLAUDE.md)

- Java Spring Boot backend, React SPA; Java 25/Spring Boot 4.1; PostgreSQL; Flyway; hexagonal layout (`domain`, `ports`, `application`, `adapters`, `config`) enforced by `ArchitectureBoundaryTest`.
- No over-engineering/abstraction; minimal code; follow framework docs; no chart library (v1.2 SUMMARY).
- Global CLAUDE.md: no `Co-Authored-By` trailers; no emojis; validate only at system boundaries (the usage endpoint IS a boundary: allowlist validation is required).
- Security: untrusted input sanitised; the usage payload is untrusted.
- GSD workflow: edits via GSD commands (research only here; nothing committed).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Page-view detection (route template) | Browser (router subscription) | - | Only the SPA knows route templates (`routeId`), server sees only API calls |
| Feature-action events | Browser | - | Fired at UI actions; name from allowlist constant |
| Buffering/batching/flush | Browser | - | Must not block UI (USAGE-04) |
| Allowlist validation, count caps | API / Backend | - | Server is the trust boundary; rejects unknown keys |
| Day bucketing | API / Backend | - | Server date (not client clock) avoids client-supplied dates and timezone skew; no per-event timestamp stored |
| Counter persistence (upsert) | Database | API | Atomic `ON CONFLICT` increments |
| Matrix aggregation | API / Backend | Browser | Server returns rows by day; client pivots/shades |
| Matrix display | Browser (dashboard tab) | - | Shaded HTML table, labelled approximate |

## Standard Stack

No new dependencies. Use what exists.

| Piece | Version | Purpose | Source |
|-------|---------|---------|--------|
| Spring `JdbcClient`/`JdbcTemplate` (Boot-managed) | Boot 4.1.0 | Upsert + matrix query | [CITED: .planning/research/v1.2/STACK.md] native upsert, no JPA entity needed |
| Flyway `V17__usage.sql` | Boot-managed | Schema | [VERIFIED: ROADMAP.md] V17 pre-assigned; latest existing migration in tree is V14 (V15/V16 belong to other phases) |
| `@tanstack/react-router` | 1.170.18 | `router.subscribe('onResolved', ...)` | [VERIFIED: frontend/package.json; node_modules router-core types show `onResolved` event with `pathChanged`/`hrefChanged`] |
| React | 19.2.8 | UI | [VERIFIED: package.json] |
| `@tanstack/react-query` | existing | Matrix read hook | [VERIFIED: repo uses it in `frontend/src/api/*`] |
| Vitest 4 | existing | Frontend tests | [VERIFIED: package.json] |
| Testcontainers PostgreSQL (`PgVectorImage.IMAGE`) | existing | Schema IT | [VERIFIED: `PromotionSchemaIT` pattern] |

Alternatives rejected (already decided at milestone level): PostHog/Matomo/Plausible, Micrometer counters, chart libraries, cookies/session ids.

## Architecture Patterns

### System flow

```
Router navigation / UI action
        |
        v
  track(key)  <-- key must be in frontend allowlist constant (type-checked union)
        |
        v
 in-memory Map<key,count>  (module singleton)
        |  flush: timer (~30 s) + visibilitychange hidden + pagehide
        v
 fetch POST /api/feature-usage  {events:[{key,count}]}  (keepalive, errors swallowed, no await in UI path)
        |
        v
 Security chain (authenticated, principal ignored) -> UsageController (no Principal param)
        |
        v
 UsageService: validate every key in UsageEventRegistry, cap count & batch size, reject whole batch if any unknown
        |
        v
 UsageRepository: INSERT ... ON CONFLICT (day, event_key) DO UPDATE SET count = count + EXCLUDED.count   (day = server current_date)
        |
        v
 GET /api/feature-usage/matrix?from&to&bucket=day|week  -> Dashboard "Usage" tab (shaded table, "approximate")
```

### Recommended structure (follows existing layout)

- `domain/usage/UsageEvent` (allowlist enum or registry value type; no framework imports), `ports/UsageRepository`, `application/UsageService`, `adapters/persistence/JdbcUsageRepository`, `adapters/web/UsageController` + dto, `db/migration/V17__usage.sql`.
- Frontend: `frontend/src/usage/` (`events.ts` allowlist, `track.ts` buffer/flush, `usePageViews` or router-level subscription), `frontend/src/api/usage.ts` (matrix hook), matrix component in dashboard.

### Pattern: page views from route templates

Subscribe once at module level after router creation: on `onResolved` with `pathChanged` true, read the deepest match's `routeId` from `router.state.matches` (e.g. `/releases/$releaseId`, a template, never the concrete id) and map to a `page.<name>` key via an explicit table; ignore routes not in the table. [VERIFIED: router-core `Matches.d.ts` exposes `routeId`/`fullPath`; event shape has `pathChanged`.] Exact payload field access should be confirmed in the plan's first task. Subscribing outside React means no effect re-run, so StrictMode cannot double-fire it; `pathChanged` also prevents counting search-param-only changes (e.g. Phase 16 dashboard tab/filter params live in the URL, and must not inflate page views).

### Pattern: feature actions

`track('feature.export-pdf')` etc. at the call sites of existing actions. Explicit call sites only; never derive names from DOM text, URLs or data. Event vocabulary is a closed list (candidates: export PDF/DOCX, promote, freeze, generate document, assistant ask, AI revise/transform, template save, record install). Final vocabulary is a discuss-phase decision.

### Schema (V17)

`usage_counter(day DATE NOT NULL, event_key VARCHAR(64) NOT NULL, count BIGINT NOT NULL CHECK (count >= 0), PRIMARY KEY (day, event_key))`. A schema IT should assert via `information_schema.columns` that the table has exactly these columns (pattern exists in `PromotionSchemaIT`/`V14PortfolioMigrationIT`). Optionally a DB `CHECK` on key format.

### Anti-Patterns

- Per-event rows or timestamps finer than a day (re-identification in a single-digit-user tool).
- Free-form event names or accepting the client's date.
- Reading `Principal`/`Authentication`, or any audit/actor-resolution call (`ActorResolutionService`) on the usage route.
- Sending `window.location`, document title, or route params.
- Counting from a `useEffect` in a component (StrictMode double invoke, re-mount on layout changes).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Atomic increment | read-then-write in Java | PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` | Race-free |
| Page route matching | regex on `location.pathname` | Router `routeId` templates | Never sees concrete ids |
| Charting | chart library | Shaded `<table>` (v1.2 decision) | Explicit milestone constraint |
| Delivery on tab close | custom retry queue | `fetch` with `keepalive: true`, drop on failure | Counts are approximate by design |

## Runtime State Inventory

Not a rename/migration phase: omitted. (New table only; nothing pre-existing to migrate.)

## Common Pitfalls

1. **Principal leakage in logs.** No request-log filter exists today [VERIFIED: grep of `src/main` for Filter/MDC/getRemoteAddr found only `SecurityConfig` and a DTO]. Pitfall is future/inadvertent: `application-local.yml` enables `org.springframework.jdbc.core: DEBUG`, which logs SQL and bound parameters (event keys and counts only, no identity, acceptable but test the default profile). Also `ResponseEntityExceptionHandler` and Spring's default logging of `HttpMessageNotReadableException` can echo payload fragments on malformed bodies. Mitigation: log-capture test posting valid, unknown-key and malformed bodies as an identifiable user and asserting no email/user id/IP/URL/key-name-of-concrete-id in captured output. Existing log-capture patterns: `OutputCaptureExtension`/`ListAppender` used in `AssistantChatStreamIT`, `AssistantServiceTest`.
2. **Tomcat access log / reverse proxy.** The usage request is an ordinary HTTP request; any container access log or the hosted reverse proxy (Phase 23 hosted deployment) would log IP and URL. Not configured in this repo [VERIFIED: no tomcat/accesslog/forwarded settings in `application.yml`, `compose.app.yaml`, `docker/`]. USAGE-02 scope is "server stores/logs for usage events" in the app; document the infrastructure caveat rather than claim it.
3. **StrictMode.** Not currently enabled in `main.tsx`. Tests for components use `<StrictMode>`. A module-level subscription plus a buffer keyed by event is idempotent; the risk is components calling `track()` inside `useEffect`. Provide a test that renders under `StrictMode` and asserts a single count for a mount-time page view.
4. **Hard reload / initial load.** The first page view occurs at initial router resolution; `onResolved` fires for the initial load too [ASSUMED: verify], so confirm exactly one count on cold load.
5. **Session refresh loop.** `apiFetch` retries on 401 with silent refresh; a keepalive flush after session expiry should not trigger redirects or UI errors. Use a dedicated lightweight `fetch` (own helper, same `buildInit` header logic for XSRF) that swallows every error, not `apiFetch` with its credential-invalid side effects. `apiFetch` also marks credentials invalid on Jira-rejection reasons.
6. **Adblock.** Avoid `analytics`, `track`, `telemetry`, `beacon`, `collect` in paths and file names (Java and TS), including the frontend folder name. Use `feature-usage`/`usage`. Blocking by filter lists on same-origin neutral paths is LOW confidence [ASSUMED]; manual check in uBlock Origin/Brave is a roadmap-mandated task, so the matrix stays labelled approximate.
7. **Unauthenticated pages.** `/login` page views: the endpoint requires authentication (`/api/**` authenticated). Do not make the usage route public; skip login page views.
8. **Cardinality / abuse.** Cap events per batch (e.g. 50), count per event per request (e.g. 1000), body size; reject unknown keys with 400 (whole batch) and a ProblemDetail `reason` consistent with `GlobalExceptionHandler`.
9. **Day boundary.** Use a server-side date; decide zone (DB `current_date` vs app-zone `LocalDate` via injected `Clock`). Matrix labelling must state the zone.
10. **Frontend/backend allowlist drift.** Add a contract test (like the existing `DashboardSpanContractTest` pattern for `MAX_HISTORY_SPAN_DAYS`) that compares the frontend `events.ts` list to the backend registry.

## Code Examples

Upsert (pattern from STACK.md/ARCHITECTURE.md Decision 5):

```sql
INSERT INTO usage_counter (day, event_key, count) VALUES (?, ?, ?)
ON CONFLICT (day, event_key) DO UPDATE SET count = usage_counter.count + EXCLUDED.count
```

Flush (illustrative):

```ts
fetch('/api/feature-usage', { method: 'POST', keepalive: true, credentials: 'same-origin',
  headers: { 'Content-Type': 'application/json', ...xsrfHeader() }, body })
  .catch(() => undefined);
```

Note: keepalive request bodies are limited (~64 KiB total in-flight) [ASSUMED]; batches are tiny.

## State of the Art

`navigator.sendBeacon` cannot set custom headers (no XSRF header), so `fetch keepalive` is the chosen mechanism (v1.2 SUMMARY). Router `onResolved` subscription replaces per-component effects.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `onResolved` fires on initial load and exposes the leaf match via `router.state.matches` | Pitfalls 4 / Patterns | Page view missed/doubled on cold load; confirm in task 1 |
| A2 | Same-origin neutral path rarely blocked by adblock filter lists | Pitfalls 6 | Undercounting; mitigated by "approximate" label |
| A3 | keepalive body limit ~64 KiB | Code Examples | None at these sizes |
| A4 | Bearer-cookie auth exempts the POST from CSRF in practice (per `SecurityConfig` comment), so missing XSRF header would not 403, but send it anyway | Summary | If CSRF enforced, flush silently 403s; verify with an integration test through the real security chain |

## Open Questions for discuss-phase (user decisions, not decided here)

1. **Vocabulary:** Which pages and which feature actions are in the allowlist? (Proposed candidates above; also whether Phase 15-21 pages added later are included now or as they land.)
2. **Matrix bucket:** days only, weeks only, or a toggle? Default window (e.g. last 14 days / 8 weeks) and maximum range?
3. **Retention:** keep counters forever, or prune beyond N months? (Table is tiny; "forever" is simplest.)
4. **Placement:** a third dashboard tab "Usage" alongside Phase 16's Releases/Portfolio tabs (tab as URL search param like D-16-01), or a section within one of them? Phase 16 changes `parseDashboardSearch`, so Phase 18 must extend the tab enum after/with Phase 16; confirm ordering/merge approach.
5. **Visibility:** is the matrix visible to every signed-in user (single permission level exists) or hidden behind a setting?
6. **Opt-out/disable:** a config flag to turn capture off entirely? (Extra surface; only if wanted.)
7. **Time zone for "day":** server zone, UTC, or configurable?
8. **Sort/shape of matrix:** rows grouped pages vs features, with totals column/row; empty rows shown as zeros or hidden? How to label the approximate caveat (adblock, closed tabs, no unique users).
9. **Should dashboard tab/filter changes or the Assistant streaming count as page views/features?** (Search-param-only changes are proposed as not counted.)
10. **Do we want the manual adblock check as a gated verification step, or a documented note?**

## Environment Availability

| Dependency | Required By | Available | Fallback |
|------------|------------|-----------|----------|
| Docker/Testcontainers | Schema IT | Used by existing ITs [VERIFIED: PromotionSchemaIT] | - |
| Node/Vitest | Frontend tests | Existing | - |
| uBlock Origin/Brave for manual check | USAGE ad-block check | Not probed | Manual, user-run |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend | JUnit 6 + AssertJ + Testcontainers (`@SpringBootTest`, `PgVectorImage.IMAGE`) |
| Frontend | Vitest 4 (`npm test` in `frontend/`, tests under `frontend/tests/`) |
| Quick run | `mvn -Dtest=Usage* test` (JAVA_HOME must be set per Maven call, per project memory); `npm test -- usage` |
| Full suite | `mvn verify`; `npm test` |

### Phase Requirements to Test Map
| Req | Behavior | Type | Command | Exists |
|-----|----------|------|---------|--------|
| USAGE-01 | Route template keys, allowlist, unknown rejected (400, nothing stored) | unit + IT | `UsageServiceTest`, `UsageControllerIT`; frontend `track.test.ts`, routeId-to-key table test | Wave 0 |
| USAGE-01 | Frontend/back allowlist contract | contract | `UsageEventContractTest` | Wave 0 |
| USAGE-02 | Table has only day/event_key/count; upsert accumulates | IT | `UsageSchemaIT` (information_schema) | Wave 0 |
| USAGE-02 | No user/IP/URL in logs for valid, unknown, malformed requests | IT log-capture | `UsageLogHygieneIT` | Wave 0 |
| USAGE-02 | Controller has no Principal; `ArchitectureBoundaryTest` rule: usage classes do not depend on `ActorResolutionService`/security | arch | extend `ArchitectureBoundaryTest` | Wave 0 |
| USAGE-03 | Matrix endpoint pivot + UI renders table with "approximate" label | IT + component | `UsageMatrix.test.tsx` | Wave 0 |
| USAGE-04 | StrictMode render counts once; flush failure swallowed; not awaited by UI | component/unit | `usageStrictMode.test.tsx` | Wave 0 |

### Wave 0 Gaps
All of the above test files are new. No framework install needed.

## Security Domain

| ASVS | Applies | Control |
|------|---------|---------|
| V4 Access Control | yes | `/api/**` authenticated; no role split exists |
| V5 Input Validation | yes | Allowlist enum, caps on batch size/count, Bean Validation |
| V7 Logging | yes | No identity/URL in usage logs; log-capture test |
| V8 Data Protection | yes | Schema cannot hold identity; day granularity |
| V13 API | yes | Neutral endpoint, JSON only, ProblemDetail errors |

Threats: counter inflation/DB bloat (caps, allowlist), stored XSS via event name (allowlist + escaped render), identity smuggling via key (allowlist; keys are fixed strings), CSRF on ingest (SameSite=Strict, XSRF header sent).

## Sources

### Primary (HIGH, verified in repo)
- `.planning/ROADMAP.md` Phase 18, `.planning/REQUIREMENTS.md` USAGE-01..04, `.planning/research/v1.2/{SUMMARY,ARCHITECTURE,PITFALLS,FEATURES,STACK}.md`
- `SecurityConfig.java`, `GlobalExceptionHandler.java`, `DashboardController.java`, `frontend/src/{main.tsx,router.tsx,api/client.ts,api/dashboard.ts,components/AppShell.tsx}`, `application*.yml`, existing IT/arch tests
- `frontend/node_modules/@tanstack/router-core` type definitions (`onResolved`, `routeId`)
- `.planning/phases/16-portfolio-dashboard-assistant-answers/16-CONTEXT.md` (Releases/Portfolio tabs as URL search param)

### Tertiary (LOW)
- Adblock behaviour and keepalive limits: training knowledge, tagged ASSUMED.

## Metadata

**Confidence:** stack HIGH (no new deps), architecture HIGH, pitfalls MEDIUM (browser behaviours assumed).
**Research date:** 2026-10-22. **Valid until:** ~30 days.
