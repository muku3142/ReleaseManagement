---
phase: 15-products-clients-installations
plan: 03
subsystem: frontend-api
tags: [react-query, portfolio, products, clients, installs]
requires: []
provides:
  - useProducts/useAddProduct/useRenameProduct/useArchiveProduct/useUnarchiveProduct
  - useClients/useClient/useAddClient/useRenameClient/useArchiveClient/useUnarchiveClient/useLinkProduct/useUnlinkProduct
  - useInstalls/useRecordInstall/useCorrectInstall/rejectedClients
  - useSetReleaseProducts, ReleaseProduct, products on ReleaseSummary/Release
  - Phase 15 REASON_MESSAGES, ProblemDetail.clients, exported SMALL_BUTTON
affects: [plans 05, 06, 10]
key-files:
  created:
    - frontend/src/api/products.ts
    - frontend/src/api/clients.ts
    - frontend/src/api/installs.ts
    - frontend/tests/api/portfolio.test.ts
  modified:
    - frontend/src/api/releases.ts
    - frontend/src/api/client.ts
    - frontend/src/api/auth.ts
    - frontend/src/components/EnvironmentLadderSection.tsx
    - frontend/tests/api/releases.test.ts
    - frontend/tests/components/ReleaseDetailRoute.test.tsx
decisions:
  - CreateReleaseInput left unchanged (plan 06 owns productIds)
metrics:
  tasks: 2
  completed: 2026-10-21
---

# Phase 15 Plan 03: Frontend data layer Summary

Typed react-query hooks for every Phase 15 endpoint (products, clients, installs, release products), the 18 new reason-code messages with the exact D-15-15 and D-15-18 copy, and a `clients` field on ProblemDetail read by `rejectedClients`.

## Commits
- 60718d5 feat(15-03): data layer and reason messages
- test commit: portfolio.test.ts plus fixture updates

## Deviations from Plan
None. Only two fixtures failed typecheck (releases.test.ts summary(), ReleaseDetailRoute.test.tsx RELEASE); the other listed test files needed no change. `npm run typecheck` was run as `npx tsc -b` (clean); full vitest suite passes (48 files, 544 tests).

## Known Stubs
None.

## Self-Check: PASSED
