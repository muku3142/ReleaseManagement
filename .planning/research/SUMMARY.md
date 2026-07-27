# Project Research Summary

**Project:** ReleaseManagement
**Domain:** Internal release-management / deployment-tracking tool — Java Spring Boot API + React SPA, Jira-sourced release documentation with optional Azure DevOps enrichment
**Researched:** 2026-07-27
**Confidence:** MEDIUM-HIGH

## Executive Summary

This is a single-role internal tool that takes over release bookkeeping currently done by hand in Jira: assembling a named, versioned set of Jira tickets into a release, generating release notes / deployment runbook / approval documents from user-authored templates, and tracking that release's promotion through a fixed environment ladder. All four researchers converge on the same shape of solution: a Spring Boot modular monolith (no microservices, no event sourcing, no message broker — explicitly rejected as over-engineering for this scale), a hexagonal/ports-and-adapters internal structure with a strict anti-corruption layer around Jira, and a React SPA served from the same jar. The stack recommendation is current-generation and low-risk: Spring Boot 4.1 on Java 25, PostgreSQL 18 + Flyway, Spring Data JPA, and Vite/TypeScript/TanStack/TipTap on the frontend.

The single most important cross-cutting decision, reached independently by Stack, Architecture, and Pitfalls research, is **how user-authored templates are rendered**. Every general-purpose Java template engine capable of loops/conditionals (FreeMarker, Pebble, Thymeleaf, Handlebars.java) has a documented history of sandbox-bypass CVEs — including a CVSS 9.1 Thymeleaf bypass disclosed in 2026, squarely inside this project's build window — and vendor documentation for several of them explicitly states they assume trusted, developer-authored templates, not end-user content. The settled answer is a **logic-less engine (Mustache / jmustache)** with **no method invocation or arbitrary property traversal**, combined with a **dedicated, flattened, versioned view-model** (`ReleaseView`/`TicketView`/`CommitView`/`PullRequestView`) that is the only thing templates ever see — never JPA entities, never raw Jira JSON, never the domain model directly. This is one decision with two mutually reinforcing halves (safe engine + safe context), and it should be treated as a single dedicated phase with room for a short design spike before any template-authoring UI is built.

The second major cross-cutting risk is the **Jira-to-Azure-DevOps link mechanism**, also independently flagged by Stack, Architecture, and Pitfalls. The obvious mechanism — Jira's "Development panel" data via `/rest/dev-status/{version}/issue/detail` — is Atlassian's own internal, undocumented endpoint. For Azure DevOps specifically, it only returns data at all if a marketplace connector app ("Azure DevOps Integration for Jira") is installed on the target instance; if it isn't, the endpoint returns empty, not an error, so the failure is silent and easy to miss until tested against a real ticket. This must be resolved with a go/no-go verification spike (ideally during Jira provider design, before the ADO enrichment phase is scoped), with a commit-message/PR-title key-parsing fallback against Azure DevOps' own API as the resilient alternative. Two further time-sensitive facts round out the risk picture: the legacy Jira `/rest/api/*/search` endpoint now returns HTTP 410 Gone (build against `/search/jql` and the dedicated paginated `/changelog` endpoint from day one), and Azure DevOps is retiring global PATs on 1 December 2026 — inside this project's likely build/operation window — so the ADO credential model needs org-scoped tokens with expiry tracking baked in from the start, not bolted on later.

## Key Findings

### Recommended Stack

Backend: Spring Boot 4.1.0 on Java 25 (LTS), synchronous Spring MVC (no WebFlux — no reactive/streaming need exists here), PostgreSQL 18 with Flyway migrations, Spring Data JPA/Hibernate for persistence, Spring's `RestClient` (not the maintenance-mode `RestTemplate`) for outbound Jira/ADO calls, and Resilience4j (Retry/CircuitBreaker/TimeLimiter) wrapping both external integrations. Frontend: Vite + React + TypeScript, TanStack Router/Query, TipTap for rich template authoring with placeholder insertion, Tailwind + shadcn/ui for bespoke interactive surfaces (template editor, environment-ladder visualization). Document export: flexmark-all (Markdown), openhtmltopdf (PDF), docx4j (DOCX). Testing: WireMock + Testcontainers + JUnit 5 + AssertJ + Mockito on the backend, Vitest + Testing Library + Playwright + MSW on the frontend.

