# Phase 16: Portfolio Dashboard & Assistant Answers - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-22
**Phase:** 16-portfolio-dashboard-assistant-answers
**Areas discussed:** Dashboard layout & product filter, Client x product matrix, What "behind" means, Summary tiles + Need paperwork

---

## Dashboard layout & product filter

| Question | Options | Selected |
|---|---|---|
| Where portfolio views live | Dashboard tabs (rec.) / New sections below / Sections above | Dashboard tabs |
| What the filter narrows | Everything (rec.) / Portfolio views only / Release matrix + tiles only | Everything |
| Single or several products | Several (multi-select) / One at a time (rec.) | Several (multi-select) |
| Releases grouped by product | Per-product summary rows (rec.) / Group headers in the matrix / Both | Per-product summary rows |

## Client x product matrix

| Question | Options | Selected |
|---|---|---|
| Cell content | Release + date + flags (rec.) / Release only / Status colour + release | Release + date + flags |
| Empty cells | Distinguish not-linked vs Not installed (rec.) / Only linked products | Distinguish both |
| Archived | Hidden with toggle (rec.) / Always shown labelled / Never shown | Hidden, with a toggle |
| Click | Go to Portfolio client detail (rec.) / Inline install history / No interaction | Go to Portfolio client detail |

## What "behind" means

| Question | Options | Selected |
|---|---|---|
| Newest installable release | Newest in Production (rec.) / Newest frozen / Newest frozen, Production preferred | Newest frozen |
| Ordering | By date reached (rec.) / By version number | By date (frozen date) |
| Distance | Count of newer releases (rec.) / Just behind/current / Time behind | Count of newer releases |
| Linked, not installed | Separate group (rec.) / Count as behind / Exclude | Separate group |

## Summary tiles + Need paperwork

Before the discussion, the user said they did not understand what the "Need paperwork" tile reflects.

| Question | Options | Selected |
|---|---|---|
| Need paperwork fix | Rename + tooltip + clickable (rec.) / Rename + tooltip only / Replace with per-document counts | Rename + tooltip + clickable |
| Stale | Same treatment (rec.) / Merge into one tile / Leave as is | Same treatment |
| New Portfolio tiles (multi) | Clients behind / Not installed / Active products-clients / Installs last 30 days | All four |

## Claude's Discretion

- ASST-11 assistant answers: not discussed. The user accepted the default of a live tool over `current_client_release`.
- Rolled-back-but-frozen release as a behind target; tooltip and filter-chip mechanics; endpoint shape.

## Deferred Ideas

- Phases 21 (License Keys) and 22 (GitHub Publishing & Hosted Deployment) were added to the roadmap mid-discussion (8989a71).
