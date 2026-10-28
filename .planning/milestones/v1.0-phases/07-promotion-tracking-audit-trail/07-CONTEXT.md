# Phase 7: Promotion Tracking & Audit Trail - Context

**Gathered:** 2026-10-07
**Status:** Ready for planning
**Source:** `/gsd-discuss-phase 7`. Constraints were already fixed by the Phase 8 discussion (D-08-01,
D-08-02, D-08-14).

<domain>
## Phase Boundary

**In scope:**
- One environment ladder whose stages the release manager can edit in Settings.
- Recording promotions of **frozen** releases as append-only entries: stage, deployed time, recorded
  time, actor, outcome, optional note (DEPL-01..04).
- Corrections that supersede an entry without editing it (AUDIT-03).
- A per-release promotion history and current stage (AUDIT-01).
- Surfacing the existing document revision history as part of the release's audit view (AUDIT-02).

**Out of scope:**
- Running deployments or reading CI/CD or ADO pipeline status (PROJECT.md).
- Per-application ladders (PROV-02, v2) and any application dimension on releases.
- A named-approver sign-off step (DEPL-05, v2) and a visual timeline (DEPL-06, v2).
- Roles and permissions.
- The cross-release history view (AUDIT-04): that is Phase 8 (D-08-12).
- A paperwork gate on promotion: offered, not selected.

**Today:** `ReleaseStatus` has only `OPEN` / `FROZEN`
(`src/main/java/com/evision/releasemgmt/domain/release/ReleaseStatus.java`), and freeze is one-way
(`Release.java:25-31`). No ladder, environment or promotion table exists. The latest migration is
`V7__template_user_default.sql` (Phase 05.4). **Phase 6 takes `V8`** (06-RESEARCH), so this phase uses the next free number after Phase 6's migrations. Check `src/main/resources/db/migration/` at planning time; never hard-code `V8`.
</domain>

<decisions>
## Implementation Decisions

### Ladder
- **D-07-01: Initial stages are DEV → TEST → UAT → PROD.** They are seeded by migration, and PROD
  carries the Production flag.
- **D-07-02: The ladder is editable in Settings.** The user chose this over fixing it in code. It
  stays **one** ladder for the whole instance: this is not PROV-02's per-application ladders.
  Edits are rename, reorder, add, and retire.
- **D-07-03: Stage ids are permanent; a used stage is retired, never deleted.**
  - A rename changes the label everywhere, history included, because history references the id.
  - A stage that any promotion references can only be **retired**: hidden from new promotions, still
    shown in history. An unused stage may be deleted.
  - Reordering changes ladder position from then on. Current stage is always computed from the current
    order, never stored.
- **D-07-04: Exactly one stage is flagged Production.** It starts on PROD. The flag can move to
  another active stage but can never be absent, and the Production stage cannot be retired while
  flagged. Phase 8 reads this flag, not a name or the last position (D-08-01).
- **D-07-05: No application dimension.** Releases move through the single ladder individually.

### Promotion rules
- **D-07-06: Only frozen releases can be promoted** (D-08-14, carried forward). Promoting an `OPEN`
  release is rejected server-side.
- **D-07-07: The target must be the next stage or a redeploy to the current stage.**
  - Skipping stages is rejected server-side.
  - "Next" and "current" are computed from the ladder order at the time of recording, ignoring
    retired stages.
  - A release with no successful promotion can only target the first active stage.
- **D-07-08: Outcomes are SUCCESS / FAILED / ROLLED_BACK** (plus correction-only `VOIDED`, D-07-15).
  - A rollback is a new entry on the stage being rolled back, with outcome `ROLLED_BACK`.
  - **Current stage** = the highest stage, by current order, holding a SUCCESS that no later
    ROLLED_BACK on that stage cancels.
  - FAILED never advances the stage (D-08-02).
  - A rolled-back Production release drops back down the ladder. Its "shipped" date in Phase 8 is still
    its **first** successful Production promotion (D-08-01), unchanged by later rollbacks.

### Recording and corrections
- **D-07-09: One entry, recorded after the fact.** The release manager records the outcome once it is
  known: target stage, outcome, optional note (DEPL-04). There is no in-progress or "started" state.
  This tool records deployments; it never runs them.
- **D-07-10: Two timestamps.**
  - `recorded_at`: server time, immutable.
  - `deployed_at`: user-entered, defaults to now. It may not be in the future, and may not precede
    the release's `frozen_at`.
  - Phase 8 durations and deployment history use `deployed_at`. The audit view shows both.
