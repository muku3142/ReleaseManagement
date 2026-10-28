---
phase: 15-products-clients-installations
plan: 13
subsystem: verification
tags: [integration-test, live-walkthrough, checkpoint]
requires: [15-01, 15-02, 15-03, 15-04, 15-05, 15-06, 15-07, 15-08, 15-09, 15-10, 15-11, 15-12]
provides: [ClientInstallFlowIT, Phase 15 sign-off]
affects: [16, 15.1]
requirements-completed: [PORT-01, PORT-02, PORT-03, PORT-04, PORT-05, PORT-06, PORT-07, PORT-08]
completed: 2026-10-22
---

# Phase 15 Plan 13: End-to-end flow and live walk-through Summary

The end-to-end HTTP IT covers products, clients, release products and installs. The full suites are green apart from known failures. The user approved the live walk-through.

## Tasks
1. ClientInstallFlowIT and full suites: 78fe544.
   - On main (clean worktree): backend 1380/1380, frontend 590 tests in 53 files.
   - On the deployed build (branch `live/phase15-chunking` = main a140406 plus the doc-assist chunking fix, plus the user's uncommitted prompt yml edits): backend 1395/1397. The 2 failures are the known `AssistantPropertiesTest` yml checks caused by those uncommitted edits.
2. Live walk-through (blocking checkpoint): the user replied **"approve phase 15"** on 2026-10-22.
   - The user ran it themselves and chose not to have it driven in Chrome.
   - No per-step observations were reported, so the 11 steps are recorded as **asserted by the user, not individually observed**.

## Deployment
- Before the rebuild, a DB backup was taken at `~/releasemanagement-backups/releasemgmt-pre-phase15-202610221226.dump`.
- Flyway V14 applied on the live database. The backfill created product CNV for the existing release (D-15-17).
- The live stack runs from worktree `D:/IdeaProjects/ReleaseManagement-live`, not main.
- `.env` was missing from the repo root. It was recreated from the running containers' environment with the same values.

## Deviations from Plan
- The deployed build is not main. It also carries the doc-assist chunking fix (02a84a2) and quick task 261022-iv7 (f8b466c..40843dc), which are on `live/phase15-chunking` only.

## Open items
- Merge `fix/doc-assist-chunking` and the 261022-iv7 commits into main before Phase 15.1 or 16 is deployed. Otherwise a main-only deploy loses them.
