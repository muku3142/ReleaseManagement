---
phase: 23-ui-enhancement-logo
plan: 08
subsystem: frontend-shell
tags: [ui, shell, sidebar, router, theme, motion, a11y]
requires: ["23-02", "23-03", "23-04", "23-07", "23-11"]
provides:
  - RootLayout (Outlet + one Toaster + TooltipProvider) at the router root
  - AppShell on the pathless authenticated route (login renders outside it)
  - AppSidebar with grouped nav, sliding active indicator, hover highlight, animated mark
  - TopBar with SidebarTrigger, CommandPalette, ThemeMenu, UserMenu
  - staticData.themed route option and the .paper island on main#content
affects: [frontend/src/router.tsx, frontend/src/components/AppShell.tsx, frontend/src/components/ui/sidebar.tsx]
tech-stack:
  added: []
  patterns:
    - "Active nav styling via TanStack Link's data-status=active attribute rather than activeProps"
    - "Page fade keyed by the leaf routeId, not the pathname"
key-files:
  created:
    - frontend/src/components/layout/AppSidebar.tsx
    - frontend/src/components/layout/TopBar.tsx
    - frontend/src/components/layout/ThemeMenu.tsx
    - frontend/src/components/layout/UserMenu.tsx
  modified:
    - frontend/src/router.tsx
    - frontend/src/components/AppShell.tsx
    - frontend/src/components/ui/sidebar.tsx
    - frontend/tests/components/AppShell.test.tsx
decisions:
  - "SidebarInset renders a div so main#content is the only main landmark"
  - "SidebarMenuButton omits data-active when false (Tailwind data-active: is a presence selector)"
  - "Active nav classes use data-[status=active]: so they override the variant's text colour without class-order luck"
  - "The nav sits inside SidebarContent (the scroll container) so the indicator scrolls with the items"
metrics:
  duration: ~25 min
  completed: 2026-10-27
  tasks: 2
  files: 8
---

# Phase 23 Plan 08: App shell rebuild Summary

The shell now lives on the authenticated route: a collapsible ProductOps sidebar with Delivery/Planning/Configure groups, a 2px indicator that slides between active items, and a top bar holding the palette, the theme menu and the account menu. The router root holds only the Toaster and TooltipProvider, and unmigrated pages render as a light `.paper` island.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Router layout split, AppShell layout, sidebar nav, paper island | 9aedf31 |
| 2 | Top bar with palette trigger, theme menu and account menu | 6b95823 |

## What was built

- `router.tsx`: `RootLayout` (TooltipProvider > Outlet + `Toaster position="bottom-right" visibleToasts={3} closeButton duration={5000}`); `authenticatedRoute` gains `component: AppShell` (id and beforeLoad unchanged); `/releases` gets `staticData: { themed: true }`; `StaticDataRouteOption { themed?: boolean }` added to the module augmentation.
- `AppShell.tsx`: SidebarProvider > skip link > AppSidebar > SidebarInset > TopBar, CredentialAlert, AdoCredentialBanner, `main#content` (tabIndex -1, `.paper` unless the leaf match is themed) > `div key={leafRouteId} className="motion-safe:animate-page-in"` > Outlet.
- `AppSidebar.tsx`: lockup link (`${PRODUCT_NAME}, go to dashboard`, `ProductOpsLogo variant="sidebar" animate`), `nav[aria-label=Main]`, Dashboard plus Assistant (when enabled), then the three groups from `NAV_ITEMS`; the indicator is measured in a layout effect on `[pathname, sidebarState, assistantEnabled]` and only gets the transform transition after its first position.
- `TopBar.tsx`, `ThemeMenu.tsx` (Light/Dark/System radio group), `UserMenu.tsx` (initial avatar, email label, Sign out with a pending "Signing out…" state; the sign-out logic moved here unchanged from the old AppShell).

## AppShell.test.tsx changes (deliberate)

