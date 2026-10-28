---
phase: quick-261026-kyg
plan: 01
subsystem: roadmap
tags: [roadmap, product, jpql, flyway, phase-17.1]
requirements: [QUICK-261026-kyg]
key-files:
  created:
    - src/main/resources/db/migration/V20__roadmap_product_required.sql
  modified:
    - src/main/java/com/evision/releasemgmt/application/RoadmapImportService.java
    - src/main/java/com/evision/releasemgmt/application/RoadmapService.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/RoadmapItemJpaRepository.java
    - frontend/src/roadmap/AddManualItemDialog.tsx
    - frontend/src/roadmap/RoadmapAddResult.tsx
    - frontend/src/api/reasonMessages.ts
decisions:
  - "Imports derive the product from the key's current Jira project (moved issues use the new project); unowned -> ROADMAP_NO_PRODUCT_FOR_PROJECT, archived -> ROADMAP_PRODUCT_ARCHIVED, per key"
  - "Roadmap list/CSV filter in two bound-parameter JPQL queries (all products vs IN list) because an empty IN () is not portable"
completed: 2026-10-26
---

# Quick 261026-kyg: Roadmap product required, list filtered in the database

Every roadmap item now has a product (DB NOT NULL via V20, `@NotNull` on manual add and details edit); Jira and release imports assign the product owning the key's Jira project and reject other keys per key; the list and CSV filter in SQL.

## Commits

- 36ef18f feat(roadmap): derive imported items' product from their Jira project
- 55aeaa2 feat(roadmap): require a product on every item and filter the list in SQL
- 5f07b1b feat(roadmap-ui): require a product and show per-key project rejections
- (next) test(roadmap): pin the V19 migration test to V19

## What changed

- `RoadmapAddResult` gained `rejected: [{jiraKey, reason}]`; `RoadmapItem.fromJira` takes a productId; per-call project->owner cache means one lookup per project.
- `RoadmapRepository.findMatching(filter)` replaces the Java-side `RoadmapFilter.matches`; ranking stays in `RoadmapRanking`.
- Frontend: no "No product" option, manual submit disabled until a product is chosen, rejected keys rendered via `messageForRejectedKey`.

## Deviations from Plan

**1. [Rule 1 - Bug] V19BriefingsMigrationIT asserted the latest version was 19**
- Found during: full backend suite. V20 broke it.
- Fix: migrate and assert to target 19, so the test stays about V19. Separate commit.

**2. [Rule 3 - Blocking] ReleaseDetailRoute test fixture lacked `rejected`**
- Found during: full vitest. The result view destructures `rejected`; the fixture for `/from-release` omitted it. Added `rejected: []`, folded into the frontend commit (amended before any push).

Note: the plan's `RoadmapServiceTest` tests that used a null product were adjusted to use a real product id, and the "clears product on null" test was dropped since null is no longer valid.

## Verification

- Backend full suite: 1821 tests; the only failure (V19BriefingsMigrationIT) was fixed and re-run green. Roadmap* tests and ITs: 64 green.
- Frontend: `npx tsc -b` clean; full vitest 850/850 (74 files).
- `grep filter::matches src/main/java`: none. V20 contains one `SET NOT NULL`.

## Known Stubs

None.

## Self-Check: PASSED
