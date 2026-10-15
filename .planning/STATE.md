---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Release Assistant
status: executing
stopped_at: Phase 10 context gathered
last_updated: "2026-10-13T13:40:09.728Z"
last_activity: 2026-10-13 -- Phase 10 execution started
progress:
  total_phases: 18
  completed_phases: 8
  total_plans: 109
  completed_plans: 95
  percent: 87
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-13)

**Core value:** Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.
**Current focus:** Phase 10 — RAG Chat & Citations

## Current Position

Phase: 10 (RAG Chat & Citations) — BLOCKED on a user decision
Plan: 9 of 9 (10-01..10-08 done; 10-09 live run stopped at the golden run)
Status: Paused — chat model choice deferred by the user (see Blockers)
Last activity: 2026-10-15 — Phase 11 wave 1 done (11-01, 11-02 merged, rag 284 green); 11-03 may run next, 11-04..11-09 wait for Phase 10

**v1 (Phases 1-8) is still open — not archived, phase directories kept on purpose.** Code for every v1 phase is
merged (backend 946, frontend 340 tests green). Four blocking human checks remain, each with no SUMMARY yet:

- 05.4-07 — template selection hand check
- 06-10 — live Jira/ADO tenant check of code links and the ADO credential
- 07-07 — promotion ladder / corrections / VOIDED walk-through
- 08-08 — release insights dashboard walk-through

Close v1 with `/gsd-complete-milestone` once all four are approved.

## Performance Metrics

**Velocity:**

- Total plans completed: 19
- Average duration: - min
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 09 | 11 | - | - |

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

- **OPEN (2026-10-14): Phase 10 chat model.** The installed `qwen3:4b` is the 2507 thinking-only build. Its template always opens `<think>`, so `think:false` does not stop about 2,500 reasoning tokens, and every transform hits the 120 s deadline (golden T01-T03 0/3). Chat leaks reasoning too. The user deferred the decision:
  - A: pull `qwen3:4b-instruct-2507-q4_K_M` and set `RAG_CHAT_MODEL` (config only, recommended);
  - B: keep the thinking model and raise the deadline (breaks D-10-12; T03 still leaked).

  After the choice: resume `/gsd-debug continue transform-deadline-qwen3`, re-measure G07/G09 (both 0/3) and harden prompts if still needed, rerun golden, then finish 10-09 steps 5-9 and the user review. Evidence: `.planning/phases/10-rag-chat-citations/10-09-SUMMARY.md`, `.planning/debug/transform-deadline-qwen3.md`.
- Resolved: Jira→ADO link-discovery spike — GO for discovery via dev-status, but commit lists are incomplete (only key-referencing commits); Phase 6 needs the ADO API for full commits. See 02-DEVSTATUS-SPIKE.md.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260810-wc3 | Fix AdfFlattener heading and code-block newline defects | 2026-08-10 | 1396ffc | [260810-wc3-fix-adfflattener-heading-and-code-block-](./quick/260810-wc3-fix-adfflattener-heading-and-code-block-/) |
| 261012-nqd | On-demand "Show linked commits" in the ticket browser (GET /api/tickets/{key}/code-links, read-only) | 2026-10-12 | c44f3e5 | [261012-nqd-ticket-browser-show-linked-commits](./quick/261012-nqd-ticket-browser-show-linked-commits/) |
| fast | Ticket browser: click anywhere on a row to expand it (checkbox, controls and text selection excluded) | 2026-10-12 | 2518d44 | - |
| fast | Release page: click anywhere on a ticket row to expand it (chip, Retry, Remove and text selection excluded) | 2026-10-13 | 22a1564 | - |
| 261014-l33 | Add release story point totals to dashboard (INSIGHT-04) | 2026-10-14 | fd32cea | [261014-l33-add-release-story-point-totals-to-dashbo](./quick/261014-l33-add-release-story-point-totals-to-dashbo/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none — first milestone)* | | | |

## Session Continuity

Last session: 2026-10-13T12:33:13.702Z
Stopped at: Phase 10 context gathered
Resume file: .planning/phases/10-rag-chat-citations/10-CONTEXT.md
