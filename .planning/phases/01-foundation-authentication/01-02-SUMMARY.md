---
phase: 01-foundation-authentication
plan: 02
subsystem: frontend-foundation
tags: [vite-8, react-19, typescript-7, tanstack-router, tanstack-query, tailwind-v4, spa-forwarding, jira-site-config]
dependency-graph:
  requires:
    - "01-01: GET|PUT /api/settings/jira-site, ProblemDetail reason-code registry"
  provides:
    - "frontend/src/api/client.ts: apiFetch<T>(path, init) / ApiError / ProblemDetail contract (verbatim import target for plan 07)"
    - "frontend/src/api/jiraSite.ts: useJiraSite() / useUpdateJiraSite() / jiraSiteQueryKey"
    - "frontend/src/router.tsx: code-based TanStack Router routeTree (/, /settings) — additive structure for plan 07's /login + beforeLoad guard"
    - "frontend/src/components/AppShell.tsx: header/nav/Outlet shell — plan 07 adds signed-in identity + sign-out here"
    - "src/main/java/com/evision/releasemgmt/config/SpaForwardingController.java: catch-all forward to index.html"
    - "Single-jar build: frontend profile self-activates, dist/ embeds into BOOT-INF/classes/static"
  affects:
    - "Plan 07 (sign-in + token-replacement UI) imports client.ts/jiraSite.ts patterns verbatim and extends router.tsx/AppShell.tsx additively"
tech-stack:
  added:
    - "Vite 8.1.5, React 19.2.8, TypeScript 7.0.2 (held — did not need to fall back to 5.9.3)"
    - "@tanstack/react-router 1.170.18, @tanstack/react-query 5.101.4"
    - "Tailwind CSS v4 (@tailwindcss/vite)"
    - "vitest ^4 (plan specified ^3 — bumped for vite 8 peer compatibility, see deviations)"
    - "@types/node ^22 (not in plan — required for vite.config.ts's Node-typed vite.d.ts to compile under strict TS)"
  patterns:
    - "Single HTTP boundary: every SPA request goes through apiFetch, which sends credentials:'same-origin' and echoes XSRF-TOKEN as X-XSRF-TOKEN on non-GET/HEAD"
    - "No token ever touches JS-reachable storage (localStorage/sessionStorage) — enforced by a grep gate"
    - "Code-based TanStack Router routes (no file-based codegen plugin)"
key-files:
  created:
    - frontend/package.json
    - frontend/package-lock.json
    - frontend/vite.config.ts
    - frontend/tsconfig.json
    - frontend/tsconfig.node.json
    - frontend/index.html
    - frontend/.gitignore
    - frontend/src/main.tsx
    - frontend/src/index.css
    - frontend/src/vite-env.d.ts
    - frontend/src/router.tsx
    - frontend/src/api/client.ts
    - frontend/src/api/client.test.ts
    - frontend/src/api/jiraSite.ts
    - frontend/src/components/AppShell.tsx
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/src/routes/SettingsRoute.tsx
    - src/main/java/com/evision/releasemgmt/config/SpaForwardingController.java
  modified:
    - README.md
decisions:
  - "TypeScript 7.0.2 held (no fallback to 5.9.3 needed) once @types/node was added and vite.config.ts got a /// <reference types=\"vitest/config\" /> triple-slash directive"
  - "vitest bumped from the plan's ^3 to ^4: vitest 3.2.7's peerDependency range for vite excludes 8.x, so npm silently nested a second vite@7.3.6 under node_modules/vitest/node_modules/vite, producing dozens of duplicate-identifier/incompatible-ImportMeta TS errors. vitest 4.1.10 declares vite: \"^6.0.0 || ^7.0.0 || ^8.0.0\" and resolves to a single shared vite 8.1.5 tree"
metrics:
  duration_minutes: 55
  tasks_completed: 2
  files_created: 18
  completed_date: "2026-07-29"
---

# Phase 01 Plan 02: Frontend Scaffold & Jira Site Settings Summary

React 19 SPA on Vite 8/TypeScript 7, built into the same Spring Boot jar as the backend, giving a release manager a working Dashboard/Settings shell where the configured Jira Cloud site can be viewed and changed end-to-end (JIRA-01).

## What Was Built

