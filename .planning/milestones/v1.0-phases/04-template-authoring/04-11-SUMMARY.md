---
phase: 04-template-authoring
plan: 11
subsystem: ui
tags: [react, tanstack-query, non-modal-drawer, accessibility, placeholder-catalog]

# Dependency graph
requires:
  - phase: 04-template-authoring (plan 01)
    provides: PlaceholderCatalog.ENTRIES served at GET /api/templates/catalog, and the PlaceholderEntry shape
  - phase: 04-template-authoring (plan 08)
    provides: The two-pane authoring screen, bodyRef, rememberSelection and the { field, start, end } selection state
  - phase: 04-template-authoring (plan 10)
    provides: ChromeFieldsForm's four chrome text inputs and rememberChromeSelection
provides:
  - PlaceholderDrawer.tsx - non-modal slide-in catalog, grouped by scope, client-side filter, amber Phase-6 caution tag
  - TemplateDetailRoute wiring - toolbar toggle, insert-at-cursor into body or any chrome field, keyboard-equivalent insertion
affects: [phase-05-document-generation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Non-modal absolutely-positioned overlay (not <dialog>) for a transient, editor-adjacent panel - first instance of this pattern in the codebase, alongside the existing modal <dialog> convention"
    - "Last-focused-field tracking via a ref (not live DOM selection) so a control outside the field (the drawer) can still act on it after focus has moved away"
    - "Locating a sibling component's rendered form fields by DOM position (querySelector order) rather than forwarding refs through it, to avoid modifying a file outside this plan's declared scope"

key-files:
  created:
    - frontend/src/components/PlaceholderDrawer.tsx
    - frontend/tests/components/PlaceholderDrawer.test.tsx
  modified:
    - frontend/src/routes/TemplateDetailRoute.tsx
    - frontend/tests/components/TemplateDetailRoute.test.tsx

key-decisions:
  - "Row buttons carry an explicit onKeyDown Enter/Space handler in addition to native onClick, because jsdom's test environment does not simulate a browser's default click-on-Enter/Space button activation - verified empirically before writing it. Real browsers already do this natively; the handler is a harmless, preventDefault-guarded no-op duplicate there."
  - "Chrome-field refocus locates the target <input type=\"text\"> by its fixed position within the editor pane (via a container ref) rather than forwarding refs through ChromeFieldsForm.tsx, since that file is not in this plan's files_modified list and LogoUpload contributes no competing text inputs to the same pane."

patterns-established:
  - "Pattern: when a plan's own action block assumes browser default-activation behavior that a jsdom test cannot exercise, verify empirically (small throwaway probe, deleted afterward) before either trusting it or adding a defensive handler - do not guess."

requirements-completed: [TMPL-03]

# Metrics
duration: 40min
completed: 2026-08-10
---

# Phase 04 Plan 11: Placeholder Catalog Drawer (Tasks 1-2 of 3) Summary

**Non-modal placeholder catalog drawer that overlays the preview pane, with insert-at-cursor into the body textarea or any of the four chrome fields, dismissible four ways and fully keyboard-operable — task 3's live-tenant checkpoint is explicitly deferred to the user.**

## Performance

- **Duration:** 40 min (tasks 1-2 only; task 3 not attempted)
- **Started:** 2026-08-10T12:34:00+03:00
- **Completed:** 2026-08-10T14:41:31+03:00
- **Tasks:** 2 of 3 (task 3 is a blocking human-verify checkpoint, deferred to the user per this run's explicit scope)
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- `PlaceholderDrawer.tsx`: an absolutely-positioned, non-`<dialog>` panel (D-04-21) — three collapsible sections (Release/Item/Group fields), a client-side substring filter, real `<button>` rows for native focus/Enter/Space activation, the amber "Always empty until Phase 6" tag reserved for `commits`/`pullRequests` (D-04-18), an `aria-live="polite"` insertion announcement, and four dismissal routes (×, toggle, Escape, outside click)
- `TemplateDetailRoute.tsx`: a toolbar "Placeholders" toggle (blue-tinted while open), the two-pane region made `relative` so the drawer anchors to its right edge and overlays only the preview pane, and `insertToken`/`focusAfterInsert` implementing UI-SPEC's insert-at-cursor mechanism exactly — splice at the last recorded caret of whichever field (body or any chrome input) was last focused, refocus that field, and place the caret immediately after the inserted text once React has committed the value (`requestAnimationFrame`)
- 12 new drawer tests (grouping, tags, description copy, click insertion, keyboard (Enter) insertion, live-region announcement, filtering, all four dismissal routes, non-modal assertion, loading state) plus 2 new route-level tests (toolbar toggle open/close + tint, and a real caret-splice integration test against a live textarea)
- Frontend suite green throughout: 121 → 135 tests (14 → 15 files), `npm run typecheck` and `npm run build` both clean

## Task Commits

Each task was committed atomically:

1. **Task 1: Build the non-modal placeholder drawer** - `0acb57d` (feat)
2. **Task 2: Wire the toolbar toggle and insert-at-cursor, and write the drawer test** - `b372478` (feat)

**Task 3 (checkpoint:human-verify, gate="blocking") was NOT executed in this run** — see "Deferred: Task 3" below.

**Plan metadata:** committed separately by orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md)

## Files Created/Modified
- `frontend/src/components/PlaceholderDrawer.tsx` - the drawer component (created)
- `frontend/tests/components/PlaceholderDrawer.test.tsx` - 12 tests covering the drawer in isolation (created)
- `frontend/src/routes/TemplateDetailRoute.tsx` - toolbar toggle, relative two-pane region, drawer render, `insertToken`/`focusAfterInsert`/`chromeFieldElement` (modified)
- `frontend/tests/components/TemplateDetailRoute.test.tsx` - added a `CATALOG` fixture and 2 tests: toggle open/close/tint, and a real caret-splice integration case (modified)

## Decisions Made
- Added an explicit `onKeyDown` handler on each drawer row (Enter/Space → insert, `preventDefault`) rather than relying solely on the native `<button>` element's default activation. Verified with a throwaway probe that jsdom (this project's Vitest environment) does not synthesize a click from a `keydown` of `Enter` on a focused button the way real browsers do — without the handler, the plan's own "no extra handler needed" claim about UX-01 keyboard equivalence would not be testable, and the acceptance criterion asking for `it('Enter dispatch calls onInsert')` would fail in this environment specifically (not in a real browser). The handler is additive and guarded, so real-browser behavior is unaffected.
- Chrome-field refocus after insertion locates the target `<input type="text">` by its fixed position within the editor pane's DOM (a container ref + `querySelectorAll('input[type="text"]')[index]`), rather than forwarding a ref map through `ChromeFieldsForm.tsx`. The plan's own interface note says `focusAfterInsert` needs "a ref map for the four chrome inputs," but `ChromeFieldsForm.tsx` is not in this plan's `files_modified` frontmatter list. Since `LogoUpload` (the only other child rendered in that pane) contributes exactly one hidden file input and no visible text inputs, the four chrome fields are reliably and exclusively the four `input[type="text"]` elements in that pane, in the fixed order `ChromeFieldsForm` always renders them (`coverTitle`, `coverSubtitle`, `headerText`, `footerText`). This keeps the plan's declared file scope exact while still satisfying "insert-at-cursor must work into chrome fields too."

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed a literal `showModal` substring from a code comment**
- **Found during:** Task 1, running the plan's own acceptance-criteria greps against the finished file
- **Issue:** The plan's suggested explanatory comment ("That rules out `<dialog>+showModal()`...") contains the literal text `showModal`, which the plan's own acceptance criterion `grep -c "showModal" ... returns 0` would then fail against — the plan's action block and its acceptance criteria were mutually inconsistent as written.
- **Fix:** Reworded the comment to describe the modal native `<dialog>` element without using the literal word `showModal`, preserving the same explanatory intent.
- **Files modified:** `frontend/src/components/PlaceholderDrawer.tsx`
- **Verification:** `grep -c "showModal"` and `grep -c "<dialog"` both now return 0, per acceptance criteria
- **Committed in:** `0acb57d` (Task 1 commit)

