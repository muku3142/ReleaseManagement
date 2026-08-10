---
phase: 04-template-authoring
plan: 10
subsystem: ui
tags: [react, mustache, tailwind, vitest, template-authoring, file-upload]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 08
    provides: "TemplateDetailRoute's chrome draft state/setChrome, dirty tracker, and the Chrome & Logo tab's placeholder paragraph this plan replaces"
  - phase: 04-template-authoring
    plan: 09
    provides: "TemplatePreview's Mustache + markUnknownTokens pipeline and buildValidTokens, reused unchanged for the chrome strings"
  - phase: 04-template-authoring
    plan: 05
    provides: "POST/DELETE/GET /api/templates/{id}/logo, the three LOGO_* reason codes, and app.template.logo.* limits published via GET /api/templates/catalog"
provides:
  - "frontend/src/components/ChromeFieldsForm.tsx - the six TMPL-08 structured chrome fields, always editable"
  - "frontend/src/components/LogoUpload.tsx - drop zone/thumbnail/replace/remove with three distinct, backend-limit-interpolated rejection sentences"
  - "TemplatePreview extended with chrome props (cover block, header/footer strips, literal page-number placeholder)"
  - "The fix for the pre-existing LOGO_TOO_LARGE/LOGO_DIMENSIONS_EXCEEDED identical-message defect in frontend/src/api/auth.ts"
