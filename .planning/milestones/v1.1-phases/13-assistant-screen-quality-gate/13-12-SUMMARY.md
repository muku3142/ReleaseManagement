---
phase: 13-assistant-screen-quality-gate
plan: 12
subsystem: testing
tags: [quality-gate, safe-04, maven-profile, docs]
requires: [13-10, 13-11]
provides: [GateEvaluator, QualityGateIT, quality-gate profile, docs/quality-gate.md, docs/assistant-security.md]
affects: [13-13, 13-14]
key-files:
  created:
    - src/test/java/com/evision/releasemgmt/quality/GateEvaluator.java
    - src/test/java/com/evision/releasemgmt/quality/GateEvaluatorTest.java
    - src/test/java/com/evision/releasemgmt/quality/QualityGateIT.java
    - docs/quality-gate.md
    - docs/assistant-security.md
  modified:
    - pom.xml
requirements-completed: [SAFE-04]
completed: 2026-10-18
---

# Phase 13 Plan 12: Quality gate harness Summary

Pure scoring (GateEvaluator, unit-tested), an opt-in QualityGateIT driving POST /api/assistant/chat over a seeded scratch instance, the `quality-gate` Maven profile, the production runbook and the injection threat model.

## Commits

| Task | Commit |
|------|--------|
| 1 GateEvaluator + test | f665dbf |
| 2 QualityGateIT + pom profile | a7c152a |
| 3 docs | 29aedb4 |

## What was built

- `GateEvaluator.evaluate(case, stream, scopeId)` yields pass plus failed check names (`outcome:X`, `refusal`, `mustContain:`, `mustNotContain:`, `citedTicketKeys:`, `sourceKinds:`, `toolsMustRun:`, `scopeLeak`). `verdict(results, devWaiver)` gives PASS/FAIL/DEV_WAIVER_NOT_SHIPPABLE plus `barMet`, safety tally, quality runs/rate, per-category tallies and tool-dependent case ids. "Quality" is the non-safety cases; safety must be all-runs-pass.
- `QualityGateIT` (`@Tag("quality-gate")`, RANDOM_PORT, fixed `Clock` bean): Testcontainers Postgres if Docker is available, else `-Dgate.db-url`; scratch rag client `rm-quality-gate-<epoch>` registered with `GATE_RAG_ADMIN_KEY`, never `RM_ASSISTANT_API_KEY`; refuses a non-empty DB; seeds, waits for all four rm-* collections to index, runs each case `gate.runs` times, writes `target/quality-gate-report.json`; teardown deletes releases, waits for indexed 0, revokes the client, records COMPLETE/INCOMPLETE.
- pom: property `excludedGroups=quality-gate` (same shape as rag-service golden) and profile `quality-gate` (`groups=quality-gate`, `excludedGroups=none`). 14-14 can add its profile beside it.

## Verification

- GateEvaluatorTest green (7 tests).
- `mvn -P '!frontend,quality-gate' test -Dtest=QualityGateIT` with no inputs: BUILD SUCCESS, class skipped (0 tests run). The plan's grep strings do not appear because a `@BeforeAll` assumption reports "Tests run: 0", so I asserted the property rather than the text.
- Full `mvn -B -P '!frontend' clean test`: 1045 run, 0 failures, 22 errors, all 22 classes failing with "ContainerFetch Can't get Docker image pgvector/pgvector:0.8.7-pg18-trixie" (this machine's Docker only has alpine; I was told not to touch containers). No error outside Docker-image fetch. QualityGateIT did not run. The 1220 baseline could not be reproduced here; rerun where the pgvector image is present.
- Docs grep checks pass; no key-like values (the one regex hit is a planning path).

## Deviations from Plan

- Profile activation is documented as `-P '!frontend,quality-gate'` (one -P list), since the plan's two `-P` flags are not guaranteed to combine.
- The IT also asserts `barMet`, in addition to `verdict != FAIL`; otherwise a waived run could never fail. The report is written before either assertion.
- The IT sets a per-request 6 minute client timeout: the fixed clock disables the app's stream deadline, so a hung rag-service would otherwise block.
- Teardown deletes every release in the (asserted-empty-at-start) scratch DB rather than only seeded ids.
- The plan's `@AfterAll` revoke is an additional safety net if the Spring context fails to load.

## Not verified (by instruction)

The live gate, the Spring wiring of the `@DynamicPropertySource` suppliers after `@BeforeAll`, and the rag-service admin/collection calls were not exercised; 13-13 is the first real run.

## Known Stubs

None.

## Self-Check: PASSED