**Core technologies:**
- Spring Boot 4.1.0 / Java 25 — non-negotiable per project constraint; current GA, current LTS
- PostgreSQL 18 + Flyway — JSONB columns fit raw Jira/ADO payload fragments alongside relational release/promotion/audit tables; Flyway preferred over Liquibase since the DB is fixed to one engine
- `com.samskivert:jmustache` 1.16 — the settled template resolution engine (logic-less, no class-resolution attack surface to sandbox)
- Spring `RestClient` + hand-rolled Jira/ADO DTOs — no official/maintained Java client exists for either Jira Cloud or Azure DevOps, so a thin typed HTTP client is the correct default, not a third-party SDK
- Vite + React + TypeScript + TanStack Router/Query + TipTap — standard, current (2026) SPA toolchain; CRA is dead, Vite is its replacement

**Unresolved: Jira Cloud vs Data Center.** This changes the API base path (`/rest/api/3/...` with ADF rich text on Cloud vs `/rest/api/2/...` with plain-string fields on Data Center) and the auth model (API token + email as HTTP Basic on Cloud vs Bearer PAT on Data Center, introduced in Jira 8.14+). Both are fully documented and low-risk either way, but the Jira provider adapter's exact shape depends on which flavour is targeted — needs to be pinned down before or during the Jira integration phase, not blocking roadmap creation itself.

### Expected Features

**Must have (table stakes):**
- Connect to Jira, browse/filter tickets (project, fix version, JQL) behind the provider abstraction
- Assemble a named, versioned release with an explicit cutoff/freeze snapshot
- Group/sort tickets by issue type (default), component, label, epic in generated documents
- Separate template per document type (release notes, runbook, approval doc) — no competitor bundles these into one universal template
- `{{ }}`-style placeholder syntax with loops/conditionals; live preview against a real release's actual data
- Export to at least one durable format
- Fixed, linear environment ladder with a visible current stage; append-only deployment/promotion record and full audit trail
- Graceful handling of tickets with no ADO link (must not error or blank out)
- Advisory (never blocking) "ticket not ready" flags — mirrors Jira's own overridable release-warning behavior

**Should have (differentiators):**
- Scope-change diff since cutoff (tickets added/removed post-freeze) — no competitor (Jira, XL Release, Octopus) models this; directly addresses PROJECT.md's stated pain point
- Template versioning — pin the exact template version that produced a historical document, so an edited template doesn't retroactively change what a 3-month-old document "should" have looked like
- Commit/PR enrichment surfaced per ticket, sourced opportunistically via Jira->ADO link discovery
- Configurable grouping/sorting per template, not a fixed hierarchy
- Lightweight sign-off note per promotion step (not a routed workflow)

