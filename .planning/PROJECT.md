# ReleaseManagement

## What This Is

A web application that lets a release manager assemble a release from Jira tickets, auto-generate the release paperwork from templates (release notes, deployment runbook, approval/change documents), and track that release as it is promoted through an environment ladder. Java Spring Boot REST backend with a React single-page frontend. Jira is the primary integration and the ticket source; where a Jira ticket happens to link to Azure DevOps, the system follows that link to enrich the ticket with its commits and pull requests. Since v1.1 a read-only assistant answers questions over this data with sources, and generated documents can be refined by a locally hosted model, both through a standalone reusable RAG service.

## Core Value

Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.

## Current State

**Shipped:** v1.0 MVP (Phases 1-8) and v1.1 Release Assistant (Phases 9-14), both closed 2026-10-20. See
`.planning/MILESTONES.md`. Several human checks were closed on the user's word rather than observed, and
the v1.1 gate reports were never produced; both are listed there.

Codebase: about 17.7k lines of Java in the app, 5.2k in `rag-service/`, 10k of TypeScript in the frontend;
Flyway at V13. Last recorded suites: backend 1260, frontend 537, rag-service 360.

## Current Milestone: v1.2 Portfolio & Planning

**Goal:** Track which products and clients each release reaches, give technical product managers a ranked roadmap, show how the app is used, and add two AI aids: deployment notes drawn from Azure DevOps commits, and a daily plan.

**Target features:**
- **Products and clients:** a release covers several products; each product has several clients; the app records which release each client installed and when, on the unchanged shared ladder; the dashboard shows releases by product and what each client runs
- **RICE roadmap page:** items read from Jira plus items a TPM adds by hand, scored by Reach, Impact, Confidence and Effort and ranked in this app
- **Usage tracking:** page and feature events captured in the frontend, stored as aggregate counts with no user identity, shown as a usage matrix on the dashboard
- **AI deployment notes (runbook Recommend):** a new AI mode on the deployment runbook that reads the release's ADO commit messages and PR titles and status, and suggests deploy steps, config and DB changes, risks and validation checks, saved as a revision after review
- **Daily planning assistant:** a page proposing what to work on today. Ollama by default; an admin may configure an optional external OpenAI-compatible provider, which sends data off the machine only when enabled and says so clearly

## Requirements

### Validated

- ✓ Sign-in with the user's own Jira credentials, scoping what they can read — v1.0
- ✓ Connect to a Jira Cloud site and browse/select tickets for a release — v1.0
- ✓ Assemble a named, versioned release from Jira tickets and manual items — v1.0
- ✓ Follow Jira's links to Azure DevOps and enrich tickets with commits and pull requests — v1.0
- ✓ Author templates with placeholders over release, ticket and commit/PR data — v1.0
- ✓ Generate release notes, deployment runbooks and approval documents from templates — v1.0
- ✓ Preview generated documents and export them as PDF and DOCX — v1.0
- ✓ Promote a release through the environment ladder and see where it sits — v1.0
- ✓ Retain release and deployment history as an audit trail — v1.0
- ✓ Ticket-source integration behind a provider abstraction — v1.0
- ✓ Dashboard of release lifecycle, composition, churn and story points — v1.0
- ✓ Ask questions in plain language and get sourced answers from a local model — v1.1
- ✓ Refine a generated document with the model and save it as an AI-assisted revision — v1.1

### Active

(v1.2 requirements: see `.planning/REQUIREMENTS.md`; target features under Current Milestone.)

### Out of Scope

