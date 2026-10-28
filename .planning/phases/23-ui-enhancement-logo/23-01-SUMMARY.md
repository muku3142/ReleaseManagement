---
phase: 23-ui-enhancement-logo
plan: 01
subsystem: frontend
tags: [shadcn, radix, tailwind, theme, tokens, a11y]
requires: []
provides:
  - "@/ alias (tsconfig paths + Vite/Vitest alias)"
  - "components.json (radix-nova)"
  - "tests/setup.ts jsdom polyfills"
  - "index.css token sheet (light, dark, .paper), paper-aware dark variant, reduced-motion rule"
  - "ThemeProvider / useTheme / Theme"
  - "Shipflow index.html head with no-flash boot script"
affects: [23-02, 23-03, 23-04, 23-05, 23-06, 23-07, 23-08, 23-09, 23-10]
tech-stack:
  added: [radix-ui@1.8.1, cn@0.4.0, class-variance-authority@0.7.1, lucide-react@1.55.0, sonner@2.0.8, cmdk@1.1.1, "@tanstack/react-table@9.3.1", recharts@3.8.0, react-is@19.2.8, "@fontsource-variable/inter@5.3.0", "@fontsource-variable/nunito@5.3.0", "shadcn@4.21.4 (dev)", "tw-animate-css@1.4.0 (dev)"]
  patterns: ["@theme inline token mapping", "owned ThemeProvider with default context value", "polyfills by assignment, never vi.stubGlobal"]
key-files:
  created:
    - frontend/components.json
    - frontend/src/lib/utils.ts
    - frontend/tests/setup.ts
    - frontend/src/components/theme/ThemeProvider.tsx
    - frontend/tests/components/ThemeProvider.test.tsx
  modified:
    - frontend/package.json
    - frontend/package-lock.json
    - frontend/tsconfig.json
    - frontend/vite.config.ts
    - frontend/src/index.css
    - frontend/src/main.tsx
    - frontend/index.html
decisions:
  - "The OS-change listener applies event.matches rather than re-reading the MediaQueryList, so the class follows the event payload"
  - "Task 3 split into a RED test commit and a GREEN feat commit (tdd=true)"
metrics:
  duration: "~10 min"
  completed: 2026-10-26
  tasks: 3
  files: 12
---

# Phase 23 Plan 01: Shipflow UI Foundation Summary

The pinned shadcn/Radix toolchain is installed (radix-nova style), along with the `@/` alias, jsdom polyfills, OKLCH tokens for light, dark and the `.paper` island, a global reduced-motion rule, self-hosted Inter/Nunito fonts, and an owned ThemeProvider with a static no-flash boot script under the Shipflow title.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Pin packages, components.json, alias, setup | ed29081 | Pin check passed; next-themes, @base-ui/react, clsx and tailwind-merge are not direct deps. clsx is only present transitively through cva and recharts. |
| 2 Tokens, dark variant, reduced motion, fonts | df3d375 | |
| 3 ThemeProvider, boot script, head | 46810b3 (RED), b9abbb8 (GREEN) | 9 tests cover every behaviour bullet |

## Verification

- `npx tsc -b`: passes.
- `npx vitest run`: 75 files, 856 tests pass. The baseline was 847 and this plan adds 9. One full run showed 1 intermittent failure. The immediate rerun was fully green. That matches the known flaky test.
- `npm run build`: succeeds. `dist/index.html` contains the Shipflow title and the `shipflow-theme` boot script. The built CSS contains the Inter/Nunito `@font-face` rules and the reduced-motion block. The pre-existing >500 kB chunk warning remains.

## Deviations from Plan

**1. [Rule 1 - Plan check defect] `--color-success-muted` is not in the built CSS yet**
- The Task 2 criterion `grep -l -- "--color-success-muted" dist/assets/*.css` cannot pass at this point. `@theme inline` inlines `var(--success-muted)` into the utilities that use it and emits no `--color-*` variable. No source file uses `bg-success-muted` yet, so Tailwind generates nothing for it.
- I checked the property itself instead. A scratch script compiled `src/index.css` with `@tailwindcss/node` against the candidates `bg-success-muted`, `text-muted-foreground`, `bg-sidebar-accent`, `fill-chart-3`, `font-brand`, `rounded-xl`, `dark:bg-card` and `ring-ring`. Every one produced a rule. `dark:bg-card` compiled to `:where(.dark, .dark *):not(.paper, .paper *)`.
- No code change was needed.

**2. Verify commands targeted the main checkout path.** The plan's `cd /d/IdeaProjects/ReleaseManagement/frontend` was run from this worktree's `frontend/` instead.

## Known Stubs

The `favicon.svg`, `favicon.ico` and `apple-touch-icon.png` links in `index.html` point at files that plan 23-02 adds. Until then those URLs 404.

## Threat Flags

None. T-23-01..04 are mitigated as planned:
- Stored values are validated and storage access is wrapped in try/catch.
- The boot script is static and only compares the stored value.
- Packages use exact pins and the lockfile is committed.
- cn is pinned at 0.4.0.

## Self-Check: PASSED

- All five created files exist.
- Commits ed29081, df3d375, 46810b3 and b9abbb8 are on the branch.
