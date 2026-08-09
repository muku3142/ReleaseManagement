---
phase: 04-template-authoring
plan: 09
subsystem: ui
tags: [react, mustache, markdown-it, tailwind-typography, xss, template-preview, vitest]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 08
    provides: "TemplateDetailRoute's authoring screen, its previewReleaseId state, and the empty data-testid=\"preview-content\" render region this plan fills"
  - phase: 04-template-authoring
    plan: 03
    provides: "RenderService, RenderContextMapper and TemplateScanner - the backend algorithms this plan's client-side markUnknownTokens mirrors"
  - phase: 04-template-authoring
    plan: 07
    provides: "frontend/src/api/templates.ts - useRenderContext, usePlaceholderCatalog, ReleaseView/ItemView/GroupView/PlaceholderEntry types"
provides:
  - "frontend/src/components/TemplatePreview.tsx - the client-side Mustache + Markdown renderer filling the preview pane"
  - "The two runtime rendering dependencies (mustache, markdown-it) and the @tailwindcss/typography plugin, D-04-09's only new frontend deps for this phase"
affects: [04-10, 04-11]

# Tech tracking
tech-stack:
  added:
    - "mustache 4.2.0 (+ @types/mustache 4.2.6) - client-side Mustache-spec-conformant render engine"
    - "markdown-it 15.0.0 - client-side CommonMark-conformant Markdown renderer, constructed with html:false"
    - "@tailwindcss/typography 0.5.20 - loaded via Tailwind 4's @plugin CSS directive, scoped to prose prose-gray max-w-none on exactly the rendered preview container"
  patterns:
    - "Client-side mirror of a server render pipeline: markUnknownTokens combines TemplateScanner's scope-stack (needed to resolve which catalog scope a token is checked against) with RenderService.markUnknownTokens's per-frame unknown flag (needed so a closing tag is blanked only when its own opening tag was unknown, not by recomputing validity against the scope active inside the section)"
    - "Single useMemo-keyed render pipeline (body, view, entries) so a keystroke costs exactly one bounded render, with a discriminated PreviewOutcome union (empty/loading/unavailable/rendered) replacing exception-based control flow for the mid-typing 'unparseable' case"

key-files:
  created:
    - frontend/src/components/TemplatePreview.tsx
    - frontend/tests/components/TemplatePreview.test.tsx
  modified:
    - frontend/package.json
    - frontend/package-lock.json
    - frontend/src/index.css
    - frontend/src/routes/TemplateDetailRoute.tsx

key-decisions:
  - "PlaceholderEntry's scope-push mapping (which SECTION tokens open a new catalog scope, e.g. items -> ITEM) is hand-mirrored as a small SCOPE_PUSHES constant rather than derived from the API response, because PlaceholderEntryResponse (the JSON the /api/templates/catalog endpoint actually serializes) omits the backend's pushes() field entirely - it is not on the wire for the client to read. The plan's own action text anticipated exactly this fixed five-entry mapping (items/groups/comments/commits/pullRequests), so this is not a divergence from spec, just confirmation that the hand-written map was the only option."
  - "Combined TemplateScanner's scope-stack (Java: scope resolution for validity checks) and RenderService.markUnknownTokens's Deque<Boolean> (Java: which closing tags to blank) into one TypeScript pass rather than two, since the client only needs the combined observable output (marked source), not the intermediate ScanResult the backend's two-stage pipeline produces for other callers (save-time warning collection)."
  - "Unsupported Mustache sigils (&, >, =) return { ok: false } (the neutral 'Preview unavailable' panel) rather than being silently passed through or crashing - these are exactly the constructs TemplateScanner rejects at save time (D-04-06's subset), so treating them as unparseable mid-typing is consistent with the Save button being the authoritative gate, not a new behavior invented for the client."

patterns-established:
  - "Pattern: when a client-side algorithm must reproduce two related backend algorithms' combined effect, write one pass tracking exactly the state each original algorithm needed (here: current scope for validity checks, plus an unknown-flag per open frame for the close-tag decision) rather than porting either backend class 1:1 - the correctness bar is matching the two classes' composed *output*."

requirements-completed: [TMPL-05, TMPL-06]

# Metrics
duration: ~50min
completed: 2026-08-09
---

