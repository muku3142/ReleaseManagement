---
phase: 06-azure-devops-enrichment
plan: 08
subsystem: enrichment
tags: [azure-devops, pull-request-commits, retry-after, virtual-threads]
requires: [06-02, 06-05]
provides:
  - AdoRepositoryPort.pullRequestCommits with continuation paging and page ceiling
  - adapter-wide Retry-After gate
  - ADO completion step in EnrichmentService (ADO_COMPLETE commit lists)
affects: [06-09, 06-10]
tech-stack:
  added: []
  patterns: [run-scoped CompletableFuture map for PR de-duplication, stored closed-PR rows as the cross-run cache]
key-files:
  created: []
  modified:
    - src/main/java/com/evision/releasemgmt/ports/AdoRepositoryPort.java
    - src/main/java/com/evision/releasemgmt/adapters/ado/AdoRestAdapter.java
    - src/main/java/com/evision/releasemgmt/application/EnrichmentService.java
    - src/test/java/com/evision/releasemgmt/adapters/ado/AdoRestAdapterTest.java
    - src/test/java/com/evision/releasemgmt/application/EnrichmentServiceTest.java
key-decisions:
  - "ADO PRs of one ticket are started together, then joined; a failure of any PR keeps the whole item KEY_TAGGED and flagged (one source per item)"
  - "Cached closed-PR commits are unioned with current key-tagged commits so a later key-tagged commit is not lost"
  - "Any unexpected exception from the ADO call maps to ADO_ERROR so ADO can never produce a FAILED item"
requirements-completed: [ADO-01, ADO-04, ADO-05, INSIGHT-03]
duration: ~30 min
completed: 2026-10-12
---

# Phase 6 Plan 08: ADO commit completion Summary

With a usable org PAT, each ticket's commit list becomes the full commit list of its own-branch PRs unioned with key-tagged commits (de-duplicated by sha, author dates kept, source ADO_COMPLETE); without one it stays Jira-only, and any ADO problem leaves a flagged Jira-only item.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. pullRequestCommits | 92bcdc4 | Paging via `x-ms-continuationtoken`, `$top=100`, page ceiling to TOO_LARGE, GUID and numeric-id validation, browser commit URLs, adapter-wide Retry-After gate (observed on every response, one retry on 429, cap 30 s) |
| 2. ADO completion step | abcd554 | Credential read once per run, expired/unreadable/org-mismatch states, per-run PR de-dup via computeIfAbsent, closed-PR reuse, reason mapping |

## Verification

`mvn -P '!frontend' clean test`: 746 tests, 0 failures (727 baseline plus 19 new). The `"url"` payload field is never read by the adapter.

## Deviations from Plan

**1. [Environment] Worktree base reset.** The worktree started at 4d1d621; I ran `git reset --hard d9b6bae` after the HEAD assertions passed.

**2. [Refinement] `AdoRun` record carries credential, credential problem and the run PR map into `enrichOne`** instead of three separate parameters. The capped-commit logic moved out of the dev-status step into `capCommits`, applied last, and keeps an existing ADO reason instead of overwriting it with COMMIT_LIMIT.

**3. [Refinement] PRs whose URL does not parse** (legacy form) are skipped with no flag; if every counting PR is skipped the item stays KEY_TAGGED.

Otherwise the plan executed as written.

## Known Stubs

None.

## Threat Flags

None. T-06-32 to T-06-36 are mitigated as planned (validated path parts as encoded template variables, no payload URL fetched, token excluded from messages, Retry-After gate, page ceiling, run de-dup and closed-PR cache, unreadable key degrades to a flag).

## Self-Check: PASSED

All five modified files exist and the test suite is green; commit 92bcdc4 exists and the task 2 commit is in the branch log.
