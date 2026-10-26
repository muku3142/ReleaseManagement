---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: Portfolio & Planning
status: executing
stopped_at: Phase 17 UI-SPEC approved
last_updated: "2026-10-25T13:39:07.280Z"
last_activity: 2026-10-25 -- Phase 17.1 execution started
progress:
  total_phases: 12
  completed_phases: 3
  total_plans: 58
  completed_plans: 48
  percent: 83
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-20)

**Core value:** Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.
**Current focus:** Phase 17.1 — product-briefings-scheduler

## Current Position

Phase: 17.1 (product-briefings-scheduler) — EXECUTING
Plan: 16 of 16 (15 complete; 17.1-16 live checkpoint pending, needs Docker)
Status: Executing Phase 17.1
Last activity: 2026-10-25 -- Phase 17.1 execution started

## Performance Metrics

**Velocity:**

- Total plans completed: 37
- Average duration: - min
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 09 | 11 | - | - |
| 10 | 9 | - | - |
| 11 | 9 | - | - |

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

- Phase 15.1 (Unfreeze and Flexible Promotion) inserted after Phase 15 on 2026-10-22 (user request): unfreeze an undeployed frozen release to change tickets, and promote straight to any later stage (reverses D-07-07; D-07-06 kept). Runs before Phase 16 executes.
- Phase 23 (UI Enhancement & Logo) inserted before publishing on 2026-10-22 at the user request; GitHub Publishing renumbered to 24.
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

- **RESOLVED (2026-10-15): dev chat model is `qwen2.5-coder:7b` (`be562cd`), accepted by the user, including its T03 failure (planted text in a transformed document steers the output, 0/3). That failure is recorded as a dev-model residual risk under SAFE-02; a stronger production model must pass T03 before v1.1 ships (Phase 13 gate). Measured: T01/T02/G07/G09 3/3. qwen3:4b (thinking) cannot answer within any practical deadline on the 4 GB GPU. Next: finish 10-09 (golden rerun with the coder model, rollout, smoke, drills, log check, user review). Note: the coder model emits tool calls as text, so Phase 11 tool flows will not work live on the dev model.**
- (superseded) **DECIDED (2026-10-15) by the user: Phase 10 uses one of the INSTALLED models for dev/testing (no pulls); production may change `RAG_CHAT_MODEL` later.** The debug session is measuring A (qwen3:4b, think:true, raised dev deadline) against B (qwen2.5-coder:7b, 120 s) and will pick one. Earlier note:
- **(was OPEN 2026-10-14): Phase 10 chat model.** The installed `qwen3:4b` is the 2507 thinking-only build. Its template always opens `<think>`, so `think:false` does not stop about 2,500 reasoning tokens, and every transform hits the 120 s deadline (golden T01-T03 0/3). Chat leaks reasoning too. The user deferred the decision:
  - A: pull `qwen3:4b-instruct-2507-q4_K_M` and set `RAG_CHAT_MODEL` (config only, recommended);
  - B: keep the thinking model and raise the deadline (breaks D-10-12; T03 still leaked).

  After the choice: resume `/gsd-debug continue transform-deadline-qwen3`, re-measure G07/G09 (both 0/3) and harden prompts if still needed, rerun golden, then finish 10-09 steps 5-9 and the user review. Evidence: `.planning/phases/10-rag-chat-citations/10-09-SUMMARY.md`, `.planning/debug/transform-deadline-qwen3.md`.

- Resolved: Jira→ADO link-discovery spike — GO for discovery via dev-status, but commit lists are incomplete (only key-referencing commits); Phase 6 needs the ADO API for full commits. See 02-DEVSTATUS-SPIKE.md.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 261022-iv7 | Allow saving a timed-out Clean up/Custom result (server restores unprocessed sections; on live/phase15-chunking, not main) | 2026-10-22 | 40843dc | [261022-iv7-allow-saving-timed-out-assist-results](./quick/261022-iv7-allow-saving-timed-out-assist-results/) |
| 260810-wc3 | Fix AdfFlattener heading and code-block newline defects | 2026-08-10 | 1396ffc | [260810-wc3-fix-adfflattener-heading-and-code-block-](./quick/260810-wc3-fix-adfflattener-heading-and-code-block-/) |
| 261012-nqd | On-demand "Show linked commits" in the ticket browser (GET /api/tickets/{key}/code-links, read-only) | 2026-10-12 | c44f3e5 | [261012-nqd-ticket-browser-show-linked-commits](./quick/261012-nqd-ticket-browser-show-linked-commits/) |
| fast | Ticket browser: click anywhere on a row to expand it (checkbox, controls and text selection excluded) | 2026-10-12 | 2518d44 | - |
| fast | Release page: click anywhere on a ticket row to expand it (chip, Retry, Remove and text selection excluded) | 2026-10-13 | 22a1564 | - |
| 261014-l33 | Add release story point totals to dashboard (INSIGHT-04) | 2026-10-14 | fd32cea | [261014-l33-add-release-story-point-totals-to-dashbo](./quick/261014-l33-add-release-story-point-totals-to-dashbo/) |
| 261020-i8c | Assistant shows clarifications and tool errors instead of the generic refusal; compose dev model corrected | 2026-10-20 | 0bcab23 | [261020-i8c-assistant-answers-unclear-questions-with](./quick/261020-i8c-assistant-answers-unclear-questions-with/) |
| fast | Relabel AI result save button to Accept and save as revision N+1 | 2026-10-18 | 1ca6519 | - |
| 261018-iii | Allow saving Summarize results as an AI-assisted revision (D-14-13, V13) | 2026-10-18 | e617d59 | [261018-iii-allow-saving-summaries](./quick/261018-iii-allow-saving-summaries/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| uat | 01-08-UAT-RESULTS (was partial) | closed on user approval | 2026-10-20 |
| verification | 05.2-VERIFICATION (was human_needed) | closed on user approval | 2026-10-20 |
| quick_task | 260810-wc3 (status missing) | complete | 2026-10-20 |
| quick_task | 261012-nqd (status missing) | complete | 2026-10-20 |
| quick_task | 261014-l33 (status missing) | complete | 2026-10-20 |
| quick_task | 261018-iii (status missing) | complete | 2026-10-20 |
| unrun_step | 12-11 T2 (`AssistantLiveCheck` never written) and T4 (push / rag-down / catch-up / log hygiene proof) | open, closed on user word | 2026-10-20 |
| unrun_step | 13-13 T3: no dev quality-gate report (SAFE-04 has no measured numbers) | open | 2026-10-20 |
| unrun_step | 14-15 T3: no first live doc-assist report | open | 2026-10-20 |
| unrun_step | 13-14 / 14-16 production-host gate runs asserted passed, no report | open | 2026-10-20 |

## Session Continuity

Last session: 2026-10-25T08:48:13.554Z
Stopped at: Phase 17 UI-SPEC approved
Resume file: .planning/phases/17-rice-roadmap/17-UI-SPEC.md
