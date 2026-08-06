---
phase: 03-release-assembly
source: 03-REVIEW.md
fixed: 2026-08-06T00:00:00Z
fix_scope: critical + warning
iteration: 1
findings:
  total: 18
  fixed: 18
  skipped: 0
  deferred: 0
verification:
  backend_tests: 323
  backend_result: pass
  frontend_test_files: 8
  frontend_tests: 60
  frontend_result: pass
  frontend_typecheck: pass
  frontend_build: pass
status: fixed
---

# Code Review Fixes — Phase 03

All 18 findings from `03-REVIEW.md` were addressed. Nothing was skipped as a false positive
and nothing was deferred. Each fix is an atomic commit.

Note: the review's frontmatter counted 15 warnings; the body actually contains 16
(`WR-01`…`WR-16`), for 18 findings total including the two blockers.

## Blockers

| ID | Commit | Fix |
|----|--------|-----|
| CR-01 | `d2915ad` | FROZEN one-way door is now enforced inside the writing transaction, closing the read-then-write race that spanned an external Jira HTTP call |
| CR-02 | `7b38479` | Add-to-release failures are surfaced to the user instead of being swallowed |

**Why CR-01 mattered:** the phase's Success Criterion 2 — "freeze a release, which snapshots
its exact ticket set at that moment" — did not hold under concurrency. `requireOpen()` validated
an in-memory snapshot in a different transaction from the write it guarded, so a freeze in one
tab and an add in another could silently mutate a frozen audit record. This was the specific
mutation path that bypassed the guard.

## Warnings

| ID | Commit | Fix |
|----|--------|-----|
| WR-01 | `5384171` | Deleted the unused `ReleaseService.create` |
| WR-02 | `b1cf1dc` | Deleted the unused `findByStatusOrderByCreatedAtDesc` and its misleading comment |
| WR-03 | `19ee0fc` | Requested ticket keys Jira never returns are now counted rather than vanishing from both counters |
| WR-04 | `194589c` | `refresh` no longer stamps `lastRefreshedAt` over snapshots that were not actually refreshed |
| WR-05 | `9798062` | Framework-generated ProblemDetails now carry a machine-readable `reason`, restoring the D-04 error contract |
| WR-06 | `880350f` | An unknown manual-item id no longer reports "Release not found"; unknown id on delete no longer reports success |
| WR-07 | `fa55e70` | A unique-constraint violation on `addItems` no longer escapes as a 500 |
| WR-08 | `718a7be` | NOT NULL snapshot columns are defaulted at the Jira boundary instead of accepting nulls |
| WR-09 | `1c14ccc` | `browsedProjectKey` is derived from the mode that produced the results |
| WR-10 | `1792fe3` | `AddToReleaseDialog` resets state on the open transition, dropping stale form values and stale errors |
| WR-11 | `c0bbdfe` | Add buttons are disabled while the add is in flight |
| WR-12 | `39eb7bc` | A failed freeze or delete closes the confirmation dialog so the error is visible |
| WR-13 | `eeaf93c` | The manual-item form resets when its toggle closes it, so "+ Add manual item" after an Edit starts empty |
| WR-14 | `5332ca6` | Deleting a release no longer refetches it before navigating away |
| WR-15 | `62c7a05` | Each `ReleasePersistenceIT` method gets its own data instead of asserting exact result sets against an accumulating shared database |
| WR-16 | `3416f11` | `npm run lint` renamed to `npm run typecheck`, since it runs `tsc -b` and no linter |

**WR-16 changes a command name.** Frontend type checking is now `npm run typecheck`.
There is still no actual linter configured — the rename makes the script honest about what
it does rather than adding one, which was outside review-fix scope.

## Verification

Both suites were run synchronously after the final fix, on the merged result:

- Backend: `mvn -o test` — **323 tests, 0 failures, 0 errors, BUILD SUCCESS** (pre-fix baseline: 307)
- Frontend: `npm test` — **60 tests across 8 files, all passing** (pre-fix baseline: 49 across 5)
- Frontend: `npm run typecheck` — clean
- Frontend: `npm run build` — clean

Test count grew by 16 backend and 11 frontend cases, including regression tests covering both
blockers. New domain exceptions added in support of the fixes:
`ReleaseRefreshIncompleteException` (WR-04) and `ManualItemNotFoundException` (WR-06).
New frontend test file: `frontend/tests/routes/addResultMessage.test.ts`.