**Defer / anti-features (tied to PROJECT.md's explicit exclusions):**
- Executing deployments or triggering CI/CD — PROJECT.md: "Performing deployments" and "Triggering CI/CD pipelines" are explicit Out of Scope items; keeps the tool out of the delivery critical path
- Multi-step CAB-style approval routing — no second user role exists to route to; PROJECT.md keeps a single release-manager role
- Writing back to Jira — PROJECT.md: Jira stays read-only, this app is the system of record
- Automated release gates hitting external monitoring systems — nothing is executed here for a gate to guard
- Per-application configurable environment ladders — PROJECT.md: "one standard ladder in v1"
- Additional ticket-source providers (Azure Boards, GitHub Issues, GitLab) and ADO build/pipeline status ingestion — PROJECT.md: abstraction defined now, implementations and build-status ingestion are later-stage/deferred

### Architecture Approach

A modular monolith: one Spring Boot process, one PostgreSQL database, internal hexagonal boundaries (`domain/` -> `ports/` -> `adapters/`) that exist purely to isolate Jira/ADO-specific concepts and keep the template contract stable — not to prepare for splitting into services. `domain/` has zero framework imports (enforced via ArchUnit), `ports/` is a small, stable set of interfaces (`TicketProvider`, `CodeReferenceEnrichmentPort`, `TemplateRenderer`, repositories), and each external system gets exactly one adapter today with no provider-selection machinery built ahead of a second provider actually existing.

**Major components:**
1. **Jira adapter (`adapters/jira/`)** — the sole anti-corruption layer for Jira; the only place JQL, custom field IDs, ADF, and fix-version concepts are allowed to exist; maps Jira JSON to neutral domain `Ticket`
2. **ADO adapter (`adapters/ado/`)** — reached only from a link discovered on a Jira ticket, never queried independently; narrow read-only surface (commits, PRs); no coupling to the Jira adapter beyond the `Optional<CodeReference>` data handed across
3. **Application services** — `ReleaseAssemblyService`, `TicketEnrichmentService`, `TemplateRenderingService`, `DocumentGenerationService`, `PromotionService`; orchestrate use cases, hold no HTTP/Jira/ADO knowledge
4. **Template rendering (`adapters/render/`)** — resolves placeholders against `TemplateContext` view DTOs only, never domain/JPA objects directly
5. **Persistence (`adapters/persistence/`)** — JPA entities separate from domain classes; releases/tickets/promotions/templates/generated-documents as relational tables, with `GeneratedDocument` stored as an immutable snapshot (templateId + templateVersion + rendered content + generatedAt/By) so past documents stay faithful even if the template or release data changes later

Note: Architecture's own illustrative code samples name "Pebble" as the rendering engine, but its explicit recommendation (Pattern 3 / Anti-Pattern 2 — a dedicated flattened view-model, no reflection/method-call exposure, no live domain objects) is engine-agnostic and fully consistent with Stack's and Pitfalls' independent conclusion that jmustache is the safer choice. Treat the engine choice as settled in favor of jmustache; treat the view-model discipline as the architecturally load-bearing part regardless of engine.

### Critical Pitfalls

1. **Treating Jira's dev-status endpoint as a stable, documented API** — it is Atlassian's internal implementation detail, populated for ADO specifically only if a marketplace connector app is installed; returns empty (not an error) otherwise. Verify against a real ticket on the real instance before committing to it; build a commit/PR-message key-parsing fallback against ADO's own API regardless.
2. **Relying on a general-purpose template engine's sandbox instead of never exposing an expression language to untrusted templates** — FreeMarker, Pebble, and Thymeleaf all have documented, exploitable sandbox-bypass histories (Thymeleaf CVE-2026-40478, CVSS 9.1, is a 2026 disclosure). Use a logic-less substitution model (Mustache/jmustache) as the primary control, not a sandboxed full engine.
3. **Exposing the domain model directly to templates** — once templates exist referencing `ticket.fields.customfield_10047...`, every future domain refactor silently breaks production templates with no compiler to catch it. A dedicated, versioned template-context DTO is not optional.
4. **Building against the deprecated Jira `/rest/api/*/search` endpoint** — it now returns HTTP 410 Gone. Build against `/search/jql` (cursor pagination, explicit `fields`) and the dedicated `/changelog` endpoint from day one; `expand=changelog` silently truncates to ~100 entries.
5. **Long-lived/global Azure DevOps PATs with no expiry tracking** — global PATs retire 1 December 2026, inside this project's build window; org-scoped PATs (or Entra service-principal auth) with tracked expiry must be the credential model from the start, not a later rework.

## Implications for Roadmap

Based on combined research, the natural build order follows a strict dependency chain: nothing works without the domain/hexagonal skeleton; ticket browsing requires Jira connectivity; release assembly requires ticket browsing; document generation requires both release assembly and a settled template engine/context; ADO enrichment enhances but never gates document generation; promotion tracking requires an assembled release; audit trail is a cross-cutting layer over assembly and promotion.

### Phase 1: Foundation & Domain Skeleton
**Rationale:** Every other phase depends on the hexagonal package structure (`domain/`/`ports/`/`adapters/`) and the single-jar deployment shape existing first; retrofitting boundaries after Jira-specific code has spread is exactly the anti-pattern Pitfalls warns against.
**Delivers:** Spring Boot app skeleton, Postgres + Flyway baseline schema, React/Vite build wired into `src/main/resources/static` (single deployable jar), ArchUnit rule banning framework imports in `domain/`, base REST/CORS/security scaffolding.
**Uses:** Spring Boot 4.1/Java 25, PostgreSQL 18/Flyway, Vite/React/TypeScript.
**Avoids:** Anti-Pattern 5 (building provider-selection infrastructure before a second provider exists) and Anti-Pattern 4 (reaching for microservices/CQRS/messaging) by explicitly choosing the modular-monolith shape here.

### Phase 2: Jira Integration & Ticket Provider
**Rationale:** Release assembly cannot exist without ticket browse/search; this is the foundational external integration and the anti-corruption-layer boundary that must be right before anything builds on it.
**Delivers:** `TicketProvider` port + `JiraTicketProvider` adapter against `/rest/api/3(or 2)/search/jql` with cursor pagination and explicit fields, `/field`-based custom-field-ID resolution (not hardcoded IDs), ADF flattening (Cloud) or plain-text handling (DC), structured search params instead of hand-concatenated JQL, tested against a non-admin scoped token. Also runs the **Jira->ADO link-discovery verification spike** here (go/no-go on whether dev-status carries ADO data for a real ticket on the real instance), even though full ADO enrichment ships in a later phase.
**Addresses:** "Connect to a Jira instance and browse/filter tickets" (table stakes); provider abstraction requirement.
**Avoids:** Pitfall 8 (deprecated search endpoint), Pitfall 6 (hardcoded custom field IDs), Pitfall 7 (naive JQL concatenation), Pitfall 5 (permission-filtered data mistaken for complete), Pitfall 11 (provider abstraction leaking Jira shape — review the interface once drafted).

### Phase 3: Release Assembly
**Rationale:** The named/versioned release is the entity every downstream feature (documents, promotion, audit) hangs off; needs Jira browse/select as a hard prerequisite but has no dependency on the template engine or ADO.
**Delivers:** Release domain model, `ReleaseAssemblyService`, explicit cutoff/freeze action that snapshots the ticket set into `release_tickets`.
**Addresses:** "Release manager can assemble a release" (table stakes); lays groundwork for the scope-change-diff differentiator (v1.x).
**Standard pattern** — straightforward CRUD-plus-snapshot, low research need.

### Phase 4: Template Engine & Document Data Model (design spike)
**Rationale:** All three of Stack, Architecture, and Pitfalls independently mark this as the highest-risk, most foundational design decision in the project — it must be settled before any template-authoring UI is built, not discovered mid-build, because migrating existing user templates to a new engine/context later is a high-cost recovery.
**Delivers:** jmustache integration; `TemplateContext` view-model (`ReleaseView`/`TicketView`/`CommitView`/`PullRequestView`) as the only thing templates ever see; placeholder catalog (reflected metadata + human-readable docs) surfaced in the editor; save-time validation render against a synthetic sample context including a not-linked-to-ADO ticket; execution timeout and iteration/collection-size limits.
**Uses:** `com.samskivert:jmustache`, TipTap (frontend placeholder-insertion editor).
**Avoids:** Pitfalls 2, 3, and 4 (SSTI-class sandbox reliance, domain-model leakage into templates, unbounded template execution).

### Phase 5: Document Generation (Release Notes, Runbook, Approval Doc)
**Rationale:** Requires both an assembled release (Phase 3) and a settled template engine/context (Phase 4); the three document types share one data spine (metadata header, grouped ticket list, narrative/procedural body) but need separate templates since each has different fields (runbook needs deploy/rollback steps, approval doc needs risk/impact/backout).
**Delivers:** Per-document-type template CRUD, grouping/sorting (issue type default, configurable to component/epic/label), live preview against a real in-progress release, export (format TBD — flexmark/openhtmltopdf/docx4j available per Stack), `GeneratedDocument` persisted as an immutable snapshot with template version pinned.
**Addresses:** Release notes, runbook, and approval-document generation, preview/export (all table stakes); template versioning (differentiator, cheap to bake in now as a column even if full version-history UI is deferred).
**Research flag:** light — export format selection is a deferred design detail (PDF/DOCX/Markdown/HTML), not a risk.

### Phase 6: Azure DevOps Enrichment
**Rationale:** Explicitly optional/conditional per PROJECT.md — enhances document content but must never gate it; scoping this phase depends on the outcome of the Phase 2 verification spike, so it comes after that spike resolves, not before.
**Delivers:** `CodeReferenceEnrichmentPort` + `AdoEnrichmentAdapter` implementing whichever discovery mechanism the Phase 2 spike confirmed (dev-status and/or commit/PR-message key-parsing fallback), org-scoped PAT credential model with expiry tracking (anticipating the 1 Dec 2026 global-PAT retirement), bounded-concurrency fan-out with caching, Resilience4j wrapping with its own config separate from Jira's.
**Addresses:** "System follows Jira->ADO link and enriches with commits/PRs" (table stakes, already Active in PROJECT.md); this is flagged in Feature Prioritization as the single riskiest P1 item due to the unconfirmed discovery mechanism.
**Avoids:** Pitfall 1 (dev-status as sole/undocumented mechanism), Pitfall 9 (global/long-lived PAT with no expiry tracking), Anti-Pattern 3 (sequential N+1 external calls).

### Phase 7: Environment Ladder & Promotion Tracking
**Rationale:** Requires an assembled release to exist; independent of the template/document work, so it can be built in parallel with Phases 4-6 if team capacity allows, but is listed after them here because it has no dependency forcing earlier placement and audit-trail value compounds once documents already exist to reference.
**Delivers:** Fixed, linear environment ladder (Octopus Lifecycle-style, no branching), `PromotionService`, immutable `Promotion` rows (release, fromEnv, toEnv, timestamp, actor), release's current-stage derived from the latest promotion.
**Addresses:** "Promote a release through a fixed environment ladder" and "retain release/deployment history as audit trail" (both table stakes).
**Avoids:** Pitfall 10 (scope creep from tracker into deployment executor) — enforce that no CI/CD write credentials or trigger code paths exist in this module, from the start.
**Standard pattern** — mirrors Octopus Lifecycles conceptually, low research need.

### Phase Ordering Rationale

- Phases 1-3 have a hard, linear dependency chain (skeleton -> Jira -> release assembly) confirmed independently by Architecture's data-flow diagrams and Features' dependency graph.
- Phase 4 (template engine) is deliberately placed as its own phase, before Phase 5 consumes it, per the explicit recommendation in both Stack and Pitfalls that this decision needs a dedicated design spike and must be settled before authoring-UI work begins.
- Phase 6 (ADO) is placed after the Phase 2 spike resolves precisely because its scope is unknown until that spike returns a result — this is the one phase whose internal design literally cannot be finalized without an answer to an open question.
- Phase 7 (promotion/ladder) has no upstream dependency beyond release assembly and could in practice run in parallel with Phases 4-6; it's sequenced last here only because nothing else depends on it and the audit-trail value increases once there are documents to tie promotions to.
- Deferred v1.x items (scope-change diff, advisory readiness flags, promotion sign-off notes, promotion timeline UI) attach to Phases 3, 2, and 7 respectively and can be scheduled as fast-follow work once each base phase is validated with real data, per Features' MVP Definition.

### Research Flags

Needs deeper research during planning:
- **Phase 2 (Jira Integration):** Jira Cloud vs Data Center decision unresolved (changes API version and auth model); dev-status/ADO-link discovery mechanism is undocumented and instance-dependent — needs a go/no-go spike against the real target instance before Phase 6 can be scoped.
- **Phase 4 (Template Engine):** Highest-risk decision in the project per all three converging research files; even with jmustache settled as the engine, the exact shape of the view-model, placeholder catalog format, and validation-render mechanics warrant a short design spike before implementation.
- **Phase 6 (ADO Enrichment):** Directly dependent on Phase 2's spike outcome; also needs the org-scoped PAT / credential-expiry design worked out given the Dec 2026 global-PAT retirement.

Phases with standard, well-documented patterns (skip `/gsd-research-phase`):
- **Phase 1 (Foundation):** Standard Spring Boot + Flyway + Vite single-jar setup, extensively documented.
- **Phase 3 (Release Assembly):** Straightforward CRUD-plus-snapshot domain modeling.
- **Phase 5 (Document Generation):** Mechanics are settled by Phase 4's output; remaining work is standard CRUD/export plumbing (format choice is a decision, not a research gap).
- **Phase 7 (Promotion Tracking):** Directly mirrors Octopus Deploy's well-documented Lifecycle model; append-only table design is standard.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH for backend framework, build tooling, and template-engine safety analysis; MEDIUM for Jira/ADO integration mechanics (reliance on an internal, undocumented Atlassian endpoint) and frontend toolchain (fast-moving ecosystem) | Per STACK.md's own header; versions verified same-day against Maven Central/npm registry |
| Features | MEDIUM-HIGH | Grounded in named, documented products (Jira, Octopus, Digital.ai Release, ServiceNow, Docxtemplater/Carbone); a few specifics (exact Jira dev-status shape) explicitly flagged LOW confidence |
| Architecture | MEDIUM-HIGH | Hexagonal architecture, Resilience4j, anti-corruption-layer, and Spring provider-selection patterns are well-documented and cross-verified; the Jira dev-status/ADO-link mechanism is confirmed to exist but is unofficial and needs project-specific verification |
| Pitfalls | HIGH | Jira/ADO API behavior and template-engine CVEs verified against official docs and 2026-dated vendor advisories; design/scope pitfalls are MEDIUM (pattern-recognition from internal-tooling experience, not single-source-citable) |

**Overall confidence:** MEDIUM-HIGH — strong, independent convergence across all four files on the two biggest decisions (template engine safety, ADO link-discovery risk), plus a right-sized architecture recommendation none of the four disputed.

### Gaps to Address

- **Blocks a specific phase — Jira Cloud vs Data Center:** determines API version (`/rest/api/3` + ADF vs `/rest/api/2` + plain text) and auth model (API token/Basic vs Bearer PAT). Resolve before/during Phase 2 planning; both paths are equally well-documented so this doesn't block roadmap creation itself.
- **Blocks a specific phase — Jira->ADO link discovery mechanism:** dev-status may be empty for ADO if the marketplace connector isn't installed on the target instance. Requires a go/no-go verification spike against a real ticket before Phase 6 (and ideally during Phase 2) is scoped in detail.
- **Deferred design detail — multiple applications/products sharing one instance:** assumed yes per PROJECT.md; Architecture confirms this is a data-modeling change (add a tenant/application dimension to `Release`), not an architecture change, so it doesn't reshape phase structure — confirm before Phase 3 data modeling.
- **Deferred design detail — environment ladder definition and in-tool approval step:** assumed DEV->TEST->UAT->PROD-style; needs confirming before Phase 7's ladder is hardcoded, but doesn't block earlier phases.
- **Deferred design detail — document export format selection:** Stack research supplies tooling for Markdown/PDF/DOCX (flexmark/openhtmltopdf/docx4j); which formats actually matter to the release manager is still open — resolve during Phase 5 planning, not before.
- **Deferred design detail — authentication method** (SSO/LDAP vs local accounts): single-role, single-user-type keeps authorization trivial either way; can be resolved at any point without affecting phase structure.
- **Resolved by research, no longer open:** whether generated documents are stored or regenerated on demand — Architecture settles this as stored, as immutable `GeneratedDocument` snapshots (template version pinned), which is also what makes template versioning and audit-trail integrity possible.

## Sources

### Primary (HIGH confidence)
- https://spring.io/blog/2026/06/10/spring-boot-4/ and https://docs.spring.io/spring-boot/system-requirements.html — Spring Boot 4.1.0 / Java baseline
- Maven Central + npm registry metadata fetched 2026-07-27 — current dependency versions
- https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/ and https://developer.atlassian.com/server/jira/platform/about-the-jira-server-rest-apis/ — Jira Cloud vs Data Center API shape
- https://support.atlassian.com/jira/kb/access-git-repo-commit-data-via-jira-rest-api-in-development-panel-data/ — official Atlassian confirmation that dev-status is internal/undocumented
- https://developer.atlassian.com/changelog/#CHANGE-2046 — legacy `/search` endpoint deprecation, `/search/jql` migration
- https://securitylab.github.com/advisories/GHSL-2020-050-pebble/, https://www.endorlabs.com/learn/its-about-thyme-how-a-whitespace-character-broke-thymeleafs-expression-sandbox-cve-2026-40478 — template-engine sandbox-bypass CVE evidence
- https://devblogs.microsoft.com/devops/retirement-of-global-personal-access-tokens-in-azure-devops/ — ADO global PAT retirement date
- https://learn.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer — canonical anti-corruption-layer pattern
- https://octopus.com/docs/releases/lifecycles — environment-ladder/lifecycle model reference

### Secondary (MEDIUM confidence)
- https://www.baeldung.com/spring-boot-resilience4j — Resilience4j configuration patterns
- Deviniti / Release Management for Jira release-notes grouping documentation — feature-landscape evidence
- Community consensus (multiple blog sources) on Flyway vs Liquibase and React/TanStack ecosystem choices in 2026

### Tertiary (LOW confidence)
- Exact current shape of the Jira dev-status/development-info response for Azure DevOps links — needs project-specific verification spike, flagged consistently across Stack, Architecture, and Pitfalls

---
*Research completed: 2026-07-27*
*Ready for roadmap: yes*
