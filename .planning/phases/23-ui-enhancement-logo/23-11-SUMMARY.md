---
phase: 23-ui-enhancement-logo
plan: 11
subsystem: frontend
tags: [motion, hover, reduced-motion, data-table, brand]
requires: ["23-02", "23-03", "23-05", "23-06"]
provides:
  - "motion tokens --motion-fast/base/slow, --ease-out"
  - "utilities animate-page-in, animate-row-in, animate-status-pulse, animate-mark-link, animate-mark-node, link-underline, count-up"
  - "useCountUp (+ COUNT_UP_MS), usePulseOnChange, useEntranceStagger"
  - "DataTable onRowClick, Card interactive, ProductOpsMark/ProductOpsLogo animate"
affects: ["23-08", "23-12"]
tech-stack:
  added: []
  patterns:
    - "Every keyframe utility is applied through motion-safe: or gated by usePrefersReducedMotion (the global reduced-motion rule does not reset animation-delay)"
key-files:
  created:
    - frontend/src/hooks/useCountUp.ts
    - frontend/src/hooks/usePulseOnChange.ts
    - frontend/src/hooks/useEntranceStagger.ts
    - frontend/tests/hooks/renderHook.tsx
    - frontend/tests/hooks/useCountUp.test.tsx
    - frontend/tests/hooks/usePulseOnChange.test.tsx
    - frontend/tests/hooks/useEntranceStagger.test.tsx
  modified:
    - frontend/src/index.css
    - frontend/src/components/ui/card.tsx
    - frontend/src/components/ui/button.tsx
    - frontend/src/components/data-table/DataTable.tsx
    - frontend/src/components/brand/ProductOpsMark.tsx
    - frontend/src/components/brand/ProductOpsLogo.tsx
    - frontend/tests/components/DataTable.test.tsx
    - frontend/tests/components/ui/ownedEdits.test.tsx
    - frontend/tests/components/ProductOpsLogo.test.tsx
decisions:
  - "Motion tokens live in a plain :root block, not @theme, so Tailwind never prunes them"
  - "The mark's single link path is split into four paths in all modes (static render identical) so each can draw with pathLength=1"
  - "Row clicks ignore events from a, button, input and [role=checkbox]; the row's link stays the keyboard path"
metrics:
  completed: 2026-10-27
  tasks: 3
  files: 16
---

# Phase 23 Plan 11: Motion and hover layer Summary

Motion tokens, seven CSS utilities, three tested reduced-motion-aware hooks, and the D-23-14/15 hover, press, stagger and logo connect-in rules on the shared DataTable, Card, Button and ProductOpsMark/Logo.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Motion tokens, keyframe utilities, useCountUp/usePulseOnChange/useEntranceStagger | 31a2d5e |
| 2 | DataTable row wash, accent bar, first-render stagger, onRowClick; Card interactive; Button press and link underline | 9bf1161 |
| 3 | ProductOpsMark/ProductOpsLogo `animate` connect-in | 62528bd |

## What was built

- `index.css`: `--motion-fast` 120ms, `--motion-base` 200ms, `--motion-slow` 600ms, `--ease-out` cubic-bezier(0.22, 1, 0.36, 1); keyframes `page-in`, `row-in`, `status-pulse`, `mark-link`, `mark-node`; `@utility` blocks for the five animations plus `link-underline` and `count-up`. Nothing loops; the global reduced-motion rule is untouched.
- Hooks match the contracts in the plan: `useCountUp(value, durationMs = COUNT_UP_MS)` counts once (rAF, cubic ease-out) the first time value is a positive finite number; `usePulseOnChange` uses the previous-value-in-state pattern; `useEntranceStagger` returns `motion-safe:animate-row-in` with 30ms steps capped at 300ms on the first ready render only. All three return their no-motion result under reduced motion.
- DataTable: rows `hover:bg-primary/5` with a `--motion-fast` colour transition; the sticky first cell's hover background is the opaque `color-mix(in oklab, var(--primary) 5%, var(--card))`; with `onRowClick`, rows get `cursor-pointer` and the first cell a 2px primary inset bar (combined with the border shadow when sticky). Skeleton, empty and filtered-empty rows never stagger.
- Card `interactive`: `data-interactive`, lift (motion-safe), `hover:shadow-md`, `hover:ring-primary/40`. Button: `motion-safe:active:not-aria-[haspopup]:scale-[0.98]` replaces `translate-y-px`; link variant is `text-primary link-underline`.
- ProductOpsMark `animate`: four `pathLength={1}` link paths draw (300ms), then the five circles pop in (300ms delay). ProductOpsLogo passes `animate={animate}` to both variants. `frontend/public` untouched.

## Deliberate assertion changes

- `DataTable.test.tsx` 'pins the first column and bands rows on hover': `hover:bg-muted` -> `hover:bg-primary/5`, plus the opaque color-mix sticky hover.
- `ProductOpsLogo.test.tsx` first ProductOpsMark test: the single combined link `d` became four `d` values, with the stroke attributes asserted on the wrapping `<g>`.

## Deviations from Plan

**1. [Rule 3 - Blocking] Local renderHook helper instead of @testing-library/react**
- **Found during:** Task 1
- **Issue:** the plan says to use `renderHook` from `@testing-library/react`, which is not a project dependency.
- **Fix:** added `frontend/tests/hooks/renderHook.tsx` (createRoot + act, ~20 lines) used by the three hook tests; no new dependency.
- **Commit:** 31a2d5e

**2. Plan wording conflict on the static mark**
- The behaviour list says both "without animate, four link paths" and "existing three ProductOpsMark tests still pass" (one asserted a single combined path). Followed the action (always four paths, visually identical: same strokes and one group opacity) and updated that one assertion, listed above.

## Verification

- `npx tsc -b`: clean.
- `npx vitest run`: 85 files, 979 tests passed (was 85/975 after Task 2; the flaky RoadmapRoute case did not trip).
- `npm run build`: succeeded (pre-existing >500 kB chunk warning only).
- Acceptance greps: all tokens and seven `@utility` names present; no `infinite` animation; all three hooks import `usePrefersReducedMotion`; no diff under `frontend/public`.

## Threat model

T-23-38/39/40 mitigations are in place: class names and delays are constants or numbers, never row data; every animation is `motion-safe:` or hook-gated with reduced-motion tests; row click ignores inner interactive elements. No new security surface.

## Self-Check: PASSED

- Files created/modified above exist; commits 31a2d5e, 9bf1161, 62528bd present on this branch.
