---
phase: 02-jira-ticket-browsing
plan: 06
subsystem: ticket-browsing-ui
tags: [react, tanstack-query, status-filter, accessibility, aria-live, jira-06]
dependency-graph:
  requires:
    - "01-07: apiFetch + ApiError + messageForReason/REASON_MESSAGES"
    - "02-02: TicketsRoute filter bar, useJiraProjects, useJiraVersions"
    - "02-05: POST /api/tickets/search and the TicketResponse shape"
  provides:
    - "frontend/src/api/tickets.ts: useTicketSearch mutation, Ticket type, StatusCategory vocabulary, DEFAULT_STATUS_CATEGORIES"
    - "The /tickets ticket table with per-row selection, select-all, and an announced counter"
    - "Loading / empty / error result states with a working Try again"
  affects:
    - "02-07: adds the expand-row toggle and detail panel onto this table"
    - "Phase 3 release assembly: consumes the selected ticket keys this screen produces"
tech-stack:
  added: []
  patterns:
    - "A filter default that changes what the user sees is asserted by a unit test, so widening it later fails loudly instead of quietly"
    - "JIRA-06's error-vs-empty distinction is carried structurally (role=alert + red vs no role + grey), not only by wording"
    - "An empty-result message names the active filter that most likely caused it, rather than listing every filter generically"
    - "indeterminate must be set through a ref — it is a DOM property, not an HTML attribute, so JSX cannot pass it"
    - "Every row checkbox carries its own aria-label naming the row's subject; an unlabelled one is announced as just 'checkbox'"
key-files:
  created:
    - frontend/src/api/tickets.ts
    - frontend/src/api/tickets.test.ts
  modified:
    - frontend/src/routes/TicketsRoute.tsx
decisions:
  - "The status filter is three checkboxes, not a <select multiple>: the vocabulary is three fixed values and a combination like 'Done and In Progress' has to be readable at a glance"
  - "Clearing all three status checkboxes sends [] (every status) and is never re-defaulted to Done — a control that silently overrides its user is worse than no control. Pinned by an acceptance-criteria grep asserting the re-default anti-pattern is absent"
  - "statusCategories is sent only on the picker path, never with raw JQL, because the backend forwards raw JQL verbatim and implying an unapplied filter would be a lie"
  - "The empty-state copy interpolates the resolved project key and version name plus the active status filter, never raw internal ids"
  - "No action button for the selected set — Phase 3 owns the consumer, and adding one now would be a dead affordance"
metrics:
  duration_minutes: 0
  tasks_completed: 3
  files_created: 2
  completed_date: "2026-08-04"
---

# Phase 02 Plan 06: Ticket Table & Status Filter Summary

The slice the phase exists for: pick a project and fix version, click **Load tickets**, get the complete matching set in a table, and check the ones that belong in a release.

## What Was Built

