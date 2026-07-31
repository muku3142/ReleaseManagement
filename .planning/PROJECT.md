# ReleaseManagement

## What This Is

A web application that lets a release manager assemble a release from Jira tickets, auto-generate the release paperwork from templates (release notes, deployment runbook, approval/change documents), and track that release as it is promoted through a fixed environment ladder. Java Spring Boot REST backend with a React single-page frontend. Jira is the primary integration and the ticket source; where a Jira ticket happens to link to Azure DevOps, the system follows that link to enrich the ticket with its commits and pull requests.

## Core Value

Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Release manager signs in with their own Jira credentials, which scope what they can read
- [ ] Release manager can connect the system to a Jira Cloud site and browse/select tickets for a release
- [ ] Release manager can assemble a release: a named, versioned set of Jira tickets
- [ ] Where a Jira ticket links to Azure DevOps, system follows that link and enriches the ticket with its commits and pull requests
- [ ] Release manager can author templates with placeholders that resolve against release, ticket, and commit/PR data
- [ ] System generates release notes from a template
- [ ] System generates a deployment runbook (deploy and rollback steps) from a template
- [ ] System generates approval / change-request documents from a template
- [ ] Release manager can preview generated documents against a real release's data and export them as PDF and DOCX
- [ ] Release manager can promote a release through a fixed environment ladder and see where it currently sits
- [ ] System retains release and deployment history as an audit trail
- [ ] Ticket-source integration sits behind a provider abstraction so a second provider can be added without reworking core

### Out of Scope

- Performing deployments — the tool records deployments; CI/CD systems execute them
- Triggering CI/CD pipelines — deliberate boundary; keeps the tool read-only against delivery infrastructure
- Writing to Jira (creating/releasing versions, transitioning issues, commenting) — this app is the system of record for releases; Jira stays a read-only ticket source
- Non-Jira ticket providers (Azure DevOps Boards, GitHub Issues, GitLab) — abstraction is defined in v1, additional implementations are a later stage
- Azure DevOps work-item mirroring — only commits and PRs are linked
- Ingesting Azure DevOps build/pipeline run status — deferred; deployment state is recorded in this tool, not read from ADO
- Per-application configurable environment paths — one standard ladder in v1
- Rebuilding Jira's own agile reports — velocity, cycle time, sprint burndown, sprint scope change. This tool sits *beside* Jira, not over it: Jira ships those reports and ships them well, and rebuilding them would cost a whole new integration surface (Agile API for boards/sprints, paginated `/changelog` for transitions, the story-point custom field) to produce a worse version of something already available. **Dashboards here must answer what Jira structurally cannot** — release documentation status, environment-ladder position, promotion history — all of which live in this system's own database because Jira does not model them at all.

## Context

**Problem being replaced.** Today the release manager does this work by hand in Jira: hand-creating release versions, chasing which tickets are actually in a release, and writing release notes and deployment instructions manually. It is slow, inconsistent between releases, and leaves a weak audit trail.

**System-of-record shift.** The important structural change is that release and deployment state moves *out* of Jira and into this tool. Jira becomes purely an upstream ticket source that is read, never written. This is what makes "replaces manual Jira work" and "read-only against Jira" consistent with each other.

**Two integrations, unequal status.** Jira is the main integration and answers *what is shipping* (tickets, their metadata). Azure DevOps is secondary and conditional: it is reached only when a Jira ticket already links to it, and it answers *what code implements this ticket* (commits, pull requests). Link discovery flows from Jira outward — the system does not independently query Azure DevOps for a release. Tickets with no Azure DevOps link are normal and must not degrade release assembly or document generation; generated documents show code detail only for the tickets that have it. Only Jira sits behind the pluggable provider abstraction.

**Template engine is the differentiating piece.** Three document types are needed on day one, and users must be able to author their own templates rather than pick from fixed formats. This makes the placeholder/data-binding model — what fields a template can reach, and how — the highest-risk design decision in the project.

