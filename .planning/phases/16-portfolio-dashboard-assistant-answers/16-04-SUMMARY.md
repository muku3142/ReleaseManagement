---
phase: 16-portfolio-dashboard-assistant-answers
plan: 04
subsystem: frontend-dashboard-data-layer
tags: [react-query, tanstack-router, url-state, dashboard]
requires: [16-01]
provides: [DashboardSearch URL schema, product-aware release and history requests, usePortfolioSummary/Behind/Matrix, missingDocuments/outOfDateDocuments, dashboard invalidation]
affects: [16-06, 16-07]
key-files:
  modified:
    - frontend/src/api/dashboard.ts
    - frontend/src/routes/DashboardRoute.tsx
    - frontend/src/api/installs.ts
    - frontend/src/api/clients.ts
    - frontend/src/api/products.ts
    - frontend/src/api/releases.ts
    - frontend/tests/api/dashboard.test.ts
    - frontend/tests/api/portfolio.test.ts
    - src/test/java/com/evision/releasemgmt/adapters/web/DashboardSpanContractTest.java
decisions:
  - "Product ids in query keys and request paths are sorted so selection order never splits the cache"
  - "paperworkCounts is built on missingDocuments/outOfDateDocuments so tiles and docs-filtered rows share one rule"
metrics:
  tasks: 2
  completed: 2026-10-22
---

# Phase 16 Plan 04: Frontend dashboard data layer Summary

URL state (tab, products, docs, archived), product-aware release and history requests, three independent portfolio queries, shared document predicates, and `['dashboard']` invalidation on every portfolio mutation.

## Commits
- 64d7a2e feat(16-04): URL state, portfolio hooks, predicates, cap contract test
- 4097d4e fix(16-04): dashboard invalidation in installs, clients, products, releases

## Tests
- `npx tsc -b` clean; `vitest run tests/api` 128 passed (10 files); DashboardRoute component tests 46 passed with dashboard.test.ts.
- `DashboardSpanContractTest` passes (now also asserts `MAX_PRODUCT_FILTER = 50`).
- TanStack serializer round trip for `products` verified (research assumption A1 holds).

## Deviations from Plan

**1. [Rule 3 - Blocking] Dashboard route compile error after widening DashboardSearch**
- **Issue:** the show-all toggle in `DashboardRoute.tsx` uses `search: (prev) => ...`; `prev` is typed as the union of every route's search (other routes have a `tab` of `'clients' | 'products'`), which no longer matched the widened `DashboardSearch.tab`.
- **Fix:** cast `prev as DashboardSearch` in that one reducer (kept the reducer form because existing tests call `search(prev)`).
- **Commit:** 64d7a2e

## Known Stubs
None.

## Notes
- Void install goes through the same `useInstallMutation` as record/correct, so one invalidation covers all three.
- STATE.md and ROADMAP.md intentionally untouched (orchestrator owns them).

## Self-Check: PASSED
