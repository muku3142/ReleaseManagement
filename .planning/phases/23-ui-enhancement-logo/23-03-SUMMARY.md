---
phase: 23-ui-enhancement-logo
plan: 03
subsystem: frontend
tags: [shadcn, radix, components, tokens, a11y, sonner]
requires: ["23-01"]
provides:
  - "src/components/ui base set (21 components + input-group, textarea) on Radix"
  - "Badge status variants success/warning/destructive/info/neutral/outline (default neutral)"
  - "Alert variants default/destructive/warning/info"
  - "Table containerProps for caller-owned scroll regions"
  - "SidebarProvider persisting to localStorage['shipflow-sidebar']"
  - "Toaster wired to the owned ThemeProvider"
affects: [23-04, 23-05, 23-06, 23-07, 23-08, 23-09, 23-10]
tech-stack:
  added: []
  patterns: ["owned shadcn edits applied after every add", "generated files import cn from the 'cn' package directly"]
key-files:
  created:
    - frontend/src/components/ui/{alert,badge,button,card,chart,command,dialog,dropdown-menu,empty,input,input-group,kbd,label,popover,separator,sheet,sidebar,skeleton,sonner,spinner,table,textarea,tooltip}.tsx
    - frontend/src/hooks/use-mobile.ts
    - frontend/tests/components/ui/ownedEdits.test.tsx
  modified: []
decisions:
  - "Destructive Button is solid (bg-destructive text-destructive-foreground), matching UI-SPEC's bg-red-600 -> Button variant=destructive migration row"
  - "input-group and textarea kept: they are registry dependencies of command"
metrics:
  duration: "~12 min"
  completed: 2026-10-26
  tasks: 2
  files: 25
---

# Phase 23 Plan 03: shadcn Base Set Summary

The 21-component shadcn base set is in `src/components/ui` as owned code on Radix (radix-nova). The UI-SPEC edits are applied: 600 weight, a solid ring-2 focus, destructive text on its own token, and token-driven status variants on Badge and Alert. Spinner and Skeleton are aria-hidden. Table takes `containerProps`. The sidebar persists to localStorage instead of a cookie, and the Toaster reads the owned ThemeProvider.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Generate base set | 5d3506a | `npx shadcn@4.21.4 add ... --yes`. No `@base-ui` anywhere; dialog imports from `"radix-ui"`. The CLI left index.css, components.json, tsconfig and vite.config untouched. |
| 2 Owned edits (TDD) | 9fc6c85 (RED), 6f31b30 (GREEN) | 17 tests cover every behaviour bullet |

## Verification

- Grep gates: no `@base-ui` or `next-themes` in src/package.json, no `font-medium`, no `ring-[3px]`/`ring-ring/50`, no `document.cookie`, no `text-white`/`dark:bg-destructive/60` in button, `shipflow-sidebar`, `motion-reduce:animate-none` and `containerProps` are present, and there is no `richColors`. All pass.
- `npx tsc -b`: passes.
- `npx vitest run`: 76 files, 873 tests pass (856 baseline + 17 new). After Task 1, two full runs each showed 1 intermittent failure, and the next run was fully green (856/856). That matches the known flaky test. The final run after Task 2 was green on the first attempt.
- `npm run build`: succeeds. The >500 kB chunk warning was already there.

## Deviations from Plan

**1. [Rule 3] The CLI added `next-themes` and loosened the `recharts` pin to `^3.8.0`.** I uninstalled `next-themes` and reinstalled `recharts@3.8.0 --save-exact`. package.json and package-lock.json ended up byte-identical to the base, so Task 1 has no package diff. The other pins (radix-ui, cn 0.4.0, cva, lucide-react, sonner, cmdk, react-is) were not changed.

**2. [Rule 3] `input-group.tsx` and `textarea.tsx` were generated in addition to the 21.** `command.tsx` imports `input-group`, which imports `textarea`, so they are not unused and I kept them. The owned edits are applied to both.

**3. [Rule 1] Focus pattern differs in radix-nova.** This style emits `focus-visible:ring-3`, not `ring-[3px]`; only badge uses `ring-[3px]`. Edit 2 matched both forms, and also the `has-[[data-slot=input-group-control]:focus-visible]` variant in input-group. The translucent `focus-visible:ring-destructive/20`, `dark:focus-visible:ring-destructive/40` and `focus-visible:border-destructive/40` overrides were removed from destructive variants, so the ring stays solid. `aria-invalid:*` was left unchanged.

**4. [Rule 1] Destructive Button was tinted in radix-nova, not `text-white`.** The generated variant was `bg-destructive/10 text-destructive` with dark tints. I replaced it with `bg-destructive text-destructive-foreground hover:bg-destructive/90`, following the UI-SPEC rule that `bg-red-600` becomes a destructive Button and the requirement that destructive text sits on `--destructive-foreground`.

**5. Badge base keeps `w-fit shrink-0 whitespace-nowrap` and the svg sizing** alongside the specified `inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold`. I dropped `h-5`, `overflow-hidden`, `justify-center` and the `aria-invalid` classes.

**6. Sonner keeps the generated `--normal-*` CSS variables** on the Toaster style. They already point at popover/border tokens.

**7. Verify commands were run from this worktree's `frontend/`**, not the main checkout path in the plan.

## Known Stubs

None. `Toaster` is not mounted yet; per the plan, 23-08 mounts it.

## Threat Flags

None. Mitigations by threat:
- **T-23-08:** pinned CLI, official registry, `@base-ui` gate passed, config untouched.
- **T-23-09:** chart.tsx logic is untouched; the only change is the weight class.
- **T-23-10:** the sidebar cookie is replaced with localStorage.
- **T-23-11:** reads and writes are wrapped in try/catch, and only the literal `'false'` is honoured.

## Self-Check: PASSED

- All 23 ui files, use-mobile.ts and ownedEdits.test.tsx exist.
- Commits 5d3506a, 9fc6c85 and 6f31b30 are on the branch.