# Phase 04 Plan 09: Client-Side Template Preview Renderer Summary

**A client-side Mustache (mustache.js) + Markdown (markdown-it) render pipeline filling the authoring screen's preview pane, with raw HTML inert from both directions, unknown placeholders chipped amber, and a document-like `prose` type scale scoped to exactly the rendered container - mirroring the backend's jmustache+flexmark posture (D-04-05, D-04-08) without a parity mechanism (D-04-10, accepted).**

## Performance

- **Duration:** ~50 min
- **Started:** 2026-08-09T22:40:00+03:00 (approx)
- **Completed:** 2026-08-09T23:30:00+03:00 (approx)
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- Added `mustache` (4.2.0) and `markdown-it` (15.0.0) as the phase's two allotted runtime
  rendering dependencies (D-04-09), plus `@tailwindcss/typography` loaded via Tailwind 4's
  `@plugin` CSS directive - no `tailwind.config.js` plugin array, matching this codebase's
  existing v4-only CSS-driven configuration.
- `TemplatePreview.tsx` builds a Mustache context that mirrors `RenderContextMapper` key-for-key
  (`release`/`items`/`groups`, each item capped to 200 for the client-side collection bound), then
  runs the same Mustache-render → Markdown-render → chip-substitution pipeline order as the
  backend's conceptual flow.
- **Raw HTML is inert from both directions**, proven by tests: `<script>` typed directly into the
  body, and `<img onerror=...>` arriving through an interpolated ticket title, both render as
  literal escaped text (`&lt;script&gt;`, `&lt;img`), never as executable markup. This rests on
  `markdown-it`'s `html: false` constructor option - the same posture flexmark takes on the
  backend, verified against the library's actual escaping behavior, not assumed from its docs.
- **An ampersand is escaped exactly once** (`Tom & Jerry` → `Tom &amp; Jerry`, never
  `&amp;amp;`) - `Mustache.escape` is overridden to identity so `markdown-it` is the only layer
  that ever HTML-escapes, matching D-04-05's exact rationale for why jmustache's own escaper is
  off on the backend.
- **Unknown placeholders render as `[[unknown: token]]` amber chips**, never blank (D-04-08). The
  marking logic (`markUnknownTokens`) is a client-side hybrid of two backend classes: it walks the
  body with `TemplateScanner`'s scope-stack shape (to know which catalog scope a token is checked
  against - `items` pushes `ITEM`, `groups` pushes `GROUP`, etc., a fixed mapping hand-mirrored
  because the API's `PlaceholderEntryResponse` never serializes the backend's `pushes()` field),
  combined with `RenderService.markUnknownTokens`'s per-frame unknown flag (so a closing tag is
  blanked only when its own opening tag was unknown, not by recomputing validity against the scope
  *inside* the section - see Deviations for a bug this distinction caught).
- **Unparseable syntax mid-typing degrades to a neutral panel**, never a crash or a red alert -
  unterminated tags, mismatched/unbalanced sections, and D-04-06's unsupported constructs
  (`{{{ }}}`/`{{& }}`, `{{> }}`, `{{= =}}`) all return `{ ok: false }` from the marking walk,
  rendering the exact "Preview unavailable — check your template syntax…" copy from the
  Copywriting Contract, with no `role="alert"`.
- **Empty string and empty collections are both falsy** for inverted sections, matching jmustache's
  `emptyStringIsFalse(true)` configuration - proven for both cases by dedicated tests, and true of
  mustache.js's default behavior with no extra configuration needed (JS's own `!value` truthiness
  already treats `""` and `[]`-emptiness the same way jmustache was explicitly configured to).
- The document type scale (`prose prose-gray max-w-none`) appears on exactly one element in the
  entire `frontend/src/` tree - the rendered preview `<div>` - and `dangerouslySetInnerHTML` appears
  in exactly one file, both asserted by acceptance-criteria greps.
- Wired into `TemplateDetailRoute.tsx` via `useRenderContext(previewReleaseId)` and
  `usePlaceholderCatalog()`, replacing the empty `data-testid="preview-content"` region plan 04-08
  left as this plan's seam.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the preview dependencies and the document type scale** - `235a575` (feat)