- Performing deployments — the tool records deployments; CI/CD systems execute them
- Triggering CI/CD pipelines — deliberate boundary; keeps the tool read-only against delivery infrastructure
- Writing to Jira (creating/releasing versions, transitioning issues, commenting) — this app is the system of record for releases; Jira stays a read-only ticket source
- Non-Jira ticket providers (Azure DevOps Boards, GitHub Issues, GitLab) — abstraction is defined in v1, additional implementations are a later stage
- Azure DevOps work-item mirroring — only commits and PRs are linked
- Ingesting Azure DevOps build/pipeline run status — deferred; deployment state is recorded in this tool, not read from ADO
- Per-application configurable environment paths — one standard ladder in v1
- Rebuilding Jira's own agile reports — velocity, cycle time, sprint burndown, sprint scope change. This tool sits *beside* Jira, not over it: Jira ships those reports and ships them well, and rebuilding them would cost a whole new integration surface (Agile API for boards/sprints, paginated `/changelog` for transitions, the story-point custom field) to produce a worse version of something already available. **Dashboards here must answer what Jira structurally cannot** — release documentation status, environment-ladder position, promotion history — all of which live in this system's own database because Jira does not model them at all.

  > **Partially reversed 2026-08-04.** The *reports* stay out of scope — that reasoning holds. But the **Agile API is now in scope**, because Phase 2 filters candidate tickets by **sprint**: releases here are scoped by sprint, not by fix version, so `GET /rest/agile/1.0/board` and `/board/{id}/sprint` are read to populate the sprint picker (`JiraProjectAdapter.listSprints`). Only board and sprint *listing* is used — no burndown, no story points, no `/changelog`. Fix-version filtering was removed in the same change. Cost incurred: one extra undocumented-stability surface (the Agile API is versioned and documented, unlike `dev-status`) and the two-call board→sprint hop, since Jira does not expose sprints per project directly.

  > **`/changelog` partially reversed 2026-10-07 (INSIGHT-03).** The user wants to tell development
  > problems (many commits before first Done) apart from testing problems (reopened after Done). Jira's
  > resolution date cannot do that: it is overwritten on re-resolution, so reopen-window commits look
  > like development commits. The paginated `/changelog` is therefore read under tight limits:
  > - only for tickets that belong to a release;
  > - only on refresh, and only when the ticket's `updated` time has changed;
  > - only the status changes are stored.
  >
  > It feeds per-ticket first-Done time and reopen count, combined with Azure DevOps commit dates. The
  > *reports* stay out of scope: no velocity, no Jira cycle time, no burndown.

  > **Story points partially reversed 2026-10-14 (INSIGHT-04).** The user wants the dashboard to show
  > the **total story points of each release**. Releases are this system's own grouping of tickets, so
  > Jira cannot answer "how big is this release". The story-point custom field is therefore read per
  > ticket. Its id differs per site and duplicate "Story Points" names are common, so it is resolved by
  > name through the existing field-metadata lookup. Only a per-release **total** is shown. Velocity,
  > per-sprint points and burndown stay out of scope.

## Context

**Problem being replaced.** Today the release manager does this work by hand in Jira: hand-creating release versions, chasing which tickets are actually in a release, and writing release notes and deployment instructions manually. It is slow, inconsistent between releases, and leaves a weak audit trail.

**System-of-record shift.** The important structural change is that release and deployment state moves *out* of Jira and into this tool. Jira becomes purely an upstream ticket source that is read, never written. This is what makes "replaces manual Jira work" and "read-only against Jira" consistent with each other.

**Two integrations, unequal status.** Jira is the main integration and answers *what is shipping* (tickets, their metadata). Azure DevOps is secondary and conditional: it is reached only when a Jira ticket already links to it, and it answers *what code implements this ticket* (commits, pull requests). Link discovery flows from Jira outward — the system does not independently query Azure DevOps for a release. Tickets with no Azure DevOps link are normal and must not degrade release assembly or document generation; generated documents show code detail only for the tickets that have it. Only Jira sits behind the pluggable provider abstraction.

**Template engine is the differentiating piece.** Three document types are needed on day one, and users must be able to author their own templates rather than pick from fixed formats. This makes the placeholder/data-binding model — what fields a template can reach, and how — the highest-risk design decision in the project.

**Audience.** The release manager is the primary user role. v1.2's roadmap page widens this to technical product managers; there is still a single permission level.

**Assistant.** Self-hosted Ollama only, so no release or Jira data leaves the machine. The assistant is read-only. Ticket text, comments and commit messages are untrusted and can carry prompt injection; the dev model is `qwen3:4b-instruct-2507-q4_K_M` on a 4 GB GPU, and a stronger production model must be measured before it is relied on.

## Constraints