affects: [04-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Chrome text fields resolve through the exact same markUnknownTokens + Mustache.render pipeline the body uses (mirroring TMPL-08's 'same placeholder rules'), but are rendered as React text children via a small marker-splitting helper instead of markdown-it/dangerouslySetInnerHTML, since a running header/footer is one line of plain text, not a Markdown document"
    - "A rejection control (LogoUpload) that owns its own reason->sentence mapping rather than routing through the app-wide messageForReason table, specifically so it can interpolate values (backend-declared byte/pixel limits) that a static REASON_MESSAGES lookup table has no access to"

key-files:
  created:
    - frontend/src/components/ChromeFieldsForm.tsx
    - frontend/src/components/LogoUpload.tsx
    - frontend/tests/components/ChromePreview.test.tsx
    - frontend/tests/components/LogoUpload.test.tsx
  modified:
    - frontend/src/components/TemplatePreview.tsx
    - frontend/src/routes/TemplateDetailRoute.tsx
    - frontend/tests/components/TemplatePreview.test.tsx

key-decisions:
  - "Tasks 1 and 2 were committed together (feat commit 24fe6fe), matching plan 04-08's precedent for interdependent single-wiring tasks: the plan's own Task 1 action text wires LogoUpload (Task 2's component) as ChromeFieldsForm's children in the same TemplateDetailRoute edit, so there is no independently-buildable split between the two tasks."
  - "LogoUpload never calls messageForReason for LOGO_TOO_LARGE/LOGO_DIMENSIONS_EXCEEDED; it owns a local messageForRejection(reason, limits) that interpolates props.limits (sourced from GET /api/templates/catalog). This resolves the known pre-existing defect (both reasons produced the identical static 'This image is too large.' sentence in auth.ts's REASON_MESSAGES) without needing REASON_MESSAGES itself to become dynamic - messageForReason/REASON_MESSAGES is untouched and remains the fallback for every other reason code in the app, per the plan's explicit instruction."
  - "Chrome field resolution failure (unparseable syntax mid-typing, or the release view not yet loaded) falls back to the field's raw unresolved text rather than a dedicated per-field 'unavailable' panel - there is no test or spec requirement for a per-field failure state, and the body's own 'Preview unavailable' panel already covers the whole-document mid-typing-syntax-error case."

patterns-established:
  - "Pattern: when a rejection/validation control's user-facing copy must include numbers from server-declared config (not a hardcoded UI literal), that control interpolates the fetched config directly rather than routing through a static reason->message lookup table designed for parameter-free messages."

requirements-completed: [TMPL-08, TMPL-09]

# Metrics
duration: ~55min
completed: 2026-08-10
---

# Phase 04 Plan 10: Document Chrome Form, Logo Upload and Preview Chrome Rendering Summary

**Six structured chrome fields (cover/header/footer/page-numbers) validated through the same placeholder pipeline as the body, a content-sniffed logo upload control with three genuinely distinct backend-limit-interpolated rejection sentences, and an approximate cover/header/footer preview with a literal `Page X of Y` placeholder.**

## Performance

- **Duration:** ~55 min
- **Started:** 2026-08-10T10:15:00Z (approx)
- **Completed:** 2026-08-10T11:35:00Z (approx)
- **Tasks:** 3
- **Files modified:** 7 (4 created, 3 modified)

## Accomplishments

- `ChromeFieldsForm.tsx` renders the six TMPL-08 structured fields in the exact UI-SPEC order (cover
  toggle, cover title, cover subtitle, header text, footer text, page-number toggle), all
  always-editable 14px `text-sm` inputs (not `font-mono` — these are short strings, not the body's
  source-editing surface), wired into `TemplateDetailRoute`'s Chrome & Logo tab.
- `LogoUpload.tsx` provides the drag/drop + Choose file zone, thumbnail + metadata + Replace/Remove
  (no confirmation) states, an in-flight `Uploading…` indicator, and a client-side type/size
  pre-check that is explicitly a latency optimisation only (T-04-52) — the server re-validates by
  content regardless, and both paths land on the identical sentence.
- **Fixed the pre-existing `LOGO_TOO_LARGE`/`LOGO_DIMENSIONS_EXCEEDED` defect.** Both reasons
  previously rendered the identical static `'This image is too large.'` string from
  `auth.ts`'s `REASON_MESSAGES`, with no interpolation of the backend-declared limits. `LogoUpload`
  now owns its own `messageForRejection(reason, limits)` that produces two clearly different
  sentences — one about file size (`Upload a file under ${formatMaxSize(limits.maxBytes)}.`), one
  about pixel dimensions (`Maximum dimensions are ${limits.maxWidth}×${limits.maxHeight}px.`) —
  sourced entirely from `GET /api/templates/catalog`'s `logoLimits`, with **zero** hardcoded byte
  or pixel literals in the file (asserted by an acceptance-criteria grep).
  `messageForReason`/`REASON_MESSAGES` in `auth.ts` is untouched and remains the fallback for
  every other reason code in the app, per the plan's explicit instruction.
- `TemplatePreview` gained `chrome: ChromeFields` and `logoUrl: string | null` props. Each chrome
  string resolves through the identical `markUnknownTokens` + `Mustache.render` pipeline the body
  uses (TMPL-08's "same placeholder rules, no second expression surface"), but **not** through
  `markdown-it` — a running header/footer is one line of plain text. Unknown tokens render as the
  same amber `<code>` chip as the body, but as React children via a small marker-splitting helper
  (`renderChromeNodes`), never `dangerouslySetInnerHTML` — that prop's count in the file stays at
  exactly one, the existing body container.
- Render order: cover block (only when `coverEnabled`, with the logo thumbnail centered above the
  title/subtitle) → header strip (only when the resolved header text is non-empty) → the existing
  body container, unchanged → footer strip (only when the resolved footer text is non-empty). The
  literal `Page X of Y` placeholder (a single shared `PAGE_NUMBER_PLACEHOLDER` element, so the
  literal string appears exactly once in source) renders in the header strip when page numbering
  is on, or in the footer strip when page numbering is on **and no header strip is present** — so
  it is never shown twice.
- `frontend/tests/components/ChromePreview.test.tsx` (9 tests) covers cover on/off, cover
  title/subtitle Mustache resolution, header strip resolution, footer strip visibility toggling,
  the single `Page X of Y` element, the unknown-token chip on a chrome field, and the logo image.
- `frontend/tests/components/LogoUpload.test.tsx` (8 tests) covers the empty/populated states, a
  client-caught GIF rejection and a client-caught oversized-PNG rejection (both issuing no fetch),
  a server-caught `LOGO_DIMENSIONS_EXCEEDED` rejection with `data-reason`, a server-caught
  `LOGO_INVALID_TYPE` rejection proven **identical in wording** to the client-caught case (T-04-52),
  Remove issuing `DELETE` with no confirmation dialog, and the `Uploading…` in-flight state.

## Task Commits

Each task was committed atomically (Tasks 1 and 2 combined — see Decisions Made):

1. **Tasks 1 & 2: Build the chrome fields form and the logo upload control** - `24fe6fe` (feat)
2. **Task 3: Render chrome in the preview and write both test suites** - `5c75c01` (test)

**Plan metadata:** committed separately by the orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md).

## Files Created/Modified

- `frontend/src/components/ChromeFieldsForm.tsx` - the six structured chrome fields
- `frontend/src/components/LogoUpload.tsx` - drop zone/thumbnail/replace/remove, three rejection states
- `frontend/src/components/TemplatePreview.tsx` - cover block, header/footer strips, page-number placeholder
- `frontend/src/routes/TemplateDetailRoute.tsx` - wired `ChromeFieldsForm`/`LogoUpload` into the Chrome & Logo tab and `chrome`/`logoUrl` props into `TemplatePreview`
- `frontend/tests/components/ChromePreview.test.tsx` - 9 tests for the chrome preview rendering
- `frontend/tests/components/LogoUpload.test.tsx` - 8 tests for the logo control
- `frontend/tests/components/TemplatePreview.test.tsx` - updated for the new `chrome`/`logoUrl` props with a no-chrome fixture; existing 15 assertions unchanged

## Decisions Made

- **Tasks 1 and 2 committed together.** The plan's own Task 1 action text wires `LogoUpload` (built
  in Task 2) as `ChromeFieldsForm`'s children in the same `TemplateDetailRoute` edit — there is no
  clean, independently-buildable split, matching the exact precedent plan 04-08 documented for the
  same reason.
- **The known `LOGO_TOO_LARGE`/`LOGO_DIMENSIONS_EXCEEDED` defect is fixed entirely inside
  `LogoUpload.tsx`, not in `auth.ts`'s static `REASON_MESSAGES` table.** `REASON_MESSAGES` has no
  access to fetched catalog data, so making its two entries merely *different* strings would still
  violate the "no hardcoded limits" requirement. `LogoUpload` builds its own rejection text from
  `ApiError.problem.reason` (server-caught) or its own pre-check (client-caught) and interpolates
  `props.limits`, so the app never renders the old identical-message defect in the one place logo
  rejections actually appear. `messageForReason` is untouched and still serves as the fallback for
  every other reason code, per the plan's explicit instruction.
- **Chrome field resolution failure falls back to raw unresolved text**, not a per-field
  "unavailable" panel — no test or spec requirement calls for one, and the body's own "Preview
  unavailable" panel already covers the whole-document syntax-error case.
- **The `Page X of Y` placeholder is a single shared JSX constant** (`PAGE_NUMBER_PLACEHOLDER`),
  referenced conditionally in both the header and footer strips, so the literal string appears
  exactly once in the source file (an acceptance-criteria grep requirement) while still rendering
  correctly in whichever strip is actually visible.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Reworded a comment to avoid a duplicate `dangerouslySetInnerHTML` grep match**
- **Found during:** Task 3, acceptance-criteria verification
- **Issue:** An explanatory comment above `renderChromeNodes` referenced `dangerouslySetInnerHTML`
  by name in prose, which combined with the actual prop usage in the body container made
  `grep -c "dangerouslySetInnerHTML" TemplatePreview.tsx` return 2 instead of the plan's expected 1
  (the same class of issue plan 04-05 documented for a `nosniff` comment).
- **Fix:** Reworded the comment to describe the prop without repeating the literal token.
- **Files modified:** `frontend/src/components/TemplatePreview.tsx`
- **Verification:** `grep -c "dangerouslySetInnerHTML" TemplatePreview.tsx` returns 1
- **Committed in:** `5c75c01` (Task 3 commit)

**2. [Rule 3 - Blocking] Updated `TemplatePreview.test.tsx` for the new required `chrome`/`logoUrl` props**
- **Found during:** Task 3, first `npm run typecheck` after extending `TemplatePreview`'s props
- **Issue:** Plan 04-09's existing test suite calls `<TemplatePreview body view entries />` without
  the two new required props, failing to compile (`TS2739`) once `chrome`/`logoUrl` were added.
- **Fix:** Added a `NO_CHROME` fixture (`ChromeFields` with every field off/empty) and `logoUrl={null}`
  to the existing `renderPreview` helper, so plan 04-09's 15 assertions keep exercising only the
  body pipeline, unchanged.
- **Files modified:** `frontend/tests/components/TemplatePreview.test.tsx`
- **Verification:** `npm run typecheck` exits 0; all 15 pre-existing assertions in this file still pass
- **Committed in:** `5c75c01` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 comment-wording fix for an acceptance-criteria grep, 1 blocking test-compile fix required by this plan's own prop additions)
**Impact on plan:** Both were necessary for this plan's own stated gates (the acceptance-criteria greps and a passing `npm run typecheck`/`npm test`) to be achievable. No behavior beyond what the plan specified was added.

## Issues Encountered

`frontend/node_modules` did not exist at session start in this worktree; `npm ci` installed the 180
pinned dependencies before any verification command could run. No further environment issues.

## Known Stubs

None. Both new components are fully wired to real backend data (`useUploadLogo`/`useRemoveLogo`/
`usePlaceholderCatalog`'s `logoLimits`) and the preview chrome resolves against the same
`ReleaseView`/`PlaceholderEntry` data the body already uses.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ChromeFieldsForm`'s `onFieldFocus`/`rememberChromeSelection` wiring already records the last
  cursor position of whichever chrome field was last focused (field name, start, end), exactly the
  seam plan 04-11's placeholder drawer needs to insert into a chrome field without any change to
  this plan's code.
- `LogoUpload`'s rejection state and `LogoLimits` plumbing are a stable, fully backend-sourced
  contract — no template-authoring UI in this app now contains a hardcoded byte or pixel literal.
- No blockers identified.

## Threat Flags

None beyond the plan's own `<threat_model>` (T-04-52 through T-04-57), which this plan's
implementation satisfies directly:
- T-04-52 (renamed-SVG elevation) - client pre-check is latency-only; server content-sniffing (plan
  04-05) is the control; both paths produce the identical sentence, proven by
  `LogoUpload.test.tsx` assertions 3 and 6.
- T-04-53 (XSS via a chrome field's resolved text) - chrome strings render as React text children;
  `dangerouslySetInnerHTML` count in `TemplatePreview.tsx` stays at exactly one.
- T-04-54 (logo `<img>` executing as HTML) - plain `<img>` element only, served with the plan
  04-05 `nosniff` header; no `<object>`/`<iframe>`.
- T-04-55 (new expression surface via chrome) - chrome fields go through the identical
  `markUnknownTokens` + `Mustache.render` pipeline and the same catalog entries as the body.
- T-04-56 (DoS via repeated huge uploads) - `file.size > limits.maxBytes` checked client-side
  before any request; server enforces its own cap plus the multipart ceiling (plan 04-05).
- T-04-57 (limits drifting between backend/frontend) - `LogoUpload.tsx` contains zero hardcoded
  byte/pixel literals, asserted by an acceptance-criteria grep; all numbers come from
  `GET /api/templates/catalog`.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-10*

## Self-Check: PASSED

All 4 created files verified present on disk (`frontend/src/components/ChromeFieldsForm.tsx`,
`frontend/src/components/LogoUpload.tsx`, `frontend/tests/components/ChromePreview.test.tsx`,
`frontend/tests/components/LogoUpload.test.tsx`). Both task commits (`24fe6fe`, `5c75c01`)
verified present in `git log`. `npm run typecheck` exits 0, `npm test` exits 0 (121/121 — 104
baseline + 17 new), `npm run build` exits 0. Every acceptance-criteria grep from the plan's three
tasks passes, including the two explicitly required checks: `LOGO_TOO_LARGE`/
`LOGO_DIMENSIONS_EXCEEDED` render clearly different, backend-limit-interpolated sentences with
zero hardcoded literals in `LogoUpload.tsx`.
