---
phase: 06-azure-devops-enrichment
plan: 06
subsystem: templates
tags: [mustache, view-model, placeholder-catalog, react]
requires:
  - phase: 06-azure-devops-enrichment
    provides: CodeLinks.documentCommits/documentPullRequests (06-01), ReleaseItem.codeLinks (06-03)
provides:
  - Templates list a LINKED ticket's commits and own PRs via {{#commits}} / {{#pullRequests}}
  - Sample validation view carries linked code on one ticket and none on another
  - Accurate placeholder catalog descriptions; "Always empty until Phase 6" tag removed
affects: [document generation, template validation, template editor drawer]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/SampleReleaseView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/CommitView.java
    - src/main/java/com/evision/releasemgmt/domain/template/view/PullRequestView.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java
    - frontend/src/components/PlaceholderDrawer.tsx
key-decisions:
  - "Assembler reads CodeLinks.documentCommits()/documentPullRequests() so documents and the staleness fingerprint share one LINKED-only rule"
requirements-completed: [ADO-01, ADO-02]
duration: 15min
completed: 2026-10-12
---

# Phase 6 Plan 06: Template Code Links Summary

ReleaseViewAssembler now fills commit and pull-request views from stored CodeLinks (LINKED items only), so NO_LINK, FAILED and NOT_ATTEMPTED tickets render as no linked code and generation never fails on ADO state.

## Tasks

1. Assembler and sample view - commit 47bd6ad. Commits use the 7-char short sha, null author/title/status/url become empty strings, PRs are the ticket's own only. Sample ticket CNV-2901 now carries a commit and PR; CNV-2896 stays the no-linked-code case.
2. Catalog and drawer - commit 3134848. Removed the stale tag from all 10 entries, rewrote descriptions per plan (including the key-tagged fallback without a token, status "such as OPEN or MERGED."), dropped the amber styling branch in PlaceholderDrawer.

## Verification

- Backend: ReleaseViewAssemblerTest (14), RenderServiceTest (11), TemplateServiceTest (19), MustacheFlexmarkRenderAdapterTest (13), GenerateThenExportSeamTest (7), PlaceholderCatalogTest (10) all green. Full suite not run (parallel plan 06-05 in flight).
- Frontend: PlaceholderDrawer.test.tsx 12 passed; `npx tsc -b` clean.
- `grep "until Phase 6" src/main frontend/src` prints nothing.

## Deviations from Plan

- The render-sample-view test lives in RenderServiceTest (where SampleReleaseView is already rendered) rather than MustacheFlexmarkRenderAdapterTest, as the plan permitted. The adapter test needed no change.
- The worktree had no frontend/node_modules; a gitignored symlink to the main checkout's node_modules was created to run vitest/tsc (not committed).
- Worktree base was corrected with `git reset --hard 2ad0081` per the startup check.

## Known Stubs

None.

## Threat Flags

None. Values flow through escaped `{{var}}` interpolation as per T-06-26.

## Self-Check: PASSED
