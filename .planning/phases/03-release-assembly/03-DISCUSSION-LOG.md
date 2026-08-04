# Phase 3: Release Assembly - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-04
**Phase:** 03-release-assembly
**Areas discussed:** Assembly flow & manual items

---

## Area selection

Four gray areas were offered. The release manager selected **one** and delegated the rest.

| Area | Offered because | Selected |
|------|-----------------|----------|
| Application dimension | `.planning/PROJECT.md:99` parked this decision for exactly this phase | |
| Freeze & snapshot depth | REL-03's "snapshots its exact ticket set" is ambiguous between keys-only and a full field copy; drives DOC-07 | |
| Identity & lifecycle | Shape of "named, versioned"; sprint coupling; freeze reversibility; deletability under append-only | |
| Assembly flow & manual items | How tickets reach a release from the existing `/tickets` screen; REL-08's category/ordering shape | ✓ |

---

## Assembly flow & manual items

### Q1 — How does a ticket get from Jira onto a release?

| Option | Description | Selected |
|--------|-------------|----------|
| Push from `/tickets` | Browse on the existing screen, tick the checkboxes already implemented (`TicketsRoute.tsx:20`), "Add to release" opens a picker. Reuses everything Phase 2 built | ✓ |
| Pull from the release | A ticket picker embedded in the release detail screen — project/sprint/JQL filters duplicated there, or Phase 2's table extracted into a shared component | |
| Both | Push for bulk sprint-scale adds, pull for one-offs. Roughly the work of the other two combined | |

**User's choice:** Push from `/tickets`
**Notes:** No embedded picker inside the release screen. → D-03-01

### Q2 — What does the "Add to release" picker offer?

| Option | Description | Selected |
|--------|-------------|----------|
| Existing open releases only | Frozen releases greyed or hidden; releases are created on a separate screen first. One job per screen | |
| Open releases + create new inline | Same list plus "New release…" taking name/version inline, creating and adding in one step | ✓ |
| You decide | Claude picks based on keeping the REST surface honest | |

**User's choice:** Open releases + create new inline
**Notes:** Recorded with a planner constraint — create-and-add must be one transaction so a failed add cannot orphan an empty release. → D-03-02

### Q3 — What is REL-08's manual-item `category`?

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed enum, small | Closed list in code: CONFIG_CHANGE / DATABASE_SCRIPT / INFRASTRUCTURE / OTHER. Reliable for DOC-02 grouping, no admin screen | ✓ |
| Free-text label | Zero constraint, but grouping fragments on typos ("DB script" vs "Database script") | |
| Enum now, admin-managed later | Enum values in a lookup table so a future screen needs no schema change. Costs a join and a table nobody edits in v1 | |

**User's choice:** Fixed enum, small
**Notes:** → D-03-03

### Q4 — Do release items carry a user-controlled order in this phase?

| Option | Description | Selected |
|--------|-------------|----------|
| No order — auto-sorted | Deterministic sort; grouping stays template-driven at generation time per DOC-02. No position column, no drag-and-drop | ✓ |
| Explicit position, drag to reorder | Position column, reorder endpoints, plus the keyboard-accessible equivalent UX-01 mandates, plus rebalancing on insert/remove | |
| You decide | Claude weighs it against what Phase 5 actually needs | |

**User's choice:** No order — auto-sorted
**Notes:** Consequence made explicit and accepted — UX-01's "moving items into or between
groupings on a release" is therefore unmet after Phase 3 and lands in Phase 5. → D-03-04

---

## Claude's Discretion

Three areas were delegated. Each was decided with reasoning recorded in CONTEXT.md so it can be
overridden at plan time.

| Area | Decision taken | CONTEXT ref |
|------|----------------|-------------|
| Application dimension | No `application` entity in v1; an advisory nullable `jira_project_key` on the release, used for library grouping only, never enforced. Answers the question `.planning/PROJECT.md:99` parked | D-03-05 |
| Freeze & snapshot depth | Materialise the full `TicketDetail` field copy per item at add time; reading a release never calls Jira. Explicit refresh while OPEN, immutable once FROZEN; `last_refreshed_at` recorded for DOC-09 | D-03-06, D-03-07 |
| Identity & lifecycle | Name + version required free text, `UNIQUE (name, version)`, no semver validation; no sprint FK; exactly OPEN → FROZEN with **no unfreeze**; hard-delete allowed for OPEN only; actor recorded on every state change; duplicate adds idempotent | D-03-08 … D-03-13 |

---

## Deferred Ideas

- UX-01 release-item drag-and-drop → Phase 5 (consequence of Q4)
- `application` entity → only if a product spans several Jira projects
- Admin-managed manual-item categories → not planned; enum is fixed in code
- REL-06 / REL-07 remain v2 and must not be built here
- DOC-09 documentation-readiness columns on the release library → Phase 5
