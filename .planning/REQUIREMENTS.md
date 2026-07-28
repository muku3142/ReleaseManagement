# Requirements: ReleaseManagement

**Defined:** 2026-07-27
**Core Value:** Given a set of Jira tickets, produce accurate and consistent release documentation plus a tracked deployment record — without anyone doing manual release bookkeeping in Jira.

## v1 Requirements

Requirements for initial release. Each maps to exactly one roadmap phase.

### Authentication

- [ ] **AUTH-01**: Release manager can sign in by supplying their Jira email and API token, which the system validates against Jira before granting access
- [ ] **AUTH-02**: All Jira reads are performed with the signed-in user's own credential, so the tool never sees tickets the user could not see in Jira
- [ ] **AUTH-03**: Stored Jira credentials are encrypted at rest and never returned to the client after being saved
- [ ] **AUTH-04**: Release manager can replace an expired or revoked Jira token without losing existing releases, templates or history

### Jira Integration

- [x] **JIRA-01**: Release manager can configure which Jira Cloud site the system reads from
- [ ] **JIRA-02**: Release manager can browse candidate tickets filtered by project, fix version, and JQL
- [ ] **JIRA-03**: Release manager sees the complete ticket set matching a filter, with paging handled to exhaustion rather than silently truncating at the first page
- [ ] **JIRA-04**: Release manager sees the fields the tool needs even on Jira instances with differing custom-field configurations, because custom fields are resolved by name through field metadata rather than hardcoded IDs
- [ ] **JIRA-05**: Jira rich-text fields arrive as Atlassian Document Format and are rendered as readable text suitable for inclusion in generated documents
- [ ] **JIRA-06**: Release manager gets a clear, actionable error when Jira is unreachable, rate-limits the request, or rejects the credential — distinguishable from "no results"

### Release Assembly

- [ ] **REL-01**: Release manager can create a named, versioned release
- [ ] **REL-02**: Release manager can add and remove Jira tickets on a release while it is still open
- [ ] **REL-03**: Release manager can freeze a release, which snapshots its exact ticket set at that moment
- [ ] **REL-04**: Release manager can open any release and see its ticket set with per-ticket detail
- [ ] **REL-05**: Release manager can list and search all releases

### Template Authoring

- [ ] **TMPL-01**: Release manager can create and edit a separate template for each document type — release notes, deployment runbook, approval document
- [ ] **TMPL-02**: Templates use `{{ }}`-style placeholders that resolve against a documented, fixed view-model of release, ticket and code-reference data, with no expression language, method invocation or arbitrary property traversal available to the template
- [ ] **TMPL-03**: Release manager can browse a catalog of every available placeholder while authoring, with a description of what each resolves to
- [ ] **TMPL-04**: System validates a template when it is saved by rendering it against a sample context that includes a ticket with no linked code, and reports unknown placeholders as errors rather than silently blanking them at generation time
- [ ] **TMPL-05**: Template rendering is bounded by an execution timeout and a collection-size limit, so no template can hang or exhaust the server
- [ ] **TMPL-06**: Release manager can preview a template against a real selected release's actual data while editing

### Document Generation

- [ ] **DOC-01**: Release manager can generate release notes for a release from its template
- [ ] **DOC-02**: Release notes group tickets by issue type by default, and a template can group by component, epic or label instead
- [ ] **DOC-03**: Release manager can generate a deployment runbook whose data model provides distinct places for deploy steps, post-deploy validation, and a rollback section with explicit trigger conditions
- [ ] **DOC-04**: Release manager can generate an approval/change document whose data model provides risk, impact and backout-plan fields
- [ ] **DOC-05**: Release manager can export any generated document as PDF
- [ ] **DOC-06**: Release manager can export any generated document as DOCX
- [ ] **DOC-07**: Each generated document is stored as an immutable snapshot recording the template version, content and generation timestamp, so regenerating creates a new revision instead of altering the previous one

### Azure DevOps Enrichment

- [ ] **ADO-01**: Where a Jira ticket links to Azure DevOps, release manager sees that ticket's linked commits and pull requests
- [ ] **ADO-02**: Tickets with no Azure DevOps link display and generate documents normally — no error, no empty sections, no failed generation
- [ ] **ADO-03**: Release manager can tell the difference between a ticket that has no Azure DevOps link and one whose enrichment was attempted and failed
- [ ] **ADO-04**: Azure DevOps access uses an org-scoped credential with a tracked expiry date, and the release manager is warned before it expires
- [ ] **ADO-05**: Enriching a release containing many tickets completes in bounded time through parallel, cached lookups rather than one sequential call per ticket

### Deployment Tracking

- [ ] **DEPL-01**: Release manager can see the fixed environment ladder and which stage each release currently sits at
- [ ] **DEPL-02**: Release manager can record the promotion of a release to the next environment in the ladder
- [ ] **DEPL-03**: Every promotion is recorded as an immutable entry capturing target environment, timestamp, actor and outcome
- [ ] **DEPL-04**: Release manager can attach a free-text note to a promotion

### Audit Trail

- [ ] **AUDIT-01**: Release manager can view the complete promotion history of a release, not just its current stage
- [ ] **AUDIT-02**: Release manager can view every document generated for a release, including superseded revisions
- [ ] **AUDIT-03**: Release and promotion history is append-only — a correction adds a new entry rather than editing or deleting an existing one

## v2 Requirements