2. **Task 2: Build the TemplatePreview component** - `0760970` (feat)
3. **Task 3: Write the TemplatePreview test** - `f17827d` (test)

**Plan metadata:** committed separately by the orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md).

## Files Created/Modified

- `frontend/package.json`, `frontend/package-lock.json` - `mustache`/`markdown-it` runtime deps,
  `@types/mustache`/`@tailwindcss/typography` dev deps
- `frontend/src/index.css` - added `@plugin "@tailwindcss/typography";`
- `frontend/src/components/TemplatePreview.tsx` - the client-side render pipeline
- `frontend/src/routes/TemplateDetailRoute.tsx` - wired `TemplatePreview` into the preview pane's
  render region, added `useRenderContext`/`usePlaceholderCatalog` calls
- `frontend/tests/components/TemplatePreview.test.tsx` - 15 tests

## Decisions Made

- **`SCOPE_PUSHES` is a hand-written constant, not derived from the API.** `PlaceholderEntryResponse`
  (what `GET /api/templates/catalog` actually returns) omits the backend `PlaceholderEntry.pushes()`
  field entirely, so the client cannot discover at runtime which SECTION tokens open a new scope. The
  plan's own action text names the same fixed five-token mapping (`items`→ITEM, `groups`→GROUP,
  `comments`→COMMENT, `commits`→COMMIT, `pullRequests`→PULL_REQUEST), confirming this was always the
  intended approach rather than an oversight to fix.
