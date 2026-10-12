---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 05.3-03-PLAN.md — Phase 05.3 COMPLETE (3/3)
last_updated: "2026-10-12T11:19:10.940Z"
last_activity: 2026-10-12 -- Phase 07 wave 2 merged (07-03); backend 827 green; Phase 06 live check pending
progress:
  total_phases: 12
  completed_phases: 7
  total_plans: 81
  completed_plans: 74
  percent: 91
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-27)

**Core value:** Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.
**Current focus:** Phase 05.3 complete. Phase 05.4 (template selection at generation time) is
inserted and unplanned — next up.

## Current Position

Phase: 05.4 (template-selection-at-generation-time) — PLANNED (0/7)
Plan: 6 of 7 — executing wave 5

Current baselines: **backend 597 tests, frontend 210 tests**, `tsc -b` clean, full `mvn clean test`
BUILD SUCCESS.

**One thing needs a human before its phase can close:**

1. **05.2's live-Jira verification** — every automated test stubs `JiraTicketPort` or mocks
   `useTicketSearch`, so nothing proves the release scope survives a real HTTP round trip. Recorded in
   `05.2-VERIFICATION.md` frontmatter as `human_needed`, not silently passed.

- **05.1 Per-Template Logo Sizing — COMPLETE, 4/4 plans, closed 2026-08-12.** The plan 05.1-04
  human-verify gate was closed **on the user's assertion** that they had run it and everything passed;
  the six per-step observations the plan required were requested twice and not supplied, so
  cross-format visual parity is **asserted, not observed**. The evidence that does exist is
  dimensional: `GenerateThenExportSeamTest` at 15mm, proven to fail against the pre-fix adapters. The
  re-runnable six-step procedure is still in `05.1-04-PLAN.md`'s `<how-to-verify>` block if that gap
  is ever worth closing. Detail in `05.1-04-SUMMARY.md`. `ChromeFields` carries
  `logoRenderHeightMm` (mm, default 30, bounds 5..120) through V6 and the full stack; both exporters
  clamp proportionally and downward-only; the cross-format seam test uses **15mm, not the 30mm
  default**, because at 30mm the pre-fix PDF's hard-coded `max-height: 3cm` coincidentally produces the
  right answer and a default-height assertion would pass against broken code. Reverting the fix failed
  4 of 4 seam tests (observed `expected: 540000L but was: 2857500L`).

- **05.2 Release Ticket Selection — COMPLETE, 3/3 plans, verified 2026-08-12.** Backend 561→571,
  frontend 185→210. The release scope is threaded as a **separate argument** to
  `TicketSearchService.search(criteria, releaseId)` rather than as a field on `TicketSearchCriteria`, so
  `toJql()` is structurally incapable of leaking release membership into a Jira query. `TicketBrowser`
  is now the single browse surface — exactly one file in the app declares the results `<table>` — used
  by both `/tickets` and the new `AddTicketsToReleaseDialog`. A frozen release renders no add affordance
  at all (the button sits inside `{isOpen && ...}`), so refusal happens before the work rather than as a
  409 after tickets are chosen.