**2. [Rule 3 - Blocking] jsdom test environment does not fire native browser button-activation on Enter/Space keydown**
- **Found during:** Task 1/2, before writing the "keydown Enter activates a row" test
- **Issue:** Verified empirically (throwaway probe, deleted before committing) that dispatching a `keydown` `Enter` on a focused `<button>` in this project's jsdom test environment does not produce a `click`, unlike every real browser. The plan's acceptance criteria require a passing test for exactly this behavior.
- **Fix:** Added an explicit, `preventDefault`-guarded `onKeyDown` handler on each drawer row that calls `insert()` for `Enter`/`Space`, documented inline with the reasoning above.
- **Files modified:** `frontend/src/components/PlaceholderDrawer.tsx`
- **Verification:** `it('calls onInsert when Enter is dispatched on a focused row')` passes; full suite green.
- **Committed in:** `0acb57d` (Task 1 commit)

**3. [Rule 3 - Blocking] React's `onSelect` prop is not a direct alias for the native `select` DOM event**
- **Found during:** Task 2, writing the caret-splice integration test
- **Issue:** A raw `dispatchEvent(new Event('select', ...))` on the body textarea did not reach the component's `onSelect={rememberSelection}` handler in jsdom, because React's `onSelect` is synthesized from several other native events, not a 1:1 native-event listener. The insertion landed at the end of the body instead of at the intended caret offset.
- **Fix:** Rewrote the test to seed the "last focused field" state via `focus()` + `setSelectionRange()` + `blur()` instead — `onBlur` shares the same `rememberSelection` handler and is a standard native event React maps directly, so this is deterministic. No production code changed; this was a test-authoring correction.
- **Files modified:** `frontend/tests/components/TemplateDetailRoute.test.tsx`
- **Verification:** The caret-splice test now asserts the exact spliced value (`'01234{{release.name}}56789'`) as required.
- **Committed in:** `b372478` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed, all Rule 3 (blocking — each would have left an untestable or self-contradictory acceptance criterion). No scope creep; no architectural changes.

