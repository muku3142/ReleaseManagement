---
phase: 17-rice-roadmap
plan: 07
subsystem: release-packages-link
tags: [release, validation, assistant]
requires: ["17-01"]
provides:
  - "PackagesUrl.normalize host allow-list (https; *.sharepoint.com, onedrive.live.com, 1drv.ms)"
  - "PUT /api/releases/{id}/packages-url, ReleaseResponse.packagesUrl, 400 RELEASE_PACKAGES_URL_INVALID"
  - "ReleaseDetail.packagesUrl in the existing releases tool; release:<id> index document"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/release/PackagesUrl.java
    - src/main/java/com/evision/releasemgmt/domain/release/InvalidPackagesUrlException.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PackagesUrlRequest.java
    - src/test/java/com/evision/releasemgmt/domain/release/PackagesUrlTest.java
  modified:
    - ReleaseService, ReleaseController, ReleaseResponse, GlobalExceptionHandler, ReleasesTool, IndexDocumentMapper and their tests
metrics:
  completed: 2026-10-25
---

# Phase 17 Plan 07: Packages link server side Summary

Packages link is validated on the parsed URI host, settable/clearable in any status over REST, and the assistant reads it from the existing `releases` tool and an indexed `release:<id>` document (no seventh tool).

## Tasks

| Task | Commit |
|------|--------|
| 1. PackagesUrl validator | e175a1c |
| 2. Service, endpoint, response field, 400 mapping | 783b21b |
| 3. Assistant tool field and index document | see git log (feat(17-07): let the assistant answer...) |

## Decisions

- The new `releases` tool description ("... current stage and packages link.") fits the 1900-char cap; ToolDefinitionsTest passes with 6 tools.

## Deviations from Plan

Task 1 had no separate RED commit: the test cannot compile without the exception class, so test and implementation were committed together after a green run.

## Verification

PackagesUrlTest and ReleaseControllerTest (71) pass after `clean`; ReleasesToolTest, IndexDocumentMapperTest, IndexSyncerTest, ToolDefinitionsTest, ArchitectureBoundaryTest (20) pass. No render/template code references the link.

## Self-Check: PASSED