**Task 1 — SPA scaffold, API client, single-jar packaging.** `frontend/` initialized with Vite 8.1.5 + React 19.2.8 + TypeScript 7.0.2 + TanStack Router 1.170.18 + TanStack Query 5.101.4 + Tailwind v4, pinned exactly as specified. `vite.config.ts` proxies `/api` and `/actuator` to `localhost:8080` with `changeOrigin: false` so the dev server and backend share cookies exactly as the packaged jar does. `frontend/src/api/client.ts` is the single HTTP boundary: `apiFetch<T>` sends `credentials: 'same-origin'`, echoes the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` on every non-GET/HEAD request, and throws `ApiError` carrying a typed `ProblemDetail` (with its `reason` field) on any non-2xx/204 response. Four Vitest cases cover the CSRF echo, the GET exemption, the `ApiError`/`reason` mapping on a 400, and the 204-to-`undefined` short-circuit. `SpaForwardingController` forwards any non-`/api`, non-`/actuator`, dotless path to `index.html` (RESEARCH.md Pattern 4, copied verbatim) so client-side routes survive a refresh. README gained a "Frontend / local development" section and the single-jar build/run commands, without touching the backend section plan 01 wrote.

**Task 2 — App shell, routing, Settings screen.** `frontend/src/router.tsx` builds a code-based TanStack Router tree: `/` (Dashboard) and `/settings` (Settings), both children of a root route rendering `AppShell`. `AppShell` is a header with title, a two-link nav (`activeProps` styling), and `<Outlet />` inside `<main className="p-6">` — no auth-dependent UI, as plan 07 owns that. `DashboardRoute` is the deliberate empty state only. `frontend/src/api/jiraSite.ts` wraps `GET|PUT /api/settings/jira-site` behind `useJiraSite()`/`useUpdateJiraSite()`, matching the plan's `<interfaces>` contract verbatim (`jiraSiteQueryKey`, `JiraSiteConfig` shape). `SettingsRoute` renders the loading/error/not-configured states (`SITE_NOT_CONFIGURED` gets its own friendly message instead of a generic error), shows `baseUrl`/`updatedAt`/`updatedBy` read-only, and a controlled form whose Save button is disabled while the mutation is pending. On a `VALIDATION_FAILED` mutation error it renders `problem.fieldErrors?.baseUrl` next to the input — the server's regex is the only URL-format check; nothing is duplicated client-side. A static "Jira API token" heading with "Sign in to manage your Jira API token." is left in place for plan 07 to replace additively.

**End-to-end manual verification performed:** ran `mvn -Pfrontend package` (unmodified `pom.xml`), confirmed `BOOT-INF/classes/static/index.html` inside the jar, started it against the live Postgres 18 compose container with JDK 25 (`--spring.docker.compose.enabled=false`, per the known Docker CLI/daemon skew noted in 01-01-SUMMARY.md). `curl localhost:8080/settings` returned 200 with the SPA's `index.html` body (deep-link forwarding works); `curl localhost:8080/api/settings/jira-site` returned the seeded config; a `PUT` with `https://acme.atlassian.net` returned 200 and round-tripped on the next `GET`; a `PUT` with `http://evil.example.com` returned 400 with `reason: "VALIDATION_FAILED"` and a `fieldErrors.baseUrl` message — exactly what `SettingsRoute` renders.

## Verification

