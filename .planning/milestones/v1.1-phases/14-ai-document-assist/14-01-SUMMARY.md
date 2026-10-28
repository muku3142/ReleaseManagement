---
phase: 14-ai-document-assist
plan: 01
subsystem: documents
tags: [flyway, jpa, document-snapshot, ai-provenance]
requires: []
provides:
  - "document_snapshot AI provenance columns (V12) with all-or-none CHECK"
  - "AiMode, AiAssist, DocumentSnapshot.createAiRevision / aiAssisted() / stalenessBasis()"
  - "aiAssisted, aiMode, aiInstruction on DocumentResponse and DocumentRevisionResponse"
affects: [14-03]
key-files:
  created:
    - src/main/resources/db/migration/V12__document_snapshot_ai_assist.sql
    - src/main/java/com/evision/releasemgmt/domain/document/AiMode.java
    - src/main/java/com/evision/releasemgmt/domain/document/AiAssist.java
    - src/test/java/com/evision/releasemgmt/domain/document/DocumentSnapshotTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/DocumentSnapshotEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/web/DocumentController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/DocumentResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/DocumentRevisionResponse.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotPersistenceIT.java
    - src/test/java/com/evision/releasemgmt/adapters/web/DocumentControllerTest.java
decisions:
  - "aiAssisted is derived from ai_mode; no ai_assisted column"
  - "Migration is V12 (no V12 existed)"
metrics:
  tasks: 3
  tests: "1092 green (baseline 1078)"
completed: 2026-10-18
---

# Phase 14 Plan 01: AI provenance on document snapshots Summary

A stored document revision can now record AI mode, instruction, source revision and the carried staleness basis. The database rejects partial provenance, and the document API exposes the three AI fields.

## Commits
- 225e054: V12 migration, AiMode/AiAssist, DocumentSnapshot, entity/adapter mapping, call-site updates, DocumentSnapshotTest
- 58bd73a: DocumentSnapshotPersistenceIT cases (round trip with logo copy, source unchanged, chain basis, CHECK rejection, history and latest-for-all)
- Task 3 commit: DTO fields, DocumentController mapping, controller tests (see git log)

## Deviations from Plan
None. DocumentSnapshotTest was added to Task 1 files as the plan instructed. The port gained no methods.

## Notes
- DocumentSnapshot.toString() prints markdownChars only (SAFE-03); AiAssist prints instructionChars only.
- The worktree base was reset to 7063e88 as instructed (it started at 18ea6f7).
- Full backend suite: 1092 tests, 0 failures.

## Self-Check: PASSED
