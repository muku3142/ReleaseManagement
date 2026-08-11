---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 05.1 inserted, awaiting planning
last_updated: "2026-08-11T10:07:38.360Z"
last_activity: 2026-08-11 -- Phase 05 completed (13/13); Phase 05.1 inserted for per-template logo sizing
progress:
  total_phases: 8
  completed_phases: 3
  total_plans: 47
  completed_plans: 45
  percent: 96
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-27)

**Core value:** Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.
**Current focus:** Phase 05.1 — per-template logo sizing (not planned yet)

## Current Position

Phase: 05.1 (per-template-logo-sizing) — NOT PLANNED
Plan: 0 of TBD — run `/gsd-plan-phase 05.1`

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

Status: Phase 05 complete — Phase 05.1 inserted, awaiting planning
Last activity: 2026-08-11 -- Phase 05 completed (13/13); Phase 05.1 inserted for per-template logo sizing

Progress: [██████████] 96%

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

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 2's Jira→ADO link-discovery spike outcome will determine Phase 6's exact scope (dev-status mechanism vs. commit/PR key-parsing fallback, or both) — revisit Phase 6 plan-phase once the spike lands.

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

Last session: 2026-08-10T09:40:05.295Z
Stopped at: Phase 5 UI-SPEC approved
Resume file: .planning/phases/05-document-generation/05-UI-SPEC.md