**Task 1 — Typed ticket-search client.** `frontend/src/api/tickets.ts` exposes `useTicketSearch()` plus the `Ticket` type, the `STATUS_CATEGORIES` vocabulary with its display labels, and `DEFAULT_STATUS_CATEGORIES = ['DONE']`. `toSearchBody` is exported separately so the request-shaping logic is unit-testable without rendering anything, and it narrows a discriminated union to exactly one filter mode rather than sending optional fields the backend would reject. `statusCategories` is attached only on the picker path. Seven vitest tests, two of which pin behaviour that regresses silently: an empty ticket list resolves as **success** (JIRA-06's error-vs-empty distinction proven at the client boundary), and an explicitly empty status set survives as `[]` rather than being dropped and re-defaulted server-side.

**Task 2 — Load button, status filter, three result states.** The status filter is a three-checkbox `fieldset` in the filter row. Unchecking all three is a legitimate "every status" request and is passed through as `[]`. The loading state is a CSS border-spin plus the UI-SPEC copy verbatim, and all filter controls carry `disabled={search.isPending}` so rapid clicking cannot overlap two exhaustive multi-page fetches. The empty state is a grey panel with **no** `role`; the error state is `role="alert"` with red text and a **Try again** that calls the same `runSearch` so the retry re-runs the identical search instead of making the release manager re-pick filters. The empty-state copy names the active status filter, because with a Done-only default the usual cause of zero results is unfinished work rather than a wrong fix version. Seven text columns, `null` resolution/assignee rendered as an em dash.

**Task 3 — Selection, select-all, announced counter.** Per-row checkboxes each carry `aria-label="Select ticket {key}"`. The header checkbox selects or clears all loaded rows and shows a true `indeterminate` state in between, set through a ref because `indeterminate` is a DOM property rather than an HTML attribute. Selecting all is unambiguous here because the complete matching set is already client-side — there is no "all across pages" question. The `{n} selected` counter sits in a `role="status" aria-live="polite"` region (UI-SPEC binding follow-up 3), so assistive-technology users get the count at the same moment sighted users do. Selection clears whenever a new result set arrives.

## Verification

- `npx tsc -b` — exit 0. `npm run build` — exit 0. `npx vitest run` — **22 tests, 4 files, all green** (15 before this plan; +7).
- Backend untouched by this plan and unchanged at **207 green**.
- All task 1 criteria pass, including `fetch(` = 0 (no raw fetch bypassing `apiFetch`'s CSRF echo and silent refresh).
- All task 2 criteria pass, including the absence assertions: `font-medium` = 0, `~` = 0 (no advisory-count copy, since none is fetched), and the status re-default anti-pattern = 0.
- All task 3 criteria pass, including `dangerouslySetInnerHTML` = 0 and `Create release|Add to release` = 0.
- Commits: `3afe3e9` (Task 1), `96ea98e` (Task 2), `4a47b74` (Task 3), `e5565d4` (counter-visibility fix).

## Deviations from Plan

1. **Plan amended before execution** to add the JIRA-07 status filter (commit `425937b`) — the filter control, the `statusCategories` payload, the `statusHint` empty-state copy, and two extra unit tests.
2. **One acceptance criterion in the amended plan was corrected during execution.** It required `DEFAULT_STATUS_CATEGORIES` to appear exactly once in `TicketsRoute.tsx`, which is unachievable while importing the constant — an import plus a `useState` seed is two occurrences. Corrected to `2` in the plan; the intent (the default comes from the shared constant, never a local literal) is unchanged and satisfied.
3. **Counter visibility tightened beyond the plan** (`e5565d4`). The plan guarded the counter on `loaded !== null && loaded.length > 0`, but the table is additionally guarded on `!search.isPending`. During a re-search that left a stale count on screen with no rows to explain it. Added `!search.isPending` to the counter so it hides with its table.

## Superseded After Execution (2026-08-04, commits `822dcd4`, `567d03b`)

1. **Fix version became optional** at the user's request, after a live run against the real tenant showed the picker empty for a project with no Jira Versions defined. `TicketSearchCriteria`'s picker variant is renamed `mode: 'project'` with `fixVersionId: string | null`, `toSearchBody` omits the field entirely when absent, `Load tickets` enables on a project alone, the label reads "Fix version (optional)", and the placeholder becomes "All fix versions" — or "No versions in this project" when Jira returned none, so an empty dropdown no longer looks like a failed load. The empty-state copy uses a `scopeDescription` that degrades to just the project key when no version is selected. One test added (`omits fixVersionId entirely when no version is selected`); frontend total 22 → 23.
2. **Tests moved out of `src/`** into a mirrored `frontend/tests/` tree at the user's request, so `src/` is production-only. `vite.config.ts` gained an explicit `test.include`, and `tsconfig.json` includes `tests` so they are still typechecked. `frontend/src/api/tickets.test.ts` is now `frontend/tests/api/tickets.test.ts`.
3. **Fix version replaced by sprint** (commit `f501a30`), at the user's direction — "this is the core for release management". The fix-version picker, `useJiraVersions`, `listVersions`, `GET /api/jira/projects/{id}/versions` and their tests are gone; `useJiraSprints` and `GET /api/jira/projects/{id}/sprints` replace them, and the criteria carry `sprintId` composing `sprint = <id>`. This brings Jira's **Agile API** into scope, reversing part of a recorded PROJECT.md exclusion — annotated there rather than absorbed silently.
4. **Table widened** — the route's `max-w-5xl` cap was cutting the eight-column table off, and the Summary cell truncated with an ellipsis. The cap is removed (`w-full`) and Summary now wraps; the other columns are `whitespace-nowrap` so they don't wrap mid-value. This supersedes plan 07's `text-ellipsis` = 1 acceptance criterion, which now reads 0 — a release manager choosing tickets has to be able to read the whole summary.

## Issues Encountered

None.

## Known Stubs

The **"Advanced: edit as JQL"** escape hatch from D-02-01 is referenced in the empty-state copy but the control itself does not exist yet — `useTicketSearch` already accepts `{mode: 'jql'}` and the backend already serves it, so only the UI affordance is missing. **Plan 07 task 2 builds it**, closing JIRA-02's third filter dimension; no follow-up outside this phase is needed.

The expand-row toggle and the description/comments detail panel are plan 07's work; the table's first column is a checkbox and there is deliberately no expand column yet.

## Threat Flags

`T-02-06-01` (XSS via Jira-sourced text) is mitigated structurally — every value is a React text child, and `dangerouslySetInnerHTML` is grep-asserted absent. `T-02-06-03` (overlapping exhaustive searches) is mitigated by `disabled={search.isPending}` on every control that can start one. `T-02-06-05` holds: only resolved keys and names reach user-facing copy, never raw ids.

## Self-Check: PASSED

Both created files and the one modified file verified on disk. All four commits verified in `git log`. Typecheck, build and test all re-run green after the final fix.