## Deferred: Task 3 (NOT executed)

**Task 3 — `<task type="checkpoint:human-verify" gate="blocking">` "Verify template authoring against the live tenant" — was intentionally NOT attempted in this run.**

Per this run's explicit scope, task 3 requires a real Jira tenant (`https://evisionegypt.atlassian.net`, project `CNV`), a real API token, and a human walking through twelve manual verification steps (starters, live preview against real data, the catalog drawer, insert-at-cursor by mouse and keyboard, unknown-placeholder handling, malformed-template rejection, raw-HTML inertness, chrome/cover/footer rendering, logo upload validation, template lifecycle, unsaved-changes handling, and responsive layout). This is explicitly the user's own verification pass against their live tenant, not something this executor can or should perform.

**This plan (04-11) is therefore NOT fully complete.** Tasks 1 and 2 (the drawer component and its wiring) are done, committed, and verified by automated tests and builds. The plan's own `<verification>` block requirement that "the human verification checkpoint is approved" remains outstanding until the user completes task 3 and reports back (approval or itemized deviations by step number).

## Issues Encountered
None beyond the three auto-fixed deviations above, all resolved within the task they were found in.

## User Setup Required
None from tasks 1-2. Task 3, when the user performs it, requires the app running against the real tenant (`stub-jira` profile off) with a valid Jira API token — see the plan's own task 3 `<read_first>` and `<what-built>` for the exact preparation steps (a `CNV`-sourced release containing both a ticket-backed item and a manual item).

## Next Phase Readiness
- The placeholder catalog drawer and insert-at-cursor mechanism are code-complete and test-covered; nothing further is needed from a future plan to satisfy TMPL-03's UI surface.
- Phase 5 (document generation) can proceed once phase 4 as a whole is marked complete — that depends on task 3's live-tenant sign-off, which is outside this run's scope and is the one remaining gate on phase 4.
- No blockers identified in the code itself.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-10 (tasks 1-2 only; task 3 deferred to user)*

## Self-Check: PASSED

All 4 created/modified source files (`PlaceholderDrawer.tsx`, `PlaceholderDrawer.test.tsx`, `TemplateDetailRoute.tsx`, `TemplateDetailRoute.test.tsx`) and this SUMMARY.md verified present on disk. Both commit hashes (`0acb57d`, `b372478`) verified present in `git log --all`. Full frontend suite green: 135/135 tests across 15 files, `npm run typecheck` and `npm run build` both exit 0.
