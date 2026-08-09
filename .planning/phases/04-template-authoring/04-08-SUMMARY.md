---
phase: 04-template-authoring
plan: 08
subsystem: ui
tags: [react, tanstack-router, tanstack-query, tailwind, vitest, template-authoring]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 07
    provides: "frontend/src/api/templates.ts typed hooks, the /templates list screen, and a placeholder /templates/$templateId route this plan replaces"
provides:
  - "frontend/src/routes/TemplateDetailRoute.tsx - the two-pane authoring screen: toolbar with inline rename and activation, Body/Chrome & Logo tab switch, 45/55 editor/preview panes, release picker, three save-outcome banners, and a dirty guard"
  - "The real /templates/$templateId route registration in router.tsx, replacing plan 04-07's placeholder"
  - "A clean seam for plan 04-09 (empty preview render region, data-testid=\"preview-content\") and plan 04-10 (Chrome & Logo tab placeholder paragraph)"
affects: [04-09, 04-10, 04-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dirty tracking derived from a `saved` snapshot vs live draft state (body/chrome), re-seeded from the server via a useEffect keyed on [template.data?.id, template.data?.updatedAt] so both a template switch and a successful save re-seed cleanly without a second effect racing it"
    - "Save-outcome banner visibility gated on `saveOutcome !== null && !dirty` rather than a separate body/chrome-change-clears-the-banner effect - avoids a real race against the seed effect (see Deviations)"
    - "useBlocker({ shouldBlockFn, withResolver: true }) plus a beforeunload listener, both gated on the same dirty flag, for the unsaved-changes-on-navigate guard"

key-files:
  created:
    - frontend/src/routes/TemplateDetailRoute.tsx
    - frontend/tests/components/TemplateDetailRoute.test.tsx
  modified:
    - frontend/src/router.tsx

key-decisions:
  - "Save-outcome banner visibility is derived (saveOutcome !== null && !dirty) instead of a literal second useEffect clearing it on every body/chrome change, because the seed-from-server effect gives `chrome` a new object reference on every successful save (even when its content is unchanged), which would fire a naive [body, chrome]-keyed effect and hide the just-shown banner in the same tick. The derived-visibility approach produces the same user-facing behavior (banner disappears once you edit again) without that race."
  - "The D-04-19 explanatory comment paraphrases the two rejected editor libraries as 'a code-editor component and a rich-text editor component' rather than naming them, because the plan's own acceptance criteria greps the route file for those exact product names (case-insensitive) and expects zero matches - naming them in a comment would fail the plan's own gate while adding no functional value over the paraphrase."
  - "Tasks 1 and 2 were committed together as a single feat commit: both write to the same component and their draft/dirty/save-outcome state is interdependent (the toolbar's Save button and dirty indicator specified in Task 1 only become meaningful once Task 2's mutation wiring exists), so there was no clean, independently-buildable split of the diff."

patterns-established:
  - "Pattern: when a controlled draft value needs a 'last known good' comparison point (dirty tracking), seed it from the query cache via an effect keyed on both the entity id and its updatedAt timestamp, and derive any outcome-banner visibility from the dirty flag rather than a second change-detection effect, to avoid racing object-identity changes in the seeded value."

requirements-completed: [TMPL-01, TMPL-04, TMPL-05, TMPL-06]

# Metrics
duration: ~40min
completed: 2026-08-09
---

# Phase 04 Plan 08: Template Authoring Screen Summary

**The `/templates/$templateId` two-pane authoring screen - plain monospace body editor, release-backed preview picker, three visually separable save outcomes, and a navigation-blocker dirty guard - replacing plan 04-07's placeholder route.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-08-09T21:35:00+03:00 (approx)
- **Completed:** 2026-08-09T22:11:45+03:00
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- `frontend/src/routes/TemplateDetailRoute.tsx` renders the toolbar (name + inline Rename that
  saves independently of the body/chrome Save action, document-type subtitle, Active badge /
  Make active), a Body / Chrome & Logo tab switch governing only the left pane, and a fixed
  45/55 two-pane region (`lg:basis-[45%]` / `lg:basis-[55%]`, stacking editor-first below `lg`)
  per D-04-21.
- The body editor is a plain `<textarea>` - `font-mono`, `resize-none`, no syntax highlighting,
  no editor dependency (D-04-19).
- The preview pane carries the standing "Preview approximation" notice, a release picker
  (`useReleases`, defaulting to the newest release, TMPL-06) and an empty
  `data-testid="preview-content"` render region for plan 04-09; with zero releases it shows the
  disabled-picker/"Create a release first" fallback instead.
- Saving produces three visually separable outcomes: a blue "Template saved." banner, an amber
  "Template saved with N unknown placeholder warning(s)." banner with a "Show details"
  disclosure (D-04-07 - warns, never blocks), or a red `role="alert"` blocking the save with the
  backend's specific `TEMPLATE_MALFORMED` detail text (naming the chrome field when relevant) or
  the fixed `RENDER_BOUND_EXCEEDED` copy (TMPL-05).
- A TanStack Router `useBlocker` plus a `beforeunload` listener, both gated on a dirty flag
  (draft body/chrome vs the last-saved snapshot), guard against losing unsaved edits on
  navigation or tab close; the confirmation reuses `ConfirmDialog` unchanged.
- The placeholder `/templates/$templateId` route from plan 04-07 (`component: () => null`) is
  now the real `TemplateDetailRoute` - no second route was registered.
- `frontend/tests/components/TemplateDetailRoute.test.tsx` (14 tests) pins the loading/loaded
  states, pane layout order, the release picker's populated/empty states, the dirty indicator's
  full lifecycle, all three save outcomes (including the exact backend detail text for both
  blocking reasons), the Active-pill/Make-active toggle, and that renaming issues `PUT .../name`
  without a `PUT .../content` alongside it.

## Task Commits

Each task was committed atomically:

1. **Tasks 1 & 2: Build the toolbar/panes/release picker and wire save/validation/dirty-guard** - `afb0b24` (feat)
2. **Task 3: Write the TemplateDetailRoute test** - `b3c87bb` (test)

**Plan metadata:** committed separately by the orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md).

