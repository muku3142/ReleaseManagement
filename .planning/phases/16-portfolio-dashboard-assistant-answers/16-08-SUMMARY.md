---
phase: 16-portfolio-dashboard-assistant-answers
plan: 08
subsystem: dashboard
tags: [integration-test, checkpoint, deploy, walk-through]
requires: [16-01, 16-02, 16-03, 16-04, 16-05, 16-06, 16-07]
provides: [portfolio-dashboard-flow-it, phase-16-live-approval]
key-files:
  created:
    - src/test/java/com/evision/releasemgmt/adapters/web/PortfolioDashboardFlowIT.java
  modified: []
completed: 2026-10-25
---

# Phase 16 Plan 08: End-to-end IT, suites and live walk-through Summary

The portfolio dashboard is covered end to end over HTTP, all three suites are green in a clean worktree, and the user approved the live walk-through (SC1-SC4) on 2026-10-25.

## Task 1: PortfolioDashboardFlowIT (3ba56a5)

2 tests, both green on first run:
- Flow: a multi-product release counts once, a stage skip to Production, matrix cells, clients behind versus not installed (plan steps 1-6).
- Guards: 401 without a session cookie, 400 `VALIDATION_FAILED` for `productIds=not-a-uuid` (steps 7-8).

## Task 2: suites (clean worktree at 5706d6c + Task 1)

- Host backend: 1494 run, 0 failures, 0 errors.
- Frontend: `tsc -b` clean, vitest 696/696 in 57 files, `npm run build` ok (chunk-size warning only).
- rag-service: 370 run, 0 failures, 0 errors.

## Task 3: deploy and walk-through

- rag-service and the app were rebuilt and started together from main 5706d6c, so the app's six tools meet rag-service's raised cap (16-05). Backup and deploy details are in 15.1-08-SUMMARY.md (deployed in the same swap).
- The chunking fix was already on main (e980054), so plan step (a)'s regression risk did not apply.
- Walk-through: user replied "approved both phases" on 2026-10-25. No per-step observations were reported.

## Deviations

- Deploy was performed by the orchestrator at the user's instruction.
- Post-merge fix during wave 3: `ArchitectureBoundaryTest` allowlists did not name `InstallsTool` or `PortfolioDashboardService.matrix`; fixed in 0a1e082.

## Self-Check: PASSED
