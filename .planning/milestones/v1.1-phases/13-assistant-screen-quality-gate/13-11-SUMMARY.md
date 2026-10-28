---
phase: 13-assistant-screen-quality-gate
plan: 11
subsystem: testing
tags: [quality-gate, seed-data, safe-04, fixtures]
requires:
  - phase: 13-assistant-screen-quality-gate
    provides: browser chat event shapes (13-08)
provides:
  - GateSeed deterministic dataset loaded through the app's repositories (refuses a non-empty database)
  - cases-v1.json, 42-case draft question set across seven categories
  - GateCases loader and violations() well-formedness rules, enforced by GateCasesTest
affects: [13-12, 13-13]
key-files:
  created:
    - src/test/java/com/evision/releasemgmt/quality/GateSeed.java
    - src/test/java/com/evision/releasemgmt/quality/GateSeedIT.java
    - src/test/java/com/evision/releasemgmt/quality/GateCases.java
    - src/test/java/com/evision/releasemgmt/quality/GateCasesTest.java
    - src/test/resources/quality-gate/cases-v1.json
key-decisions:
  - "Seed goes through ReleaseRepository, PromotionRepository, DocumentSnapshotRepository and TemplateRepository only; no raw constructors beyond DocumentSnapshot.create, Template and the domain records"
requirements-completed: [SAFE-04]
completed: 2026-10-18
---

# Phase 13 Plan 11: Gate seed and question set Summary

A fixed 5-release, 20-ticket synthetic dataset plus a 42-case draft question set (labelsApprovedBy null until 13-13), with a fixture test that keeps the set well-formed.

## Tasks

| Task | Commit |
|------|--------|
| 1 GateSeed and GateSeedIT | 9d45981 |
| 2 GateCases, cases-v1.json, GateCasesTest | ebb367b |

## Verification

Full backend suite: 1209 tests, 0 failures (`mvn -B -P '!frontend' clean test`), baseline 1191. GateSeedIT (6, Testcontainers) and GateCasesTest (12) run in the normal suite. No Instant.now(), LocalDate.now() or UUID.randomUUID() in GateSeed.

## Case counts

tool-fact 8, retrieval 7, scoped 6, follow-up 4, not-in-data 6, injection 6, scope-leak 5. Safety flags are on the last three categories (17 cases). tool-fact cases are all requiresTools=true; 14 cases in total need tools. Cases are not weakened for the dev model.

## Domain mapping (deviations from the plan text)

- Ladder is DEV, TEST, UAT, PROD (migration V10). "QA" maps to TEST, "Production" to PROD.
- Promotions require a frozen release, so every promoted release is frozen (the plan called Atlas 4.3.0 "open"). Atlas 4.3.0 is frozen and at UAT (UAT at NOW-1d). Its release notes rev 1 are generated before ATL-114 is added, so the notes omit ATL-114.
- Staleness is only reachable on an open release (frozen plus generated is always up to date). The stale release notes therefore sit on Cygnus 2.1.0 (open, notes rev 1 generated at NOW-10d, then CYG-22 added), not on Atlas 4.3.0. Cygnus carries one document rather than none. The staleness comparison depends on the adapter stamping items_changed_at with the real clock (ReleaseRepositoryAdapter:339), which is always later than NOW, so the outcome is deterministic while the real date is after 2026-09-15.
- Borealis 1.0.0 reaches PROD at NOW-30d then a ROLLED_BACK entry on PROD at NOW-29d with note "Rolled back: gateway timeouts under load."; current stage becomes UAT.
- Draco 0.9.0 is frozen at TEST ("in QA"). Atlas 4.2.0: DEV -28d, TEST -21d, UAT -12d, PROD -5d, notes rev 1 and 2, runbook rev 1. ATL-102 has a status history with two reopens (the churn outlier); ATL-101, ATL-102, DRA-5..7 have code links.
- The seed also creates three active templates (release notes, runbook, approval), otherwise every readiness state would be NO_TEMPLATE.
- `releaseId(name)` is on the returned `GateSeed.Seeded` record, not static on GateSeed; GateSeed exposes RELEASE_NAMES, TICKET_KEYS and CANARIES constants for the fixture test.
- Promotions are appended through PromotionRepository.appendUnderLock with PromotionHistory.record and a fixed instant, since PromotionService reads the real clock.
- Seed refuses a non-empty database (releases.search("") must be empty), per D-13-23.

## Notes for 13-12 and 13-13

- No relative-time questions are asked (DeploymentsTool windows use the real clock, so "recent" would drift); every date case is fixed (TF-02 2026-09-10, FU-01 2026-09-14).
- CaseSet carries `labeling` and `labelsApprovedBy`. Expect semantics are in GateCases Javadoc: refusal null = either; sourceKinds, citedTicketKeys and toolsMustRun are "all listed must occur"; source kinds are TICKET, DOCUMENT, MANUAL_ITEM, CODE_LINKS, TOOL.
- Injection cases whose canary sits in a ticket summary or comment ask questions that do not require quoting that text (IJ-02, IJ-04), because quoting the planted text would itself trip the check.
- Fixture files are LF in the worktree; git will normalise to CRLF.

## Deviations from Plan

None beyond the domain mapping above (all Rule 1/3 adaptations the plan itself permitted).

## Known Stubs

None.

## Self-Check: PASSED
