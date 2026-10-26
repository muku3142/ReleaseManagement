---
phase: 12-releasemanagement-as-first-client
plan: 08
subsystem: assistant-index-push
tags: [after-commit, transactional-event-listener, virtual-threads, rag-index]
requires: [12-02, 12-05, 12-06, 12-07]
provides: [post-commit coalesced index push, switch-off proof]
affects: [ReleaseRepositoryAdapter, DocumentSnapshotRepositoryAdapter]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/assistant/IndexInvalidationListener.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/IndexAfterCommitIT.java
    - src/test/java/com/evision/releasemgmt/AssistantSwitchOffIT.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotRepositoryAdapter.java
decisions:
  - Listener clears the per-release queued flag only after acquiring a listener-wide turn lock, so events during a running sync collapse into one follow-up.
metrics:
  tasks: 2
  completed: 2026-10-15
---

# Phase 12 Plan 08: Post-commit index push Summary

Persistence adapters publish `ReleaseIndexInvalidated` inside their transactions (10 `ReleaseRepositoryAdapter` mutations plus `DocumentSnapshotRepositoryAdapter.create`). A switch-gated `IndexInvalidationListener` handles it AFTER_COMMIT and only enqueues a coalesced sync on a private virtual-thread executor.

## Commits
- 943bfcc feat(12-08): publish from adapters, add listener
- test commit: feat/test for IndexAfterCommitIT, AssistantSwitchOffIT and the listener coalescing fix

## Verification
Full backend suite: 1028 tests, 0 failures (`mvn -P '!frontend' clean test`). The ITs prove: one event per mutating method and none for reads; push only after commit, on a thread with no transaction, different from the caller; rollback drops it; a blocked port does not slow mutations; a 503 port leaves the mutation intact; 20 events during a running sync yield exactly 2 pushes; switch off constructs no assistant bean and v1 mutations work.

## Deviations from Plan
**[Rule 1 - Bug] Coalescing did not hold with the plan's handler.** Clearing the queued flag at task start let tasks pile up behind the syncer lock (3 syncs observed, limit 2). Fix: a `ReentrantLock turn` in the listener; the flag is cleared only once a task owns the turn. Files: IndexInvalidationListener.java. Behaviour is otherwise as specified, with an added comment explaining the lock.

## Known Stubs
None.

## Self-Check: PASSED