- **05.3 Actor Identity Display — COMPLETE, 3/3 plans, closed 2026-08-13.** Wave 1 (plan 01) built the
  projection-only `ActorDirectory` port, `findEmailsByAccountIdIn` JPQL projection, and the batching
  `ActorResolutionService`/`ResolvedActors` pair — proven end-to-end against a real Postgres row this
  key cannot decrypt (`findByAccountId` throws, `ActorDirectory` still resolves the email). Wave 2
  (plan 02) wired it into `DocumentController`, `JiraSiteConfigController`, `ReleaseController` and
  `TemplateController` — all eight actor fields across six response DTOs now carry the resolved email,
  with `ReleaseController.list()` and `DocumentController.revisions()` each issuing exactly one batched
  lookup regardless of row count. Backend 579→590 (11 new controller-slice tests). Wave 3 (plan 03)
  crossed the seam: `ActorDisplaySeamIT` drives the real `ReleaseController`/`DocumentController` beans
  against a Postgres row whose ciphertext the active key cannot decrypt, with a mutation check
  confirming the tests fail if the adapter is reverted to entity hydration; two new ArchUnit rules
  (`theWebAdapterCannotReachTheTokenBearingCredentialPort`, `actorResolutionResolvesThroughActorDirectoryOnly`)
  lock the display path out of `JiraCredentialRepository`, each proven non-vacuous by hand. Backend
  590→597 (5 seam + 2 arch tests). The plan's human checkpoint (Task 3, pre-existing records display
  correctly) was **approved verbatim ("approved")** — no per-step observations were volunteered or
  recorded; see `05.3-03-SUMMARY.md`'s "Checkpoint Evidence" section for the explicit distinction from
  05.1's "asserted, not observed" gate. The load-bearing fact, confirmed by pattern mapping rather than
  inferred: decryption fires because `JiraCredentialEntity.token` carries
  `@Convert(converter = EncryptedTokenConverter.class)`, so the risk attaches to the entity **type** —
  any query returning that type can throw on a superseded key, and only a scalar projection avoids it.

**Phase 05 (document-generation) is complete** — 13/13 plans, verified 2026-08-11.
Both verification gaps were closed by gap-closure plans and the phase's human
checkpoint was approved against a real PDF and DOCX export:

- 05-12 built the document viewer route the phase designed but never registered
  (`/releases/$releaseId/documents/$documentType`), extracted the shared
  `RenderedDocument` module so one markdown-it instance and one set of chrome
  markup serve both the authoring preview and the viewer, and upgraded every
  View affordance from a dead `<a href>` to a typed `<Link>`.

- 05-13 fixed generation storing flexmark HTML in a field named `markdown`,
  which made every exported PDF and DOCX render its own source as escaped tag
  text. `RenderResult.html()` — one accessor returning two different content
  types, which is what made the defect look correct at every call site — is now
  `output()`. `GenerateThenExportSeamTest` crosses generate → export with no
  hand-written fixture on either side; reverting the fix fails 4 of its 4 tests.

- Correction to `05-VERIFICATION.md`: its gap-2 text claimed chrome and both
  export ports were also broken. They were not. Re-validation before planning
  showed chrome already used `resolveText` (Mustache only, no flexmark) and both
  ports already documented Markdown. Generation was the single disagreement.

**Phase 05.1 was inserted after Phase 05 on 2026-08-11**, from a defect found
while answering a question about controlling logo size: the cover logo is sized
in three independent places and only two cap it. `DocxExportAdapter.java:204-205`
multiplies native pixels by `EMU_PER_PIXEL` with no clamp, so a 512×512 logo is
5.3 in tall in DOCX versus ~1.2 in in the PDF and 80 px in the browser. Uploads
are accepted up to 2000×2000, making the DOCX worst case ~20.8 in on an 11.7-in
A4 page. Chosen direction: a per-template `ChromeFields` setting stored on the
snapshot, so an existing document keeps the size it was generated with.

**Phase 2 is NOT formally complete.** Outstanding before it can be closed:

- 02-04 dev-status capture (needs a human with a live token). Phase 6 cannot be
  scoped until this resolves — that dependency is unchanged.

- JIRA-02..07 are still `Pending` in REQUIREMENTS.md. Deliberately not ticked:
  JIRA-03 (paging to exhaustion) is proven by WireMock only, never against a
  >100-ticket real result set, and the evidence-notes discipline from Phase 1
  says not to tick on a passing suite alone.

Post-plan changes landed after their plans were marked complete (all recorded in
the relevant summaries): JIRA-07 status filter, fix version made optional then
replaced entirely by **sprint** (brings Jira's Agile API into scope, reversing
part of a PROJECT.md exclusion), the system-field-wins fix for shadowed field
names, table widening, and frontend tests moved to `frontend/tests/`.

Status: Phase 05.4 EXECUTING (6/7) — 05.4-07 awaiting human verify. Phases 6, 7, 8 discussed; 02-04 capture in progress (unblocks Phase 6).
Last activity: 2026-10-07 -- Phase 05.4 planned (7 plans); per-user default template added (D-05.4-03..06)

