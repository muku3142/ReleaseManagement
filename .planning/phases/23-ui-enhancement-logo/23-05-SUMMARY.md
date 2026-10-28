---
phase: 23-ui-enhancement-logo
plan: 05
subsystem: frontend
tags: [tanstack-table-v9, data-table, a11y, shadcn]
requires: ["23-03"]
provides:
  - "dataTableFeatures / DataTableFeatures: the single v9 tableFeatures extension point (sorting, column filtering, faceting, visibility)"
  - "DataTable<TData> with sort, faceted filters, column choice, sticky header, optional sticky first column, loading/empty/filtered-empty states and a polite count"
  - "DataTableColumnHeader (sortable header button), DataTableViewOptions, DataTableToolbar, DataTableFacetedFilter"
affects: [23-09, 23.1]
tech-stack:
  added: []
  patterns:
    - "v9 only: useTable + tableFeatures + create*RowModel + table.FlexRender; columns via createColumnHelper<DataTableFeatures, Row>().columns([...])"
    - "column meta typed through the columnMeta slot (metaHelper<DataTableColumnMeta>): label, headerClassName, cellClassName"
    - "faceted columns set filterFn: 'arrHas'"
key-files:
  created:
    - frontend/src/components/data-table/features.ts
    - frontend/src/components/data-table/DataTable.tsx
    - frontend/src/components/data-table/DataTableColumnHeader.tsx
    - frontend/src/components/data-table/DataTableViewOptions.tsx
    - frontend/src/components/data-table/DataTableToolbar.tsx
    - frontend/src/components/data-table/DataTableFacetedFilter.tsx
    - frontend/tests/components/DataTable.test.tsx
  modified: []
decisions:
  - "Faceted columns use v9 filterFn 'arrHas' (scalar value equals any selected value). v9 arrIncludesSome only matches array cell values."
  - "Sort, filter and visibility state lives in the table's own v9 atoms, not mirrored React state. It is still in memory, per mount, and never persisted."
  - "sortDescFirst: false at table level, so every column cycles ascending, descending, unsorted (v9 would otherwise start numeric columns descending)."
metrics:
  duration: "~25 min"
  completed: 2026-10-26
  tasks: 2
  files: 7
---

# Phase 23 Plan 05: Shared DataTable Summary

There is now one shared DataTable, built on TanStack Table v9. Columns sort through a header button that cycles ascending, descending, unsorted, and the `th` carries `aria-sort`. The toolbar has faceted filters with counts, "Clear filter" and "Clear filters", plus a "Columns" menu. The header stays put inside a `max-h-[70vh]` region that is labelled by the caption and can be scrolled with the keyboard. The first column can optionally stick. Loading, empty and filtered-to-none states are in place, and a polite live region reads "{n} {noun} shown."

## Tasks

| Task | Commits | Notes |
|------|---------|-------|
| 1 Features, core, sortable header, column choice (TDD) | 99e3771 (RED), 24c3e08 (GREEN) | 9 tests |
| 2 Toolbar, faceted filters, filtered-empty, live count (TDD) | bcd64fa (RED), 1b9d7d7 (GREEN) | 7 tests |

## Contract for 23-09 and 23.1

```ts
const helper = createColumnHelper<DataTableFeatures, Row>();
const columns = helper.columns([
  helper.accessor('name', { header: ({ column }) => <DataTableColumnHeader column={column} title="Name" />, enableHiding: false }),
  helper.accessor('status', { header: 'Status', filterFn: 'arrHas', meta: { label: 'Status' } }),
]);
<DataTable columns={columns} data={rows} getRowId={(r) => r.id} caption="…" noun="releases"
  facetedFilters={[{ columnId: 'status', title: 'Status', options: [...] }]} toolbar={…} emptyState={…} isLoading={…} stickyFirstColumn />
```

`meta.label` is the name shown in the Columns menu (falls back to the column id). `meta.headerClassName` and `meta.cellClassName` are for right-aligned numeric columns.

## Verification

- `npx tsc -b`: passes.
- `npx vitest run tests/components/DataTable.test.tsx`: 16/16 pass.
- `npx vitest run` (full): 77 files, 892 tests. 891 passed; 1 failure was the known flaky RoadmapRoute 'opens the detail row named by the item param'. Running that file alone failed twice and then passed (10/10). This plan touches no roadmap code.
- `npm run build`: succeeds. The >500 kB chunk warning was already there.
- Grep gates: no `useReactTable` / `getCoreRowModel` / `useLegacyTable` / `dangerouslySetInnerHTML` in `src/components/data-table`; `useTable(` and `tableFeatures(` are present, as are "match these filters." in DataTable and "Clear filters" in DataTableToolbar.

## Deviations from Plan

**1. [Rule 1 - Bug] `arrHas` replaces `arrIncludesSome` as the facet filter function.**
- Found during: Task 2 GREEN
- Issue: v9's `filterFn_arrIncludesSome` returns false for any cell value that is not an array, so selecting a facet value on a string column hid every row.
- Fix: registered `filterFn_arrHas` (scalar equality against any selected value) under the key `arrHas`, and the test fixture uses `filterFn: 'arrHas'`. `arrIncludesSome` is not registered, so nobody can pick the broken option by mistake.
- Files: features.ts, DataTable.test.tsx. Commit 1b9d7d7.

**2. [Discretion] The table owns its own state.** The plan said to keep local `useState` and pass it through `state`/`on*Change`. v9's `useTable` already holds per-mount atoms and re-renders on changes, so mirroring that state would only duplicate it. The behaviour is the same: in memory, per mount, no persistence. Toolbar code reads `table.state.columnFilters`; v9 has no `getState()` on the React table.

**3. [Discretion] The first-column header uses a single shadow.** It is `shadow-[inset_-1px_-1px_0_var(--border)]`. Two shadow utilities, one for the bottom rule and one for the column rule, would override each other through tailwind-merge.

**4. Verify commands were run from this worktree's `frontend/`**, not from the main checkout path in the plan.

## Known Stubs

None.

## Threat Flags

None. T-23-17: every cell, label and count is rendered as React children through FlexRender or JSX, and the `dangerouslySetInnerHTML` grep gate is clean.

## Self-Check: PASSED

- All 7 created files exist.
- Commits 99e3771, 24c3e08, bcd64fa and 1b9d7d7 are on the branch.
