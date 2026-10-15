---
phase: 12-releasemanagement-as-first-client
plan: 06
subsystem: assistant-indexing
tags: [rag, index, scheduling]
requires: [12-01, 12-04]
provides: [IndexSyncer, IndexSweep]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/assistant/IndexSyncer.java
    - src/main/java/com/evision/releasemgmt/application/assistant/IndexSweep.java
    - src/test/java/com/evision/releasemgmt/application/assistant/IndexSyncerTest.java
    - src/test/java/com/evision/releasemgmt/application/assistant/IndexSweepTest.java
decisions:
  - "Recorded refs are added before the push, so a failed PUT is still deleted later if the document is removed"
  - "Log lines carry collection enum name, id and status only; exception messages are never logged"
metrics:
  tasks: 2
  completed: 2026-10-15
---

# Phase 12 Plan 06: IndexSyncer and IndexSweep Summary

Per-release index synchroniser (one ReentrantLock, in-memory recorded ids, quiet failures) plus a fixedDelay scheduled sweep that ensures collections, re-pushes every release and cleans up recorded releases that no longer exist.

## Commits
- 54dddd2 IndexSyncer + test
- 345957c IndexSweep + test

## Deviations from Plan
- Per environment instructions, only `IndexSyncerTest` and `IndexSweepTest` were run (10 tests pass); the full backend suite is left to the orchestrator after merge.
- Log assertion checks the collection enum name (`TICKETS`), which is what the log line carries, not the wire name `rm-tickets`.

## Known Stubs
None.

## Self-Check: PASSED