- **One combined scan/mark pass, not two.** The backend runs `TemplateScanner.scan` (produces
  `ScanResult.unknownTokens`, used elsewhere for the save-time warning banner) and
  `RenderService.markUnknownTokens` (a separate pass that rewrites the source using that list) as two
  stages. The client only needs the final marked-source output, so `markUnknownTokens` here does both
  in one left-to-right walk - it tracks the scope stack (for validity checks, from `TemplateScanner`'s
  shape) and an `unknown` boolean per open frame (for the close-tag decision, from
  `RenderService.markUnknownTokens`'s `Deque<Boolean>` shape) simultaneously.
- **Unsupported sigils (`&`, `>`, `=`) return `{ ok: false }`.** These are exactly the constructs
  `TemplateScanner` rejects with `TemplateMalformedException` at save time. Since the live preview's
  only failure mode is the neutral "unavailable" panel (never an error), treating an in-progress
  unescaped-variable/partial/custom-delimiter construct as "can't parse yet" is consistent with the
  Save button remaining the authoritative gate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed a close-tag scope bug in `markUnknownTokens` found by its own tests**
- **Found during:** Task 3, first test run (7 of 14 failures)
- **Issue:** The initial implementation recomputed a closing tag's validity using `currentScope`
  (the scope stack's *current* top, i.e., the scope already pushed and active *inside* the section
  being closed) instead of tracking whether the section's own *opening* tag was unknown. For a valid
  section like `{{#items}}...{{/items}}`, this meant the close-tag check asked "is `items` a valid
  token in `ITEM` scope?" (no - `items` is a `ROOT` token) instead of "was `items`'s own open tag
  unknown?" (no - it's valid), which flipped the outcome and caused every test with any `{{#items}}`
  wrapper to fall through to `{ ok: false }` ("Preview unavailable" for perfectly well-formed
  templates).
- **Fix:** Added an `unknown: boolean` field to each stack frame, set once at open-time from the
  correct (enclosing) scope's validity check, and read - never recomputed - at close-time. This
  exactly mirrors `RenderService.markUnknownTokens`'s `Deque<Boolean> openSectionIsUnknown`, which
  the plan's own `<read_first>` list pointed at for this task.
- **Files modified:** `frontend/src/components/TemplatePreview.tsx`
- **Verification:** All 14 (then 15, after the ampersand test was added) tests pass; `npm run
  typecheck` and `npm run build` both exit 0.
- **Committed in:** `f17827d` (Task 3 commit - the fix and the tests that caught it were
  inseparable, so they landed together rather than as a separate task-3.5 commit)

**2. [Rule 2 - Missing Critical] Added an explicit ampersand double-escape test**
- **Found during:** Task 3, reviewing the plan's stated "central risk" section against the 13
  listed test cases
- **Issue:** The plan's `<the_central_risk_of_this_plan>` section explicitly calls out "an
  ampersand is escaped exactly once" as one of three behaviors this plan's only defence rests on,
  and the executor's own success criteria require "a test proves an ampersand is escaped exactly
  once (asserts `&amp;amp;` is ABSENT)" - but the plan's Task 3 action text's 13 enumerated test
  cases do not include this specific assertion.
- **Fix:** Added a 15th test, `escapes an ampersand in an interpolated value exactly once`,
  asserting `container.innerHTML` contains `Tom &amp; Jerry` and does not contain `&amp;amp;`.
- **Files modified:** `frontend/tests/components/TemplatePreview.test.tsx`
- **Verification:** Test passes; confirms `Mustache.escape` being a no-op plus `markdown-it`'s
  single escaping pass produces exactly the single-escape behavior D-04-05 requires.
- **Committed in:** `f17827d` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 bug in application code, found and fixed by the plan's own
test suite; 1 missing test explicitly called for by the plan's risk section and the executor's own
success criteria, not by the Task 3 action text's enumerated list)
**Impact on plan:** The scope bug would have made the preview pane show "Preview unavailable" for
almost every template using `{{#items}}` or `{{#groups}}` - i.e., nearly every realistic template -
so this was necessary for TMPL-06 to actually work, not an unrelated fix. The added test closes a
gap between the plan's stated risk and its enumerated test list with no functional code change.

## Issues Encountered

`frontend/node_modules` did not exist at session start in this worktree; `npm install` resolved and
installed all 180 packages (baseline plus the two new runtime deps and two new dev deps) before any
verification command could run. `npm audit` reports one pre-existing high-severity advisory in
`nanoid` (transitive via `vite` → `postcss`, unrelated to this plan's dependency additions and
present before this plan touched `package.json`) - left untouched per the deviation rules' scope
boundary (not directly caused by this task's changes).

## Known Stubs

None. The preview pane is fully wired to real release data via `useRenderContext`/
`usePlaceholderCatalog`; no hardcoded empty values or placeholder text remain in the render path.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `TemplatePreview` renders the body region only; plan 04-10 extends it with the cover
  block/header/footer chrome strips around the same preview pane, per this plan's own scope note
  ("Chrome rendering — the cover block and the header/footer strips — is plan 04-10's; this
  component renders the body region only, and plan 04-10 extends it").
- `frontend/src/index.css`'s `@plugin "@tailwindcss/typography";` line and the `prose prose-gray
  max-w-none` container are the exact seam plan 04-10's cover-block styling (which UI-SPEC says
  should also use `prose`-scale sizing for the cover title/subtitle) will sit alongside, not
  replace.
- No blockers identified. D-04-10's accepted drift risk (two independent render implementations)
  stands as recorded: no golden-fixture parity test or server verify action was built, per the
  plan's explicit instruction not to.

## Threat Flags

None beyond the plan's own `<threat_model>` (T-04-46 through T-04-51), which this plan's
implementation satisfies directly:
- T-04-46 (XSS via `dangerouslySetInnerHTML`) - mitigated by `markdown-it`'s `html: false`, proven
  from both the template-body and interpolated-value directions.
- T-04-47 (XSS via the hand-built chip regex) - mitigated by operating only on `markdown-it`'s
  already-escaped output.
- T-04-48 (XSS via a mustache.js lambda/prototype property) - mitigated by `toContext` building a
  plain JSON object with no functions.
- T-04-49 (DoS via unbounded rendering) - mitigated by `PREVIEW_ITEM_CAP` + the single `useMemo`.
- T-04-50 (preview/generation divergence) - accepted per D-04-10, no new mitigation attempted.
- T-04-51 (supply-chain risk) - exactly two new runtime deps added, pinned to exact versions, no
  third renderer crept in (asserted by an acceptance-criteria grep).

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

Both created files verified present on disk (`frontend/src/components/TemplatePreview.tsx`,
`frontend/tests/components/TemplatePreview.test.tsx`). All three task commits (`235a575`,
`0760970`, `f17827d`) verified present in `git log`. `npm run typecheck` exits 0, `npm test` exits 0
(104/104, including the 15 new tests: 89 baseline + 15), `npm run build` exits 0. Every
acceptance-criteria grep from the plan's three tasks passes.
