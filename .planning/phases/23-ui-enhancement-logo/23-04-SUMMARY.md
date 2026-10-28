---
phase: 23-ui-enhancement-logo
plan: 04
subsystem: frontend
tags: [command-palette, cmdk, shortcuts, navigation, tanstack-query]
requires: ["23-03"]
provides:
  - "CommandPalette (top-bar trigger + CommandDialog + Ctrl/Cmd+K), ready for 23-08 to mount inside SidebarProvider"
  - "NAV_ITEMS: one nav list (label, route, icon, group, goKey, assistantOnly) for palette, shortcuts and the 23-08 sidebar"
  - "useGoShortcuts: G then D/T/R/M/B/P/S"
  - "Optional trailing `enabled = true` on useReleases, useClients, useProducts, useRoadmap"
  - "CommandDialog forwards onCloseAutoFocus to DialogContent"
affects: [23-08]
tech-stack:
  added: []
  patterns: ["list hooks take an opt-in `enabled` flag for lazy consumers", "palette rows use stable `kind:id` values with name keywords for cmdk filtering"]
key-files:
  created:
    - frontend/src/components/layout/navItems.ts
    - frontend/src/hooks/useGoShortcuts.ts
    - frontend/src/components/CommandPalette.tsx
    - frontend/tests/hooks/useGoShortcuts.test.tsx
    - frontend/tests/components/CommandPalette.test.tsx
  modified:
    - frontend/src/api/releases.ts
    - frontend/src/api/clients.ts
    - frontend/src/api/products.ts
    - frontend/src/api/roadmap.ts
    - frontend/src/components/ui/command.tsx
decisions:
  - "Loading/error rows are disabled forceMount CommandItems inside a forceMount group, so cmdk does not filter them out while the user types"
  - "Product and roadmap destinations are built through parseDashboardSearch / parseRoadmapSearch, so palette navigation passes the same validators as a typed URL"
  - "Search text is cleared on every open/close rather than kept across sessions"
metrics:
  duration: "~10 min"
  completed: 2026-10-26
  tasks: 2
  files: 10
---

# Phase 23 Plan 04: Command Palette and G-Shortcuts Summary

Ctrl/Cmd+K now opens a cmdk palette. It lists pages and existing actions, and searches releases, active clients, active products and active roadmap items through the four existing list hooks, which fetch only once the palette has first opened. The shared `NAV_ITEMS` list drives both the palette and the G-sequence shortcuts.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1. Lazy list hooks, `navItems.ts`, `useGoShortcuts` | 666c042 | 17 tests: all seven G-keys, the 1500 ms window, disarm on another key, input/textarea/select/contenteditable, ctrl/meta/alt, open dialog |
| 2. CommandPalette | 09de107 | 18 tests: lazy fetch, Ctrl/Meta+K toggle (also from inside an input), trigger + Win/Mac Kbd, group visibility, all four destinations, archived filtering, per-group error/loading, empty text, theme, sidebar, XSS name |

## Verification

- `npx tsc -b`: clean.
- `npx vitest run`: 79 files, 916/916 passed, on 4 consecutive runs. One earlier run had 1 failure in a file that was not captured, and it passed on rerun. It matches the known RoadmapRoute flake.
- `npm run build`: succeeds. The existing >500 kB main-chunk warning is still there (775 kB, 229 kB gzip).
- Acceptance greps: there is no `dangerouslySetInnerHTML` and no `'/api/` string in CommandPalette.tsx. `enabled = true` is in all four API files, and `Products & clients` is in navItems.ts.

## Deviations from Plan

- **`ui/command.tsx` passthrough (anticipated by the plan).** The generated `CommandDialog` spread its extra props onto `Dialog`, not `DialogContent`. It now takes one `onCloseAutoFocus` prop and forwards it, so that after a navigation focus goes to `#content` and not back to the trigger.
- **`<Command>` wrapper inside `CommandDialog`.** The radix-nova `CommandDialog` does not render a `Command` root, so the palette wraps its own input and list in `<Command>`. This is the generator's intended usage, not a code change.

None otherwise. There were no auth gates.

## Threat Model Coverage

- T-23-12: names render only as React text. The hostile `<img onerror>` release name test confirms that no `img` element is created.
- T-23-13: navigation uses typed `params`/`search` built from server ids. The dashboard and roadmap searches go through the existing parsers.
- T-23-15: `enabled: open` on all four lists. A test asserts that nothing is requested before the first open.
- T-23-16: G-sequences are ignored in form fields, while a modifier is held, and while a dialog is open. The palette's own dialog counts, so they do not fire while it is open. There are no single-key shortcuts.

## Known Stubs

None. The palette is not mounted yet; mounting it in the top bar is plan 23-08's job.

## Self-Check: PASSED

- All 5 created files exist; commits 666c042 and 09de107 are on the branch.
