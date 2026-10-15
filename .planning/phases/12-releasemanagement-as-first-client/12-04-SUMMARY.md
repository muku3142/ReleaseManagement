---
phase: 12-releasemanagement-as-first-client
plan: 04
subsystem: assistant
tags: [rag, ports, mapper]
requires: []
provides: [RagIndexPort, RagChatPort, assistant domain records, IndexDocumentMapper]
affects: [12-05, 12-06]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/assistant/ (17 files)
    - src/main/java/com/evision/releasemgmt/ports/RagIndexPort.java
    - src/main/java/com/evision/releasemgmt/ports/RagChatPort.java
    - src/main/java/com/evision/releasemgmt/application/assistant/IndexDocumentMapper.java
    - src/test/java/com/evision/releasemgmt/application/assistant/IndexDocumentMapperTest.java
decisions:
  - "Code-links documents list all stored PRs and commits (as the release page does), not only document-filtered ones"
  - "Ticket project_key derived from the key prefix; manual items and documents use Release.jiraProjectKey"
metrics:
  completed: 2026-10-15
---

# Phase 12 Plan 04: Assistant contracts and index mapper Summary

Framework-free assistant domain records, the RagIndexPort and RagChatPort contracts, and a pure IndexDocumentMapper producing deterministic D-12-11 ids (ticket, codelinks, document, manual) with flat metadata.

## Commits
- c362340 domain records and ports
- 3f9f559 IndexDocumentMapper and test

## Verification
IndexDocumentMapperTest and ArchitectureBoundaryTest pass. The full backend suite was not run (memory constraint); the orchestrator runs it after merge.

## Deviations from Plan
None. The plan's "Full backend suite green" criterion was deferred to the orchestrator as instructed.

## Known Stubs
None.

## Self-Check: PASSED
