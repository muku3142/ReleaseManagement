---
phase: 23-ui-enhancement-logo
plan: 07
subsystem: frontend
tags: [login, brand, toasts, sonner, paper, dark-mode, alerts, a11y]
requires: ["23-02", "23-03"]
provides:
  - "Standalone branded LoginRoute (ShipflowLogo login variant, Card, warning/destructive Alerts) ready for the 23-08 router move"
  - "Signed-in visitor redirect from /login through safeRedirectTarget"
  - "Settings success confirmations as sonner toasts (the Phase 23 toast proof)"
  - ".paper on RenderedDocument page box, both prose roots and the template-logo preview"
  - "CredentialAlert and AdoCredentialBanner on Alert variant=warning"
affects: [23-08]
tech-stack:
  added: []
  patterns: ["mock sonner in tests and assert toast calls", "Alert content wrapped in one div so inline text + link stay on one grid row"]
key-files:
  created:
    - frontend/tests/routes/LoginRoute.test.tsx
  modified:
    - frontend/src/routes/LoginRoute.tsx
    - frontend/src/routes/SettingsRoute.tsx
    - frontend/tests/components/SettingsRoute.test.tsx
    - frontend/src/components/RenderedDocument.tsx
    - frontend/src/components/TemplatePreview.tsx
    - frontend/src/components/LogoUpload.tsx
    - frontend/src/components/CredentialAlert.tsx
    - frontend/src/components/AdoCredentialBanner.tsx
decisions:
  - "Login and site-not-configured copy use PRODUCT_NAME (Plurio), never a hard-coded name; the long product description is left out because the login layout has no supporting-text slot"
  - "Settings ADO 'OK' badge moved from text-green-600 to text-success so SettingsRoute has no raw green (plan grep criterion)"
metrics:
  duration: "~25 min"
  completed: 2026-10-26
  tasks: 3
  files: 9
---

# Phase 23 Plan 07: Login, Settings Toasts, Paper and Banners Summary

The login page is a standalone branded layout: the brand lockup (mark, `plurio` wordmark, "The product delivery platform") above a "Sign in" card on a soft brand glow, built from tokens only. A rejected sign-in shows a destructive Alert that takes focus; the fields keep their values. When no Jira site exists, the form is replaced by a warning Alert that explains `RM_JIRA_DEFAULT_BASE_URL`, with no link to the session-protected `/settings`. A visitor who already has a session is sent on through `safeRedirectTarget`. Settings now confirms saves with toasts, and documents and template previews stay light paper inside a dark app.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 Standalone branded login | 8455d67 | New `tests/routes/LoginRoute.test.tsx` (10 tests, written first, 8 failed before the rewrite) |
| 2 Settings toasts | 820807f | `toast.success` for the Jira site, the token and the ADO save; the three green inline texts and their `saved`/`tokenReplaced` state are gone |
| 3 Paper + warning banners | e47f1f8 | No test assertion needed changing |

## Changed test assertions

- `SettingsRoute.test.tsx` 'sends organization, token and expiry on submit': `toContain('Azure DevOps settings saved.')` is now `expect(toast.success).toHaveBeenCalledWith('Azure DevOps settings saved.')` plus a check that the text is not inline.
- `SettingsRoute.test.tsx` 'shows the mapped message for a rejected token': added `expect(toast.success).not.toHaveBeenCalled()`.
- Added: 'confirms a saved Jira site with a toast instead of inline text' and 'confirms a replaced token with a toast and clears the input'. `stubFetch` takes an optional map of extra PUT responses, and `submitFormOf(inputId)` generalises `submitAdoForm`.
- AdoCredentialBanner, RenderedDocument, TemplatePreview, LogoUpload and AppShell tests: unchanged and green. They assert text, role and data-reason, not the replaced amber classes. The TemplatePreview amber chip assertion still holds because the unknown-marker chip was not touched.

## Deviations from Plan

**1. [Rule 3 - Criterion conflict] ADO state badge `OK` uses `text-success`**
- **Found during:** Task 2
- **Issue:** The acceptance grep requires no `text-green-600` in SettingsRoute.tsx, but the ADO state badge also used it, outside the three success texts.
- **Fix:** Swapped that one class to the `text-success` token. The amber/red badge classes are unchanged because Settings is still an unmigrated page.
- **Commit:** 820807f

**2. Layout details**
- The Card uses `gap-6`, not `mt-6` on the form, so the header-to-form spacing (24px) applies to both the form and the site-not-configured Alert.
- `paper bg-white text-zinc-950` also sits on the `MarkdownBody` prose root (not just the RenderedDocument wrapper) because `AiResultCard` renders `MarkdownBody` on its own.
- For the logo preview, `paper` goes on the `<img>` (the light preview box) rather than on its parent, which also holds the Replace/Remove buttons and should stay themed.
- Both banners and the login error put their content in one `<div>` inside the Alert, because the Alert is a CSS grid and would otherwise split the text and the link onto separate rows.

## Verification

- `npx tsc -b`: clean
- `npx vitest run` (full): 893 tests in 78 files. 892 passed; the only failure was the known flaky RoadmapRoute 'opens the detail row named by the item param', which passes on rerun (10/10)
- `npm run build`: succeeds (only the existing chunk-size warning)
- Grep criteria: no `href="/settings"` and no raw gray/blue/red/green/amber classes in LoginRoute.tsx; `RM_JIRA_DEFAULT_BASE_URL`, `autoComplete="username"` and `type="password"` present; `toast.success('Jira site saved.')` present and no `text-green-600` in SettingsRoute.tsx; `paper` in all three preview files; no `prose-invert`/`dark:` in RenderedDocument/TemplatePreview; `variant="warning"` in both banners and no `amber-` in them

## Threat Flags

None. Both redirect paths go through `safeRedirectTarget` (T-23-23, and a test covers the external URL case). The form attributes and `rel="noreferrer"` are kept (T-23-24). No unauthenticated settings path was added (T-23-25). The sanitisation and `dangerouslySetInnerHTML` inputs are untouched (T-23-26).

## Self-Check: PASSED

- FOUND: frontend/tests/routes/LoginRoute.test.tsx
- FOUND commits: 8455d67, 820807f, e47f1f8
