---
phase: 14-ai-document-assist
plan: 03
subsystem: documents
tags: [readiness, ai-revision, document-snapshot, index]
requires: [14-01]
provides:
  - "DocumentReadinessService compares DocumentSnapshot.stalenessBasis()"
  - "AiRevisionService.save: the only write path for AI text"
  - "RevisionSupersededException (HTTP 409 mapping is 14-07)"
affects: [14-06, 14-07]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/AiRevisionService.java
    - src/main/java/com/evision/releasemgmt/domain/document/RevisionSupersededException.java
    - src/test/java/com/evision/releasemgmt/application/AiRevisionServiceTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/DocumentReadinessService.java
    - src/test/java/com/evision/releasemgmt/application/DocumentReadinessServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/IndexAfterCommitIT.java
    - src/test/java/com/evision/releasemgmt/application/DocumentExportServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/assistant/IndexDocumentMapperTest.java
decisions:
  - "No Clock bean is used; Instant.now() matches DocumentGenerationService"
  - "AiRevisionService has no @Transactional and no logging (caller owns the log line)"
metrics:
  tasks: 3
  tests: "1180 green (baseline 1169)"
completed: 2026-10-18
---

# Phase 14 Plan 03: Readiness basis, AiRevisionService, export/index pins Summary

Readiness now keys off the staleness basis, so an AI revision of a stale document stays stale. AiRevisionService appends revision N+1 and refuses a superseded source revision.

## Commits
- e5b50d2: readiness uses stalenessBasis() plus 4 test cases
- ebf1418: RevisionSupersededException, AiRevisionService, AiRevisionServiceTest (4), IndexAfterCommitIT AI-revision case (exactly one ReleaseIndexInvalidated, index push contains the AI text)
- Task 3 commit: export (unstamped, AI markdown and chrome) and index mapper (latest revision, no ai_* metadata) pins

## Deviations from Plan
None. Task 3 needed no production change; DocumentExportService and IndexDocumentMapper are untouched.

## Notes
- Worktree base was reset to b6893d1 as instructed (it started at 18ea6f7).
- AiRevisionServiceTest uses Mockito mocks (the repo style) rather than a hand-written fake repository.
- Full backend suite: 1180 tests, 0 failures.

## Self-Check: PASSED
