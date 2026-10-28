# Phase 17: RICE Roadmap - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-22
**Phase:** 17-rice-roadmap
**Areas discussed:** RICE units & rubric, Item lifecycle & page, Jira import & refresh, Ranking table & CSV

---

## RICE units & rubric

| Question | Options | Selected |
|---|---|---|
| Reach counts | Clients affected (rec.) / Users per quarter / Site-configured unit | Clients affected |
| Effort unit | Person-weeks (rec.) / Person-months / Story points | Person-weeks |
| Confidence input | Fixed steps 100/80/50 (rec.) / Free 0-100 / Steps plus custom | Fixed steps |
| Reach from product client count | Hint only (rec.) / Auto-fill when empty / No link | Hint only |

## Item lifecycle & page

| Question | Options | Selected |
|---|---|---|
| Placement | New Roadmap nav page (rec.) / Portfolio tab | New Roadmap nav page |
| Extra fields (multi) | Status / Notes / Target quarter / Owner | All four |
| Leaving the roadmap | Archive never delete (rec.) / Delete allowed / Both | Archive, never delete |
| Jira detail visibility | Stored summary/status for everyone (rec.) / Only users who can read the issue | Stored for everyone |

## Jira import & refresh

| Question | Options | Selected |
|---|---|---|
| Adding from Jira | Pick from search results (rec.) / Pick plus add-all | Free text: "Pick from search results + tickets from releases created". Clarified as: pick from a release's tickets |
| Duplicates | Skip and say so (rec.) / Allow duplicates | Skip and say so |
| Refresh | Refresh all + per item (rec.) / Automatic on page open / Per item only | Refresh all + per item |
| Done/archived in ranking | Open items only (rec.) / Everything ranked | Open items only |

## Ranking table & CSV

| Question | Options | Selected |
|---|---|---|
| Tie-break | Score, Impact, oldest (rec.) / Score, lower Effort, title / Score, manual order | Score, then Impact, then oldest |
| Columns (multi) | Rank/title/score; R,I,C,E; product/status/quarter/owner; Jira status + fetched | All |
| Precision | 1 decimal (rec.) / Whole / 2 decimals | 1 decimal |
| CSV | What's on screen (rec.) / Everything | What's on screen |

## Claude's Discretion

- History display and coverage beyond R/I/C/E; archived product linking; CSV injection guard, BOM and filename; refresh batch cap; a reusable `ranked()` for Phase 20.

## Deferred Ideas

None.