- `cd frontend && npm ci && npm run build` — exits 0, `frontend/dist/index.html` produced.
- `cd frontend && npx vitest run` — 4/4 tests passing.
- `git diff --name-only -- pom.xml` — empty; `pom.xml` untouched by this plan.
- `mvn -Pfrontend package` — jar contains `BOOT-INF/classes/static/index.html`.
- Live-server manual verification (see above) — all 5 `<verification>` steps from the plan passed.
- Grep gates: `X-XSRF-TOKEN` (1), `credentials: 'same-origin'` (1), `localStorage|sessionStorage` (0), `dangerouslySetInnerHTML` (0), `forward:/index.html` (1), `SITE_NOT_CONFIGURED` (1), `VALIDATION_FAILED` (1), no client-side `atlassian\.net$`/`new RegExp` duplication of the server's URL validator (0).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `tsc -b` failed under TypeScript 7.0.2 without `@types/node`**
- **Found during:** Task 1, first `npm run build`
- **Issue:** `vite.config.ts` (and vitest's own type surface) reference Node built-ins (`node:fs`, `Buffer`, `NodeJS` namespace, etc.) that aren't ambient without `@types/node`. The plan's devDependency list omitted it.
- **Fix:** Added `@types/node: "^22"` to devDependencies; added `frontend/src/vite-env.d.ts` with `/// <reference types="vite/client" />` so the CSS side-effect import in `main.tsx` type-checks.
- **Files modified:** `frontend/package.json`, `frontend/src/vite-env.d.ts` (new)
- **Commit:** 8fb6c1f

**2. [Rule 3 - Blocking] `vite.config.ts`'s `test` field failed to type-check ("Object literal may only specify known properties")**
- **Found during:** Task 1, same build attempt
- **Issue:** Vitest augments Vite's `UserConfig` type with a `test` property via module augmentation, but that augmentation isn't picked up for a plain `defineConfig` import from `'vite'` unless a reference to `vitest/config`'s types is present in the file being checked.
- **Fix:** Added `/// <reference types="vitest/config" />` at the top of `vite.config.ts`, the documented Vitest pattern for this exact case.
- **Files modified:** `frontend/vite.config.ts`
- **Commit:** 8fb6c1f

**3. [Rule 3 - Blocking] `vitest@^3` and `vite@8.1.5` peer-dependency mismatch produced dozens of duplicate-identifier TS errors**
- **Found during:** Task 1, after fixing #1 and #2, `npm run build` still failed
- **Issue:** `vitest@3.2.7`'s `peerDependencies.vite` range does not include `^8`, so npm silently installed a second, nested `vite@7.3.6` under `node_modules/vitest/node_modules/vite` to satisfy it. `tsc -b` then saw two incompatible `ImportMeta`/`ViteHotContext` type trees (one from the top-level `vite@8.1.5`, one from vitest's nested `vite@7.3.6`) and failed with `TS2430`/`TS2300` duplicate-identifier errors across `@types/chai`, `vitest`'s own `.d.ts` chunks, and `rollup`.
- **Fix:** Bumped `vitest` from the plan's `^3` to `^4` (resolved to 4.1.10), whose `peerDependencies.vite` is `"^6.0.0 || ^7.0.0 || ^8.0.0"` — this collapses to a single shared `vite@8.1.5` install with no nested duplicate, and the build/tests both pass cleanly.
- **Files modified:** `frontend/package.json`
- **Commit:** 8fb6c1f
- **Note:** TypeScript 7.0.2 itself was never rejected — the plan's fallback-to-5.9.3 clause was not needed; the real blocker was the vitest/vite peer-version mismatch, not the TypeScript version.

**4. [Rule 1 - Bug] `SettingsRoute` didn't gate the field-level error on `reason === 'VALIDATION_FAILED'`**
- **Found during:** Task 2, acceptance-criteria grep pass (`grep -c 'VALIDATION_FAILED' frontend/src/routes/SettingsRoute.tsx` returned 0)
- **Issue:** Initial implementation rendered `problem.fieldErrors?.baseUrl` whenever it was present, without checking `problem.reason` first, matching the plan's data but not its literal wording/intent.
- **Fix:** Extracted the mutation's `ApiError.problem` once and gated `fieldError` on `mutationProblem?.reason === 'VALIDATION_FAILED'` before falling back to `mutationProblem?.detail` for the generic message.
- **Files modified:** `frontend/src/routes/SettingsRoute.tsx`
- **Commit:** 71f94ab

**5. [Environment] Build artifacts (`*.tsbuildinfo`, `vite.config.js`, `vite.config.d.ts`) appeared as untracked files after the first `tsc -b`**
- **Found during:** Pre-commit `git status` review
- **Issue:** `tsc -b`'s composite project references write `.tsbuildinfo` cache files, and older leftover compiled artifacts (`vite.config.js`/`.d.ts`) from an earlier config shape were on disk.
- **Fix:** Deleted the stray build artifacts and extended `frontend/.gitignore` with `*.tsbuildinfo`, `vite.config.js`, `vite.config.d.ts` so they never get staged.
- **Files modified:** `frontend/.gitignore`
- **Commit:** 8fb6c1f

None of the above required an architectural decision (Rule 4) — all were dependency-version/type-surface corrections or bugs against the plan's own literal acceptance criteria.

## Known Stubs

None. The Settings screen is fully wired to the live backend endpoint with no mock/placeholder data path. The "Jira API token" section intentionally shows only static text ("Sign in to manage your Jira API token.") per the plan's explicit design — plan 07 (AUTH-04) replaces that line with the real replacement form; this is documented in the plan itself, not an undocumented stub.

## Threat Flags

None beyond the plan's own `<threat_model>` (T-02-01 through T-02-06) — no new network endpoints, auth paths, or schema surfaces were introduced beyond what the plan specified. `vitest`'s major-version bump (#3 above) is a devDependency/test-tooling change only and does not alter the production bundle or its trust boundaries.

## Self-Check: PASSED

All 18 created/modified files verified present on disk. Both commits (8fb6c1f, 71f94ab) verified in `git log`.