## Files Created/Modified

- `frontend/src/routes/TemplateDetailRoute.tsx` - the two-pane authoring screen
- `frontend/src/router.tsx` - swapped the placeholder `/templates/$templateId` route's
  `component` for `TemplateDetailRoute`; no new route registered
- `frontend/tests/components/TemplateDetailRoute.test.tsx` - 14 tests

## Decisions Made

- **Save-outcome banner visibility derived from `!dirty` rather than a literal clearing effect.**
  The plan's action text describes clearing `saveOutcome` in a `useEffect` keyed on
  `[body, chrome]`. Implementing that literally creates a real bug: the seed-from-server effect
  (which re-seeds `saved`/`body`/`chrome` on both template switch and successful save, per the
  plan's own instruction) assigns `chrome` a new object reference from the server response even
  when its content is identical, which would re-fire a `[body, chrome]`-keyed effect in the same
  tick as a successful save and immediately hide the banner that save just triggered. Gating
  banner visibility on `saveOutcome !== null && !dirty` produces the same observable behavior
  (the banner disappears once the author edits again) without that race, and is tested by
  "shows the warning banner ... reveals the verbatim entries on Show details" and the clean/save
  round-trip test, both of which pass.
- **D-04-19 comment paraphrases rejected editor libraries instead of naming them.** The plan's
  action text supplies a literal comment naming both rejected libraries by product name, but the
  same plan's acceptance criteria greps the route file (case-insensitive) for those exact names
  and requires zero matches. Naming them would fail the plan's own gate. The paraphrase preserves
  the WHY (a code-editor library and a rich-text editor library were both considered) without the
  literal product tokens.
- **Tasks 1 and 2 committed together.** Both operate on the same single component with
  interdependent state (dirty tracking, save-outcome banners); there is no clean way to build and
  verify one without the other in this component's structure. Task 3 (tests) remained its own
  commit as planned.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed a closure-narrowing TypeScript error in the rename handlers**
- **Found during:** Task 1/2, first `npm run typecheck`
- **Issue:** `startRename`/`commitRename` referenced `data.name` (from `const data = template.data; if (!data) return null;`) inside nested function declarations; TypeScript does not retain the outer `if (!data) return null` narrowing across a nested function-declaration boundary, so `data` was still typed `Template | undefined` inside those closures (`TS18048`).
- **Fix:** Captured `const currentName = data.name;` immediately after the guard (mirroring `ReleaseDetailRoute.tsx`'s `const currentReleaseId = data.id;` pattern already established in this codebase) and referenced `currentName` inside the closures instead.
- **Files modified:** `frontend/src/routes/TemplateDetailRoute.tsx`
- **Verification:** `npm run typecheck` exits 0
- **Committed in:** `afb0b24`

**2. [Rule 1 - Bug] Fixed a line-wrapped literal string breaking a plan acceptance-criteria grep**
- **Found during:** Task 1, acceptance-criteria verification
- **Issue:** The standing preview-approximation notice's text was written across two JSX source lines for readability; this collapses correctly at runtime (JSX joins adjacent text lines with a space) but the plan's acceptance criterion is a literal single-line `grep` for the full sentence, which does not match text split across source lines.
- **Fix:** Rewrote the sentence onto one source line (and did the same for the render-bound-exceeded copy for consistency), matching the plan's own single-line example code.
- **Files modified:** `frontend/src/routes/TemplateDetailRoute.tsx`
- **Verification:** `grep -c` for the exact sentence returns 1
- **Committed in:** `afb0b24`

**3. [Rule 1 - Bug] Used the native value-setter in tests instead of direct `.value` assignment**
- **Found during:** Task 3, first test run (2 failures)
- **Issue:** Setting `.value` directly on the controlled `<textarea>`/rename `<input>` and dispatching a plain `Event('input', ...)` left React's internal value tracker believing nothing had changed, so `onChange` did not fire and the dirty/rename assertions failed.
- **Fix:** Added a `setNativeValue` helper that calls the underlying `HTMLTextAreaElement`/`HTMLInputElement` prototype's `value` setter before dispatching the `input` event - the standard workaround for simulating user input against React-controlled elements without `@testing-library/react`'s `fireEvent`.
- **Files modified:** `frontend/tests/components/TemplateDetailRoute.test.tsx`
- **Verification:** All 14 tests pass; the pre-existing `AddToReleaseDialog.test.tsx` pattern (direct `.value` assignment) was left untouched since it already passes there.
- **Committed in:** `b3c87bb`

---

**Total deviations:** 3 auto-fixed (2 bugs in application code, 1 bug in test code)
**Impact on plan:** All three were necessary for the plan's own stated gates (`npm run typecheck`/`build` exit 0, the acceptance-criteria greps, and a passing test suite) to be achievable at all. No behavior beyond what the plan specified was added.

## Issues Encountered

`frontend/node_modules` did not exist at session start in this worktree; `npm ci` installed the
167 pinned dependencies before any verification command could run. No further environment
issues.

## Known Stubs

Both are explicitly scoped to later plans by this plan's own action text, not omissions:

- **Chrome & Logo tab body** (`TemplateDetailRoute.tsx`, the `tab === 'chrome'` branch) renders a
  single placeholder paragraph ("Chrome and logo settings.") with a
  `{/* plan 04-10 replaces this with the six chrome fields and the logo control */}` comment.
  Plan 04-10 fills this in.
- **Preview render region** (`TemplateDetailRoute.tsx`, `data-testid="preview-content"`) is an
  empty `<div>`. Plan 04-09 fills it with the Mustache+Markdown render.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The preview pane's render region (`data-testid="preview-content"`) is the exact seam plan
  04-09 fills; `previewReleaseId` and `useRenderContext(releaseId)` (from `templates.ts`, unused
  by this plan) are both already available for it to consume.
- The Chrome & Logo tab's placeholder paragraph is the exact seam plan 04-10 fills with the six
  structured fields and the logo control; `chrome`/`setChrome` state and its inclusion in the
  dirty/save wiring are already in place and untouched by that future work.
- `bodyRef` and `selectionRef` (last-cursor-position tracking on the body textarea) are already
  wired per plan 04-11's stated need, even though nothing in this plan reads them yet.
- No blockers identified.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

Both created files verified present on disk (`frontend/src/routes/TemplateDetailRoute.tsx`,
`frontend/tests/components/TemplateDetailRoute.test.tsx`). Both task commits (`afb0b24`,
`b3c87bb`) verified present in `git log`. `npm run typecheck` exits 0, `npm test` exits 0
(89/89, including the 14 new tests), `npm run build` exits 0. Every acceptance-criteria grep
from the plan's three tasks passes.
