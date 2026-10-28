---
phase: 12-releasemanagement-as-first-client
plan: 09
subsystem: testing
tags: [archunit, assistant, read-only, asst-04]
requires: [12-03, 12-05, 12-07]
provides:
  - build-enforced read-only and isolation rules for the assistant, proven non-vacuous
key-files:
  modified:
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
  created:
    - src/test/java/com/evision/releasemgmt/archfixture/MutatingTool.java
    - src/test/java/com/evision/releasemgmt/archfixture/JiraReachingTool.java
    - src/test/java/com/evision/releasemgmt/archfixture/IndexWritingChat.java
metrics:
  tasks: 1
  completed: 2026-10-15
---

# Phase 12 Plan 09: Assistant ArchUnit rules Summary

ArchUnit rules fail the build if an AssistantTool calls a non-allowlisted service method or depends on a non-allowlisted type, or if AssistantService can reach the index writers; fixtures prove each rule rejects a violation.

## What was built
- Rule factories `toolsCallOnlyAllowlistedReadMethods`, `toolsDependOnlyOnAllowedTypes`, `chatPathCannotWriteTheIndex(predicate)`, each run on the production import and on a fixture import.
- Real-class tests, `toolRulesApplyToTheRealTools` (exactly ReleasesTool, TicketsTool, DeploymentsTool, DashboardTool, DocumentsTool, all in the tools package), `toolRulesRejectViolations`, `toolsAreNotTransactional`, `ragClientTypesStayInTheRagAdapter`, `ragChatPortExposesNoMutation`.
- `applicationDoesNotDependOnAdapters` now also forbids `..adapters.rag..`.
- Index-writer rule matches by simple name (RagIndexPort, IndexSyncer, IndexSweep, IndexInvalidationListener, IndexDocumentMapper), so it does not need 12-08's classes to exist.

## Verification
- ArchitectureBoundaryTest: 17 tests pass. Full backend suite: 1028 tests, 0 failures (baseline 1020 + 8 new).
- Mutation check: temporarily changed `releaseService.get(id)` to `freeze(id, "x")` in ReleasesTool; `toolsCallOnlyAllowlistedReadMethodsOnTheRealClasses` failed naming ReleasesTool.java:74. Reverted.

## Deviations from Plan
- The tool type allowlist needed no widening beyond the plan's list. Calls on records and nested classes owned by application services are treated as data accessors and skipped by the method rule; the type rule still governs them.
- Added `ReleaseService.freeze` fixture via a null field, never invoked.

## Self-Check: PASSED
Commit 78dddb3 exists; all four files present.