- **D-07-11: Corrections supersede; nothing is updated or deleted (AUDIT-03).**
  - A correction is a new entry that references the entry it corrects (`supersedes_id`), carries the
    corrected values, and requires a reason.
  - The original stays visible with a "superseded by" marker.
  - Current stage, rule checks (D-07-07) and every Phase 8 metric use only **effective** entries,
    i.e. non-superseded ones.
  - An entry can be superseded at most once; correcting a correction supersedes the correction.
  - The promotion table has **no UPDATE or DELETE path** in code. Enforce append-only at the database
    too (trigger or revoked privileges are the planner's call). The repository port exposes no update
    or delete.
- **D-07-12: Any signed-in user may record or correct promotions, and edit the ladder.** No role model
  exists, and adding one is out of scope. The actor is the accountId (`principal.getName()`) and is
  displayed via the 05.3 actor resolution (`ActorResolutionService`, batched, never per row).

### Decided after research (2026-10-12)
- **D-07-13: "Later" means later by `deployed_at`, tie-broken by `recorded_at`.** This applies to
  D-07-08's rollback cancellation. A ROLLED_BACK is rejected unless the stage holds a standing SUCCESS
  whose `deployed_at` is not after the rollback's.
- **D-07-14: A correction passes field checks only, never the ladder-sequence rules.**
  - The checks are: `deployed_at` bounds (D-07-10), stage not retired, and a reason.
  - Replaying D-07-07 over the corrected history is rejected, because it would make early entries
    impossible to fix.
- **D-07-15: A fourth outcome, `VOIDED`, reachable only through a correction.** This amends D-07-08.
  - It is for entries that should never have existed, e.g. one recorded on the wrong release.
  - A voided entry stays visible in the history with its reason.
  - An effective VOIDED entry counts toward nothing: not the current stage, not the D-07-07 checks,
    and not any Phase 8 metric. Phase 8 must exclude it.
  - New entries cannot be recorded as VOIDED.

### Claude's Discretion
- Table shapes: e.g. `environment_stage(id, name, position, is_production, retired_at)` and an
  append-only `promotion(id, release_id, stage_id, outcome, deployed_at, recorded_at, recorded_by, note,
  supersedes_id, correction_reason)`.
  - Index for Phase 8's filter on `(stage_id, deployed_at)` (D-08-14).
  - A partial unique index enforces "superseded at most once" (`UNIQUE (supersedes_id) WHERE
    supersedes_id IS NOT NULL`).
  - Promotions survive release deletion trivially, because only open releases are deletable (A2 in the
    Phase 8 scan) and those can have no promotions. Use `ON DELETE RESTRICT`.
- REST shape following existing controller conventions, e.g.:
  - `GET/POST /api/releases/{id}/promotions`
  - `POST /api/releases/{id}/promotions/{promotionId}/corrections`
  - `GET/PUT /api/environment-ladder`, or per-stage endpoints
- New ProblemDetail reason codes for:
  - promoting an open release;
  - a skipped stage;
  - a retired target;
  - a correction of an already-superseded entry;
  - a future or pre-freeze `deployed_at`;
  - removing or retiring the Production flag holder.
- UI:
  - the release page shows current stage as stepped dots, a "Record promotion" dialog (target
    preselected to the next stage), and a history list with a "Correct" action and superseded entries
    marked;
  - Settings gets a "Environment ladder" section.
  - Reuse the plain-Tailwind patterns. No timeline component (DEPL-06 is v2).
- AUDIT-02: the document revision history already exists (Phase 5; it shows the template name since
  05.4). This phase links it into the release's audit view. Do not rebuild it.
- Phase is `**Mode:** mvp` in ROADMAP: plan vertical slices.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md`: Phase 7, including its "Constraints placed by Phase 8" note, and Phase 8.
- `.planning/REQUIREMENTS.md`: DEPL-01..04, AUDIT-01..03; DEPL-05/06 and PROV-02 are v2.
- `.planning/PROJECT.md`: Out of Scope (no deployments, no pipeline status, one standard ladder).
- `.planning/phases/08-release-insights-dashboard-the-cross-release-views-jira-stru/08-CONTEXT.md`
  (D-08-01, D-08-02, D-08-12..14): Phase 8 is this phase's main consumer, so its contract is binding
  here.
- `.planning/phases/05.3-actor-identity-display-resolve-stored-jira-accountid-to-a-hu/05.3-CONTEXT.md`
  for actor display, and the rule that display paths never touch the credential port.
- `.planning/phases/03-release-assembly/03-CONTEXT.md` for freeze semantics (D-03-10).
- Code:
  - `src/main/java/com/evision/releasemgmt/domain/release/Release.java`
  - `ReleaseStatus.java`
  - `adapters/web/ReleaseController.java`
  - `adapters/web/GlobalExceptionHandler.java`
  - `application/ActorResolutionService.java`
  - `frontend/src/routes/ReleaseDetailRoute.tsx`
  - `frontend/src/routes/SettingsRoute.tsx`
  - `frontend/src/components/DocumentsSection.tsx` (revision history)
</canonical_refs>

<specifics>
## Specific Ideas

- Tests must prove these properties (assert the properties, not grep counts):
  - append-only: the DB rejects UPDATE/DELETE on promotions;
  - a FAILED entry never advances the stage;
  - a rollback lowers the stage;
  - a superseded entry is ignored by current-stage computation;
  - renaming a stage relabels history;
  - a used stage cannot be deleted;
  - the Production flag can never be absent.
- A promotion into a retired stage is rejected even if it would otherwise be "next".
</specifics>

<deferred>
## Deferred Ideas

- Paperwork gate (block or warn on promoting with stale or missing documents): offered as a gray area
  and not selected; candidate for a later phase.
- Named-approver sign-off (DEPL-05), visual timeline (DEPL-06), per-application ladders (PROV-02): all
  v2.
- An "in progress" deployment state: not chosen (D-07-09).
</deferred>

---

*Phase: 07-promotion-tracking-audit-trail*
*Context gathered: 2026-10-07*