**Audience.** A single primary user role: the release manager. Approvers, ops and stakeholders consume the generated documents but are not users of the tool in v1. This keeps permissions and collaboration features simple.

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
| Java Spring Boot backend | User requirement; organisational standard | — Pending |
| React SPA on a REST API | Rich template-authoring and preview UI needs a real client-side app; server-rendered templating would fight the live editor | — Pending |
| Jira read-only; this app is the system of record | Avoids two-way sync complexity and write-scope risk; makes release history independent of Jira project changes | — Pending |
| Provider abstraction in v1, Jira as the only implementation | User expects other providers later; retrofitting an abstraction after Jira-specific code spreads is far more expensive than defining it up front | — Pending |
| Track deployments only — never execute or trigger them | Keeps the tool out of the delivery critical path and removes the need for deployment credentials | — Pending |
| Azure DevOps reached only via links present on a Jira ticket, at commit/PR level | User's stated need; makes ADO an optional enrichment rather than a required integration, so the tool works against Jira alone | — Pending |
| Fixed environment ladder rather than per-app configuration | Simplifies the promotion model for v1; configurability can be added once the ladder is proven | — Pending |
| User-authored templates rather than fixed document formats | User explicitly asked for a template generator, not three hardcoded reports | — Pending |
| Logic-less template engine (Mustache/jmustache), not FreeMarker, Pebble, Thymeleaf or Handlebars.java | Templates are authored by users, so template source is untrusted input. Every full-expression Java engine has a documented sandbox-bypass history — including a CVSS 9.1 Thymeleaf bypass disclosed in 2026 — and several vendors state outright that they assume trusted authors. A logic-less engine has no expression language or class-resolution surface to escape from, so this is prevention rather than mitigation | — Pending |
| Templates see a dedicated flattened view-model, never domain or JPA entities | Two reasons that reinforce each other: it removes the reflection/method-call surface that makes template injection possible, and it stops user templates from binding to internal field names — otherwise no domain refactor is ever safe again, with no compiler to catch the breakage | — Pending |
| Generated documents stored as immutable snapshots with template version pinned | An audit trail is worthless if a template edit retroactively changes what a three-month-old document says. Regeneration produces a new revision instead of mutating history | — Pending |
| Modular monolith — no microservices, event sourcing, CQRS or message broker | Single-role internal tool. Hexagonal package boundaries give the isolation that actually matters (keeping Jira concepts out of the domain) without distributed-system cost | — Pending |
| Authenticate by reusing the user's own Jira credentials | Removes an entire user-store and password-reset surface, and makes the tool's read access exactly equal to the user's own Jira permissions rather than a broader service account | — Pending |

## Open Questions

Resolved after research (2026-07-27):

- **Jira flavour** → **Jira Cloud**. Fixes the API to `/rest/api/3/`, authentication to API-token Basic auth, and means rich-text fields arrive as Atlassian Document Format and must be flattened.
- **Authentication** → **reuse the user's own Jira credentials**. The release manager supplies their Jira email and API token; validating it against Jira both authenticates them and naturally scopes what tickets they can read. No separate user store.
- **Document export formats** → **PDF and DOCX**. Markdown and HTML are not required as deliverables, though HTML is likely an internal intermediate on the way to PDF.
- **Azure DevOps link discovery** → the Jira instance **has the Azure DevOps connector app installed**, so Jira's Development-panel data should carry commits and pull requests. The endpoint behind it is still Atlassian's internal, undocumented `dev-status` API, so this remains verify-first, not assume-first.
- **Persistence** → PostgreSQL, and generated documents are **stored as immutable snapshots** with the template version pinned, not regenerated on demand. This is what makes the audit trail trustworthy when Jira data or a template later changes.

Still open, deferred to phase discussion — not blocking roadmap creation:

- **Multiple applications**: whether one instance manages releases for several applications/products sharing the one standard environment ladder. Assumed yes; this is a data-model dimension, not an architecture change. Confirm before release data modelling.
- **Environment ladder definition**: the actual stages (assumed a DEV → TEST → UAT → PROD style path) and whether promotion requires an in-tool sign-off step.

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
*Last updated: 2026-07-27 after initialization*