Progress: [█████████░] 86%

## Performance Metrics

**Velocity:**

- Total plans completed: 8
- Average duration: - min
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 34 | 3 tasks | 22 files |
| Phase 01 P05 | 53 | 2 tasks | 13 files |
| Phase 01 P06 | 55 | 2 tasks | 13 files |
| Phase 01 P07 | 100 | 3 tasks | 22 files |
| Phase 05.3 P01 | 4min | 3 tasks | 7 files |
| Phase 05.3 P02 | 25min | 3 tasks | 10 files |
| Phase 05.3 P03 | ~6min | 3 tasks | 3 files |

## Accumulated Context

### Roadmap Evolution

- Phase 05.1 inserted after Phase 05 on 2026-08-11 (URGENT) — per-template logo sizing and the DOCX
  cover-logo parity defect. Found after Phase 05 was verified and closed, while answering a question
  about controlling logo size: `DocxExportAdapter.java:204-205` applies no clamp to the logo, unlike
  the PDF's `max-height: 3cm` and the browser's `max-h-20`, so the three renderers disagree by up to
  4.5×. Direction chosen with the user: a per-template `ChromeFields` field stored on the snapshot,
  rather than a global config value or a bug-fix-only clamp, so an existing document keeps the size
  it was generated with. Written by hand — `state add-roadmap-evolution` is SDK-only in this CLI
  version and does not run.

- Phase 05.2 inserted after Phase 05.1 on 2026-08-11 — release ticket selection. Two assembly
  frictions the user hit in use: already-added tickets still appear while browsing, and tickets can
  only be added from the browse screen. Filtering scoped to the *target* release only, because
  `release_ticket_item_key_unique (release_id, ticket_key)` deliberately allows one ticket in several
  releases and a global filter would make that state unreachable through the UI while leaving it
  reachable in data. Neither requirement is a correctness fix — `addTickets` already de-duplicates.

- Phase 05.3 inserted after Phase 05.2 on 2026-08-11 — actor identity display. Every actor field
  stores the Jira accountId (not a session id: `AuthenticationService:58` issues the JWT with
  accountId as subject and the email as a separate claim), across 9 capture sites in 5 controllers and
  8 response DTOs. Read-time resolution chosen over storing the email or storing both, because it is
  the only option that fixes already-recorded history — `document_snapshot` is append-only so a stored
  label could never be corrected.

- Phase 05.4 inserted after Phase 05.3 on 2026-08-12 (URGENT) — template selection at generation time.
  `DocumentGenerationService.java:51` resolves the template as `findActive(documentType)`, and
  `setActive` keeps exactly one active per type, so generating with a different template means leaving
  the release, re-activating globally, and coming back — and that switch changes what every other
  release generates next. D-05.4-01: `active` stays as the preselected default and templateId becomes
  an optional override, rejecting the larger "remove `active` entirely" diff for a workflow the user
  has not asked to lose. D-05.4-02: the picker is filtered to the slot's document type and the server
  rejects a mismatch, because readiness, revision numbering and export filenames all key off document
  type. No schema change: `DocumentSnapshot.create` already stores the template id, name and body.
  Sequenced after 05.3 — both edit `DocumentController.java`. Written by hand;
  `state add-roadmap-evolution` is SDK-only in this CLI version and errors out.

