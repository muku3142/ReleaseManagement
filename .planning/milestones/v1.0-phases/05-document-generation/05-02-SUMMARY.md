---
phase: 05-document-generation
plan: 02
subsystem: docs
tags: [mustache, jmustache, requirements, template-content]

# Dependency graph
requires:
  - phase: 04-template-authoring
    provides: seeded starter templates (runbook, approval) validated at startup through TemplateSeeder
provides:
  - Real, catalog-token-only content for the runbook starter's Deploy steps / Post-deploy validation / Rollback sections (DOC-03's only satisfying artefact)
  - Real, catalog-token-only content for the approval starter's Risk / Impact / Backout plan sections (DOC-04's only satisfying artefact)
  - DOC-02 requirement wording narrowed to component-only grouping, with epic/label recorded as out of scope
  - UX-01 formally resolved to v2 with three recorded grounds, ending two prior silent deferrals
affects: [05-03, 05-04, phase-05-requirements-coverage-gate, milestone-retrospective]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Starter template bodies are the sole satisfying artefact for DOC-03/DOC-04 under D-05-01 — content authored directly in Markdown, validated through TemplateSeeder's normal save/activate path at startup"

key-files:
  created: []
  modified:
    - src/main/resources/templates/starter-runbook.md
    - src/main/resources/templates/starter-approval.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "DOC-02 narrowed to component only (D-05-02) — epic and label recorded as out of scope in a dated REQUIREMENTS.md note, additive if wanted later"
  - "UX-01 resolved to v2 (D-05-11), not deferred a third time — its last concretely-scoped consumer (runbook deploy steps) became static text under D-05-01, the remaining candidate (item reordering) was already declined on reasoning that still holds, and no stored item order exists anywhere in the view-model or output for a reorder to feed"

patterns-established: []

requirements-completed: [DOC-03, DOC-04]

# Metrics
duration: 20min
completed: 2026-08-10
---

# Phase 05 Plan 02: Starter Content & Requirement Wording Summary

**Rewrote the runbook and approval starter templates with real, catalog-token-only deploy/validation/rollback and risk/impact/backout-plan content, and closed two outstanding requirement-wording debts: DOC-02 narrowed to component-only grouping, UX-01 resolved to v2 with recorded reasoning.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-08-10T16:04:05+03:00
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- `starter-runbook.md`'s three previously-stub sections (Deploy steps, Post-deploy validation,
  Rollback) now carry real, ordered, actionable content, including explicit rollback trigger
  conditions — this is DOC-03's only satisfying artefact under D-05-01, so it had to be real, not
  a placeholder.
- `starter-approval.md`'s three previously-stub sections (Risk, Impact, Backout plan) now carry
  real content, with the backout plan explicitly deferring to the runbook's Rollback section as
  authoritative rather than maintaining a second, divergent copy — this is DOC-04's only
  satisfying artefact under D-05-01.
- Both starters use only `{{release.name}}` and `{{release.version}}` beyond their existing
  tokens — both already exist in `PlaceholderCatalog`, so no `[[unknown: token]]` warning risk was
  introduced.
- The approval starter's opening pipe table (the 05-01 regression canary for the
  `flexmark-ext-tables` fix) was left untouched and still renders correctly.
- `.planning/REQUIREMENTS.md`: DOC-02 reworded to name only component as the alternative grouping
  dimension; UX-01 marked resolved and moved to v2 with a full dated decision note recording three
  grounds and the v2 revival shape.

## Task Commits

Each task was committed atomically:

1. **Task 1: Author real runbook content (DOC-03)** - `b167bf4` (feat)
2. **Task 2: Author real approval content (DOC-04)** - `8a68e43` (feat)
3. **Task 3: Trim DOC-02's wording and formally resolve UX-01 (REQUIREMENTS.md portion only)** - `d017d96` (docs)

**Plan metadata:** this file, committed separately per parallel-executor protocol (SUMMARY.md must
be committed before the worktree agent returns).

## Files Created/Modified

- `src/main/resources/templates/starter-runbook.md` - Deploy steps, Post-deploy validation and
  Rollback sections rewritten with real, ordered content and explicit rollback trigger conditions
- `src/main/resources/templates/starter-approval.md` - Risk, Impact and Backout plan sections
  rewritten with real content; backout plan defers to the runbook's Rollback section
- `.planning/REQUIREMENTS.md` - DOC-02 wording narrowed to component-only; UX-01 marked resolved
  and moved to v2; new dated "Grouping dimensions and UX-01 (resolved 2026-08-10)" note section
  added after the existing "Runbook and approval content" note; phase-mapping table row for UX-01
  updated; Coverage block's UX-01 parenthetical updated

## Decisions Made

- **DOC-02 narrowed to component only (D-05-02).** Epic needs a parent-link resolution step and
  label is a free-text multi-value field; neither has a real source document asking for it (the
  release manager's actual `Orchstro 4.2.0 Release Notes.docx` groups by component-shaped
  sections). Recorded as a dated REQUIREMENTS.md note, with both dimensions left additive for a
  later phase if ever wanted.
