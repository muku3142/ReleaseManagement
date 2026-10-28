---
phase: 23-ui-enhancement-logo
plan: 09
subsystem: frontend
tags: [ui, tokens, data-table, page-header, badge, skeleton, empty]
requires: ["23-03", "23-05", "23-06"]
provides:
  - "PageHeader (h1 + description + actions) and PageContainer (wide/narrow) in src/components/layout"
  - "ReadinessPill on Badge (success/warning/neutral) with 'Out of date' app-wide"
  - "/releases as the 23.1 reference page on the shared DataTable"
affects: [DashboardRoute, DocumentsSection, DocumentViewerRoute (ReadinessPill consumers)]
tech-stack:
  added: []
  patterns:
    - "Route columns via createColumnHelper<DataTableFeatures, Row>() inside useMemo keyed on the data the cells read"
    - "Badge asChild wrapping a router Link so the Badge is the anchor"
key-files:
  created:
    - frontend/src/components/layout/PageHeader.tsx
    - frontend/src/components/layout/PageContainer.tsx
    - frontend/tests/components/PageHeader.test.tsx
  modified:
    - frontend/src/components/ReadinessPill.tsx
    - frontend/src/routes/ReleasesRoute.tsx
    - frontend/tests/components/ReadinessPill.test.tsx
    - frontend/tests/components/DashboardRoute.test.tsx
    - frontend/tests/components/ReleasesRoute.test.tsx
decisions:
  - "Status facet uses filterFn 'arrHas' (not arrIncludesSome as the plan text said), per the 23-05 contract"
  - "Jira project and Last refreshed sort as text on `value ?? ''`; ISO timestamps sort correctly lexically and nulls never reach a comparator"
  - "The empty state is suppressed while the error block shows, so a failed load never also reads 'No releases yet'"
metrics:
  duration: ~25 min
  completed: 2026-10-27
---

# Phase 23 Plan 09: /releases reference page Summary

/releases now uses PageContainer and PageHeader with token classes only. The list is the shared DataTable: the Release column links to the release and stays put when you scroll sideways, and there are a Status facet, column choice, skeleton rows and an Empty block. ReadinessPill is now a Badge everywhere it appears, and STALE reads "Out of date".

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 PageHeader, PageContainer, ReadinessPill on Badge | 2cdc91b | Tests were updated first and failed: 6 failures in RED; all pass now. |
| 2 /releases on PageHeader + DataTable | b244453 | Tests were updated first and failed: 10 of 14 in RED; 14/14 pass now. |

## Deliberate assertion changes

- `tests/components/ReadinessPill.test.tsx`: "Stale" became "Out of date". The colour test is now `upToDateAndStaleUseDistinctVariants`, which checks for `text-success` vs `text-warning`. New assertions cover the aria-hidden icons, `bg-muted` on the neutral states, and `rounded-full` + `hover:underline` on every pill.
- `tests/components/DashboardRoute.test.tsx:260` (the plan said :197): `'Stale'` became `'Out of date'`.
- `tests/components/ReleasesRoute.test.tsx`: Name and Version are merged into one "Release" column, "Project" is now "Jira project", and the readiness cell indices moved from 7-9 to 6-8. The products `title` now sits on the inner span, not the `td`, because DataTable owns the cell. New tests cover the h1 and description, the Release link classes, the Frozen neutral badge with its lock icon, numeric sorting on Items, the Status facet set to Frozen, the 300 ms debounced query fetch, the skeleton loading state, the empty and no-match copy, and the error block whose Try again button refetches. The readiness-failure test now also checks that the rest of the row still renders.

## Verification

- `npx tsc -b`: clean.
- `npx vitest run` (full): 86 files, 992 tests, all passed. The known flaky RoadmapRoute test passed this time.
- `npm run build`: succeeds. The >500 kB chunk warning was already there before this plan.
- Grep gates: ReadinessPill has no `'Stale'` and no raw gray/green/amber/blue classes. ReleasesRoute has no raw gray/blue/red/green/amber classes, and it contains `<DataTable`, the description string and `T-05-60`.

## Deviations from Plan

**1. [Rule 1 - Bug] Status facet filterFn is `arrHas`, not `arrIncludesSome`.** v9's `arrIncludesSome` hides every row when the cell value is a scalar (see 23-05 SUMMARY), and only `arrHas` is registered. Commit b244453.

**2. [Rule 1 - Bug] The empty state is hidden while the error shows.** DataTable falls back to its empty state when `data` is `[]`. Without this guard, a failed load showed "No releases yet" under the alert. The old page never showed both. Commit b244453.

**3. [Rule 2] Added `tests/components/PageHeader.test.tsx`.** It covers the PageHeader/PageContainer behaviour the plan lists: one h1, an optional description, the actions slot and the wide/narrow widths. Commit 2cdc91b.

## Known Stubs

None.

## Self-Check: PASSED

- All files listed under key-files exist; commits 2cdc91b and b244453 are on the branch.