- **The `phase insert` handler places a new decimal section immediately after the *named* phase, not
  in numeric order.** Inserting 05.2 and 05.3 "after 5" put both sections *before* 05.1 in the file,
  and left the top checklist, the Progress table and the `Phases execute in numeric order:` line
  untouched. Four hand fixes per insert. Verify section ordering after every insert.

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: Jira→ADO link-discovery spike placed in Phase 2 (Jira), not Phase 6 (ADO) — Phase 6 cannot be scoped in detail until it resolves
- Roadmap: ADO enrichment sequenced after document generation (Phase 6 after Phase 5) so documents work Jira-only first, per the "must never gate" constraint
- Roadmap: AUTH folded into Phase 1 (Foundation) rather than given its own phase — reuses Jira credentials, no separate user store
- Roadmap: Template engine design (jmustache + view-model + placeholder catalog) is the first work inside Phase 4, settled before the authoring UI is built on top of it
- [Phase 01]: Testcontainers 2.x renamed postgresql/junit-jupiter modules to testcontainers-postgresql/testcontainers-junit-jupiter — Plan's pre-2.x artifact IDs no longer exist on Maven Central
- [Phase 01]: Spring Boot 4.1 moved Flyway autoconfiguration into spring-boot-starter-flyway and @WebMvcTest into spring-boot-starter-webmvc-test — Raw flyway-core and spring-boot-starter-test no longer autoconfigure these on their own in Boot 4.1's modularized autoconfigure
- [Phase 01]: Explicitly restored CsrfFilter.DEFAULT_CSRF_MATCHER on top of csrf.spa() - oauth2ResourceServer() unconditionally exempts bearer-resolvable (cookie-carried) requests from CSRF, mitigated in practice by access_token's SameSite=Strict
- [Phase 01]: Jackson 3 (tools.jackson.*) is Boot 4.1's managed Jackson databind - com.fasterxml.jackson.databind.ObjectMapper no longer resolves, only jackson-annotations stays under com.fasterxml
- [Phase 01]: Narrowed ArchitectureBoundaryTest's application-vs-adapters ban to jira/persistence/web only — AuthenticationService must inject JwtTokenService (adapters.security) directly - no port abstraction exists over session/JWT infrastructure, and none is warranted for a single-implementation technical concern
- [Phase 01]: JiraAccessContextService.currentContext() is the sole AUTH-02 mechanism for the rest of the project — Keyed strictly on CurrentUserPort.currentAccountId(); no overload takes an accountId and no shared-credential path exists anywhere in the codebase
- [Phase 01]: AuthController extracts a shared accountId(jwt) helper so jwt.getSubject() is called from exactly one place — Keeps the literal grep-gated acceptance criterion accurate while removing duplication between session() and replaceCredential()
- [Phase 01]: PUT /api/auth/credential revalidates the replacement token live against Jira using the stored email before upserting, and rejects ACCOUNT_MISMATCH (409) if the validated identity belongs to a different account — AUTH-04 requires replace-without-losing-data; revalidation prevents storing a token Jira itself would reject, and the mismatch check prevents one account's credential row being overwritten by another account's token
- [Phase 01]: Frontend credentialStatus.ts uses a dependency-free useSyncExternalStore module instead of a state library for the single credential-invalid boolean signal — Avoids adding a state-management dependency for one boolean flag shared between apiFetch and CredentialAlert
- [Phase 05.3]: ActorResolutionService.resolve uses varargs (String...) not List<String>, so single/dual-id call sites can call it inline while a list call site uses toArray(String[]::new) which tolerates a null unfrozen-release entry
- [Phase 02]: Jira→ADO link discovery resolved — GO (discovery only; ADO API needed for complete commits), see 02-DEVSTATUS-SPIKE.md
- [Phase 05.3]: ReleaseController.toResponse/toAddResult and TemplateController.toResponse de-staticed to instance methods; ReleaseController.toSummaryResponse stays static and takes a resolved ResolvedActors parameter so a list row cannot resolve for itself

### Pending Todos

None yet.

### Blockers/Concerns

- Resolved: Jira→ADO link-discovery spike — GO for discovery via dev-status, but commit lists are incomplete (only key-referencing commits); Phase 6 needs the ADO API for full commits. See 02-DEVSTATUS-SPIKE.md.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260810-wc3 | Fix AdfFlattener heading and code-block newline defects | 2026-08-10 | 1396ffc | [260810-wc3-fix-adfflattener-heading-and-code-block-](./quick/260810-wc3-fix-adfflattener-heading-and-code-block-/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none — first milestone)* | | | |

## Session Continuity

Last session: 2026-08-13T00:00:00.000Z
Stopped at: Completed 05.3-03-PLAN.md — Phase 05.3 COMPLETE (3/3)
Resume file: None