- **UX-01 resolved to v2 (D-05-11), not deferred a third time.** Verbatim reasoning recorded in
  REQUIREMENTS.md (also reproduced here per the plan's `<output>` instruction, so this summary and
  any later milestone retrospective can cite it without re-reading REQUIREMENTS.md):

  > **UX-01 moved to v2, decided rather than deferred again.** UX-01 was deferred from Phase 3
  > (D-03-04) and again from Phase 4 (D-04-04). D-05-11 required a decision this phase rather than
  > a third deferral. The decision is to move it to v2, on three grounds:
  >
  > 1. **Its last concretely-scoped consumer is gone.** D-05-01 made runbook deploy steps static
  >    template text, so there is no structured list of steps to reorder.
  > 2. **The remaining candidate was already declined on reasoning that still holds.** D-03-04
  >    declined item reordering because "the ordering that actually matters is the ordering inside
  >    a generated document, which is template-driven at generation time". That is now more true,
  >    not less: grouping is a per-template setting (D-05-04) and within-group order is
  >    deterministic.
  > 3. **Nothing would read the output.** No stored item order exists in the view-model, in any
  >    template, or in any generated document. Building a drag-and-drop reorder whose result
  >    nothing consumes is precisely the speculative work CLAUDE.md's engineering principles rule
  >    out.
  >
  > **What v1 does owe, and does deliver:** every interactive element Phase 5 adds —
  > readiness-pill links, Generate/Regenerate/Export buttons, the revision `<select>`, the
  > revision-history disclosure — is a native HTML element, keyboard-operable with no custom
  > gesture layer. **No drag interaction exists anywhere in v1**, so no keyboard equivalent is
  > owed.
  >
  > **If v2 revives it,** the smallest correct shape is recorded in `05-UI-SPEC.md`: a `position`
  > integer on `release_ticket_item`/`release_manual_item`, a reorder endpoint, and per-row
  > "Move up"/"Move down" buttons (`aria-label="Move {item} up"`) shipped *alongside* any drag
  > handle, never a drag-only control.

## Deviations from Plan

### Auto-fixed / Orchestrator-scoped adjustments

**1. [Parallel-execution constraint] ROADMAP.md edits (d) and (e) from Task 3 were NOT applied**

- **Found during:** Task 3
- **Issue:** The plan's Task 3 instructs editing both `.planning/REQUIREMENTS.md` and
  `.planning/ROADMAP.md`. This executor's parallel-execution instructions explicitly forbid
  modifying `.planning/ROADMAP.md` — the orchestrator owns that file and applies it after all
  worktree agents in the wave complete.
- **What was skipped:** Two edits inside `### Phase 5: Document Generation` only:
  - **Success criterion 1** — replace `by issue type by default and by component, epic, or label
    when the template specifies it` with `by issue type by default and by component when the
    template specifies it (narrowed by D-05-02 — epic and label are out of scope)`.
  - **Success criterion 8** — replace the drag-and-drop criterion in full with:
    ```
      8. UX-01 is explicitly resolved rather than deferred a fourth time: it is closed to v2 with recorded reasoning (D-05-11), and every interactive element this phase adds is a native, keyboard-operable HTML element with no drag interaction anywhere in its shipped surfaces
    ```
- **Action needed:** Orchestrator must apply these two edits to `.planning/ROADMAP.md` directly
  with Edit (not via the known-defective ROADMAP-updating CLI helper — it reports success without
  writing) after this worktree merges.
- **Files modified:** None (deliberately not touched)
- **Committed in:** N/A — not applied in this worktree

---

**Total deviations:** 1 (scope adjustment required by parallel-execution constraints, not a code
defect)
**Impact on plan:** REQUIREMENTS.md and ROADMAP.md will disagree about DOC-02's scope and UX-01's
status until the orchestrator applies the two skipped ROADMAP.md edits above. This is a known,
temporary, and fully specified gap — not a silent omission.

## Issues Encountered

None. The `Bash` tool in this sandboxed worktree rejects multi-command chains with redirects as
"too complex to verify" — all verification and git-safety commands were run as separate,
single-purpose invocations instead of composed pipelines. No functional impact.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- DOC-03 and DOC-04 are now satisfiable end-to-end: the seeded starters carry real, labelled
  content and both pass `TemplateSeederIT` (9/9) plus the full backend suite (427/427, unchanged
  baseline — no Java was touched by this plan).
- DOC-02's requirement wording now matches what D-05-02's implementation work (deferred to a later
  wave/plan in this phase) must actually build: component grouping only.
- UX-01 is closed for the whole project — no future phase needs to re-raise it.
- **Blocker for phase close:** `.planning/ROADMAP.md`'s Phase 5 success criteria 1 and 8 still
  read the pre-D-05-02/pre-D-05-11 wording. The orchestrator must apply the two edits documented
  above before the phase's requirements-coverage gate runs, or REQUIREMENTS.md and ROADMAP.md will
  visibly disagree.

## Self-Check: PASSED

- FOUND: `.planning/phases/05-document-generation/05-02-SUMMARY.md`
- FOUND: `src/main/resources/templates/starter-runbook.md`
- FOUND: `src/main/resources/templates/starter-approval.md`
- FOUND commit: `b167bf4`
- FOUND commit: `8a68e43`
- FOUND commit: `d017d96`

---
*Phase: 05-document-generation*
*Completed: 2026-08-10*
