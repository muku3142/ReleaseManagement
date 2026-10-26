# Phase 7 Discussion Log — 2026-10-07

Human reference only; downstream agents read 07-CONTEXT.md. Constraints carried in from the Phase 8
discussion: frozen-only promotion, append-only rows with outcome, first-success Production date.

| Area | Question | Chosen |
|---|---|---|
| Ladder | Stages | DEV → TEST → UAT → PROD |
| Ladder | Defined where | Editable in Settings (chosen over the recommended code-fixed option) |
| Ladder | Application dimension | None — one ladder, releases individually |
| Ladder | History under edits | Permanent stage ids; rename relabels; used stages retire, never delete |
| Ladder | Production identity | Exactly one stage flagged Production |
| Rules | Allowed targets | Next stage or redeploy current; no skipping |
| Rules | Rollbacks | Outcome ROLLED_BACK; lowers current stage |
| Recording | Shape | One entry after the fact |
| Recording | Timestamps | recorded_at (server) + deployed_at (user, defaults now) |
| Recording | Corrections | New entry supersedes original, reason required |
| Recording | Who | Any signed-in user |

Not selected for discussion: paperwork gate (deferred idea).