- Removed "shows Assistant between Releases and Templates when enabled": Assistant now sits straight after Dashboard (replaced by "shows Assistant straight after Dashboard when enabled").
- Removed "links Roadmap to /roadmap straight after Portfolio" and "F5: links Briefings to /briefings straight after Roadmap": replaced by one full-order assertion (Dashboard, Tickets, Releases, Roadmap, Briefings, Products & clients, Templates, Settings) that also checks the Roadmap href. The label "Portfolio" is now "Products & clients".
- Removed "hides Assistant and does not fetch the status when signed out": the shell no longer renders signed out (BL fix 5), so the case cannot occur; replaced by the paper-island cases.
- Kept "hides Assistant when disabled / while loading / when the status request fails" (disabled is covered by the full-order case).
- Router mock gained `useMatches`, `useNavigate`, `useRouterState`, a pass-through `Link` that sets `aria-current`/`data-status` for the mocked pathname, and an `Outlet` that counts mounts. CommandPalette is mocked; ThemeMenu and UserMenu run for real inside ThemeProvider + QueryClientProvider + TooltipProvider.
- New cases: group labels, hover class, brand link + mark animation + no h1, indicator (single, aria-hidden, opacity-0 when nothing active, no slide on first position, transform transition after moving), skip link first focusable and a single `main`, paper vs themed, page fade remount by routeId only, banners still rendered, top bar order, theme menu (checked System, Dark applies the class and stores `productops-theme`), account menu sign-out to /login via POST /api/auth/logout, pending state disabled.

## Verification

- `npx tsc -b`: clean.
- `npx vitest run tests/components/AppShell.test.tsx`: 18/18 passed.
- `npx vitest run`: 1002 passed, 1 failed of 1003 (86 files); the failure was the known flaky RoadmapRoute "opens the detail row named by the item param", which passed 10/10 when its file was rerun on its own.
- `npm run build`: succeeded (the existing >500 kB chunk warning only).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Nested main landmarks**
- **Found during:** Task 1
- **Issue:** shadcn's `SidebarInset` renders `<main>`, so the plan's `main#content` inside it would nest two `main` landmarks (UI-SPEC wants one `main#content`).
- **Fix:** `SidebarInset` renders a `div` in `ui/sidebar.tsx`; the test asserts a single `main`.
- **Commit:** 9aedf31

**2. [Rule 1 - Bug] Every sidebar button styled as active**
- **Found during:** Task 1
- **Issue:** `SidebarMenuButton` always set `data-active={isActive}`, which React writes as `data-active="false"`; Tailwind v4's `data-active:` is a presence selector, so every item would get the accent background.
- **Fix:** `data-active={isActive || undefined}`.
- **Commit:** 9aedf31

**3. [Rule 1 - Bug] Active classes vs. muted text colour**
- **Found during:** Task 1
- **Issue:** Link `activeProps` className is concatenated without tailwind-merge, so `text-primary` vs `text-muted-foreground` would depend on CSS order.
- **Fix:** Active styling uses `data-[status=active]:bg-primary/10 data-[status=active]:text-primary data-[status=active]:font-semibold` (TanStack Link sets `data-status="active"`); `aria-current="page"` still drives the indicator.
- **Commit:** 9aedf31

**4. [Rule 1 - Bug] Indicator measurement and placement**
- **Found during:** Task 1
- **Issue:** `offsetTop` is relative to each `SidebarMenuItem` (`relative`), so it would always be ~0; and wrapping `SidebarContent` in `nav` would put the indicator outside the scroll container.
- **Fix:** The nav sits inside `SidebarContent`, and the top is measured as the bounding-rect difference between the active link and the nav.
- **Commit:** 9aedf31

**5. [Rule 2] `main` fills the inset**
- Added `flex-1` to `main#content` so the paper island reaches the bottom of the viewport on short pages instead of leaving dark frame below.

## Known Stubs

None.

## Self-Check: PASSED

- Files exist: AppSidebar.tsx, TopBar.tsx, ThemeMenu.tsx, UserMenu.tsx, router.tsx, AppShell.tsx.
- Commits exist: 9aedf31, 6b95823.