Deferred to future release. Tracked but not in the current roadmap.

### Release Assembly

- **REL-06**: Release manager can see a scope-change diff since cutoff — which tickets were added or removed after the freeze, and when
- **REL-07**: Release manager sees advisory "not ready" flags on tickets whose resolution is unset, whose status is not done-like, or that have open blockers — as warnings that never block progress

### Template Authoring

- **TMPL-07**: Release manager can view template version history and see exactly which version produced a given historical document

### Deployment Tracking

- **DEPL-05**: Release manager can record a sign-off note naming who acknowledged each promotion step
- **DEPL-06**: Release manager can view promotion history as a visual timeline per release

### Provider Coverage

- **PROV-01**: Additional ticket-source providers (Azure DevOps Boards, GitHub Issues, GitLab) implemented against the existing provider abstraction
- **PROV-02**: Per-application configurable environment ladders rather than one standard ladder

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Performing deployments | The tool records deployments; CI/CD systems execute them. Keeps the tool out of the delivery critical path and removes any need for deployment credentials |
| Triggering CI/CD pipelines | Same boundary. Once the tool can trigger, it becomes a system of execution with a far larger blast radius |
| Writing anything back to Jira — creating or releasing versions, transitioning issues, commenting | This app is the system of record for release state. Writing to Jira reintroduces two-way sync complexity and write-scope risk the design deliberately avoids |
| Ingesting Azure DevOps build or pipeline run status | Deployment state is recorded here, not read from ADO. Deferred |
| Azure DevOps work-item mirroring | Only commits and pull requests are linked; work items would duplicate what Jira already answers |
| Multi-step CAB-style approval routing | Only one user role exists, so a routed multi-approver workflow has nobody to route to and would add a workflow engine for no present value. The approval document is generated for a human to sign outside the tool |
| Automated release gates hitting external monitoring systems | Nothing is executed here, so there is nothing for a gate to guard |
| Git-commit-driven changelog generation as the primary release-notes source | Jira is the source of truth for what is shipping. Commit-driven changelogs would create a second, disagreeing narrative of the same release and bypass ticket curation entirely. Commits enrich a ticket's entry; they never replace it |
| Real-time multi-user collaboration — comments, mentions, notifications, concurrent editing | Single user role in v1. Approvers and stakeholders consume the generated documents; they are not users of the tool |
| Built-in ITSM ticketing or change-window scheduling calendar | Outside the stated problem and duplicates the organisation's existing ITSM tooling. A change window can be a field on the release if needed |
| FreeMarker, Pebble, Thymeleaf or Handlebars.java as the template engine | Templates are untrusted user input. All four have documented sandbox-bypass histories, including a CVSS 9.1 Thymeleaf bypass disclosed in 2026. A logic-less engine is used instead |
| Microservices, event sourcing, CQRS, message broker | Single-role internal tool. A modular monolith with hexagonal package boundaries gives the isolation that matters at a fraction of the cost |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Pending |
| AUTH-02 | Phase 1 | Pending |
| AUTH-03 | Phase 1 | Pending |
| AUTH-04 | Phase 1 | Pending |
| JIRA-01 | Phase 1 | Complete |
| JIRA-02 | Phase 2 | Pending |
| JIRA-03 | Phase 2 | Pending |
| JIRA-04 | Phase 2 | Pending |
| JIRA-05 | Phase 2 | Pending |
| JIRA-06 | Phase 2 | Pending |
| REL-01 | Phase 3 | Pending |
| REL-02 | Phase 3 | Pending |
| REL-03 | Phase 3 | Pending |
| REL-04 | Phase 3 | Pending |
| REL-05 | Phase 3 | Pending |
| TMPL-01 | Phase 4 | Pending |
| TMPL-02 | Phase 4 | Pending |
| TMPL-03 | Phase 4 | Pending |
| TMPL-04 | Phase 4 | Pending |
| TMPL-05 | Phase 4 | Pending |
| TMPL-06 | Phase 4 | Pending |
| DOC-01 | Phase 5 | Pending |
| DOC-02 | Phase 5 | Pending |
| DOC-03 | Phase 5 | Pending |
| DOC-04 | Phase 5 | Pending |
| DOC-05 | Phase 5 | Pending |
| DOC-06 | Phase 5 | Pending |
| DOC-07 | Phase 5 | Pending |
| ADO-01 | Phase 6 | Pending |
| ADO-02 | Phase 6 | Pending |
| ADO-03 | Phase 6 | Pending |
| ADO-04 | Phase 6 | Pending |
| ADO-05 | Phase 6 | Pending |
| DEPL-01 | Phase 7 | Pending |
| DEPL-02 | Phase 7 | Pending |
| DEPL-03 | Phase 7 | Pending |
| DEPL-04 | Phase 7 | Pending |
| AUDIT-01 | Phase 7 | Pending |
| AUDIT-02 | Phase 7 | Pending |
| AUDIT-03 | Phase 7 | Pending |

**Coverage:**
- v1 requirements: 40 total (AUTH 4 + JIRA 6 + REL 5 + TMPL 6 + DOC 7 + ADO 5 + DEPL 4 + AUDIT 3) — confirmed 2026-07-27, supersedes the "37" originally stated here
- Mapped to phases: 40/40 ✓
- Unmapped: 0 ✓

---
*Requirements defined: 2026-07-27*
*Last updated: 2026-07-27 after roadmap creation (7 phases, full coverage)*