- **Tech stack**: Backend must be Java Spring Boot — user requirement, matches team/organisational standard
- **Tech stack**: Frontend is a React SPA consuming a REST API — user selection; specific toolchain (build tool, TypeScript, component library, rich-text/template editor component) to be settled during research
- **Dependencies**: Jira is read-only — no write scope required or used against Jira APIs
- **Dependencies**: Azure DevOps is optional and read-only, limited to repository data (commits, pull requests), and reached only by following links found on a Jira ticket
- **Resilience**: Azure DevOps being unlinked, unreachable or unauthorised must degrade gracefully — release assembly and document generation continue without code detail
- **Architecture**: Ticket-source access must go through a provider interface from the start, even though only Jira is implemented
- **Security**: Template source is untrusted user input — the rendering path must have no expression language, no method invocation and no arbitrary property traversal, and must be bounded in execution time and collection size
- **External deadline**: Jira's legacy `/rest/api/*/search` endpoint already returns HTTP 410 Gone — build against `/search/jql` with cursor pagination and the dedicated paginated `/changelog` endpoint from day one. `expand=changelog` silently truncates at around 100 entries
- **External deadline**: Azure DevOps retires global Personal Access Tokens on 1 December 2026, inside this project's build window — the credential model must use org-scoped tokens with tracked expiry from the start rather than being reworked later

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java Spring Boot backend | User requirement; organisational standard | ✓ Good (v1.0) |
| React SPA on a REST API | Rich template-authoring and preview UI needs a real client-side app; server-rendered templating would fight the live editor | ✓ Good (v1.0) |
| Jira read-only; this app is the system of record | Avoids two-way sync complexity and write-scope risk; makes release history independent of Jira project changes | ✓ Good (v1.0) |
| Provider abstraction in v1, Jira as the only implementation | User expects other providers later; retrofitting an abstraction after Jira-specific code spreads is far more expensive than defining it up front | ✓ Good (v1.0) |
| Track deployments only — never execute or trigger them | Keeps the tool out of the delivery critical path and removes the need for deployment credentials | ✓ Good (v1.0) |
| Azure DevOps reached only via links present on a Jira ticket, at commit/PR level | User's stated need; makes ADO an optional enrichment rather than a required integration, so the tool works against Jira alone | ✓ Good (v1.0) |
| One environment ladder (not per-app), stages editable in Settings | One ladder keeps the promotion model simple; the user chose Settings-editable stages (DEV → TEST → UAT → PROD seeded, one stage flagged Production) over code-fixed — see 07-CONTEXT D-07-01..04 | Decided 2026-10-07 |
| User-authored templates rather than fixed document formats | User explicitly asked for a template generator, not three hardcoded reports | ✓ Good (v1.0) |
| Logic-less template engine (Mustache/jmustache), not FreeMarker, Pebble, Thymeleaf or Handlebars.java | Templates are authored by users, so template source is untrusted input. Every full-expression Java engine has a documented sandbox-bypass history — including a CVSS 9.1 Thymeleaf bypass disclosed in 2026 — and several vendors state outright that they assume trusted authors. A logic-less engine has no expression language or class-resolution surface to escape from, so this is prevention rather than mitigation | ✓ Good (v1.0) |
| Templates see a dedicated flattened view-model, never domain or JPA entities | Two reasons that reinforce each other: it removes the reflection/method-call surface that makes template injection possible, and it stops user templates from binding to internal field names — otherwise no domain refactor is ever safe again, with no compiler to catch the breakage | ✓ Good (v1.0) |
| Generated documents stored as immutable snapshots with template version pinned | An audit trail is worthless if a template edit retroactively changes what a three-month-old document says. Regeneration produces a new revision instead of mutating history | ✓ Good (v1.0) |
| Modular monolith — no microservices, event sourcing, CQRS or message broker | Single-role internal tool. Hexagonal package boundaries give the isolation that actually matters (keeping Jira concepts out of the domain) without distributed-system cost | ✓ Good (v1.0) |
| **Exception (2026-10-13): a standalone, domain-free RAG service** in `rag-service/` (own pom, Dockerfile, compose service, `ragdb` database) | The user wants retrieval-augmented Q&A reusable by other internal apps in any language. Callers push documents (`PUT/DELETE /v1/collections/{c}/documents/{id}`) and **run their own tools** — the service returns tool calls and never calls into apps or holds their credentials. Per-app API keys; Ollama is the only outbound call. Stack: Java 25 / Boot 4.1 MVC, pgvector 0.8.7, Flyway + JdbcClient, RestClient to Ollama, SseEmitter, springdoc-openapi; no AI framework. ReleaseManagement is the first client. Off-the-shelf RAG servers (AnythingLLM, Dify, RAGFlow) rejected: second stack, own user model, tool model doesn't fit. | ✓ Good (v1.1) |
| Authenticate by reusing the user's own Jira credentials | Removes an entire user-store and password-reset surface, and makes the tool's read access exactly equal to the user's own Jira permissions rather than a broader service account | ✓ Good (v1.0) |

## Open Questions

- **Multiple applications / clients** → partly answered by v1.2's client list: one instance serves several products, each with several clients, on the one shared ladder. Per-client ladders (v2's PROV-02) stay deferred.
- **External LLM for the planning assistant** → decided 2026-10-20: Ollama by default, optional admin-configured external provider. This reverses no-egress only for that feature and only when enabled.

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-10-20 at the start of milestone v1.2*
