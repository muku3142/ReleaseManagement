# Feature Research

**Domain:** Release management / deployment tracking (release assembly from Jira, template-driven documentation, environment-ladder promotion tracking)
**Researched:** 2026-07-27
**Confidence:** MEDIUM-HIGH (grounded in named products' documented behavior; some specifics — e.g. exact Jira dev-status API shape — are LOW confidence and flagged)

## Context Recap

Single user role (release manager). No deployment execution, no CI/CD triggering, no Jira writes. This product occupies a specific, narrower niche than any single competitor: it takes Jira's release/version concept (scope assembly, release notes) and Digital.ai Release / Octopus Deploy's tracked-promotion concept (environment ladder, deployment record, audit trail), and adds a general-purpose, user-authored template engine that ServiceNow/Jira/Octopus do not offer end users. Findings below are organized around the five focus areas in the research question, then rolled into table-stakes/differentiator/anti-feature tables.

## 1. Release Assembly

**Real products and how they scope a release:**

- **Jira Software** — a "version" (`fixVersion`) is the unit of release scope. Tickets are added to a version by setting the Fix Version field; the Release Hub shows progress against that version. Jira's own release action ("Release" button) checks for unresolved issues at release time and warns — but distinguishes **status** (Done) from **resolution** (set or not), a frequent source of false "unresolved" warnings. [Atlassian Support — release warning](https://support.atlassian.com/jira/kb/when-releasing-the-version-a-warning-regarding-unresolved-issues-is-displayed-even-though-these-issues-are-marked-as-completed/), [community reports of this confusion](https://community.atlassian.com/forums/Jira-questions/Releasing-version-always-says-unresolved-issue/qaq-p/2741931). Jira lets a user override and "ignore unresolved issues" and release anyway — the check is advisory, not a hard gate. This is the correct model to copy: **readiness is a warning, not a lock.**
- **Digital.ai Release / Octopus Deploy** operate one layer downstream — they assume a release's contents (artifacts, work items) are already decided and focus on orchestrating the *pipeline*, not on curating scope. Scope curation from a ticket system is Jira's job in these ecosystems, reinforcing that ticket-driven scope assembly is this product's unique territory, not something to copy wholesale from a pipeline tool.
- **Scope freeze / cutoff:** industry practice (general PM literature, not release-tool specific) treats freeze as "stop accepting new unassessed commitments, keep executing already-approved work," and explicitly calls out that when the freeze is broken, the change should be tracked, not silently absorbed — re-baseline only when the change is material enough to invalidate the original plan. [ProjectManagement.com — Scope Freeze](https://www.projectmanagement.com/articles/245003/scope-freeze). Translated to this product: a release, once cut, is a snapshot; tickets added/removed afterward should be visible as a **diff against the frozen snapshot**, not just silently reflected in an always-current query.
- **"Not actually ready" detection** is a known pain point that Jira itself only half-solves natively — teams resort to a manual checklist ticket with sub-tasks, or a third-party checklist add-on (e.g. Smart Checklist), because Jira has no first-class "release readiness" concept. [Titanapps — Release Readiness Checklist](https://titanapps.io/blog/release-readiness-checklist), [Atlassian — sub-task blocking condition](https://support.atlassian.com/jira/kb/how-to-prevent-issues-from-being-closed-while-the-sub-tasks-are-still-open-in-jira/). This is a gap in the market this product can close cheaply: since the tool already reads full ticket data, it can flag (not block) tickets whose Jira status isn't in a "done-like" category, whose resolution is unset, or that have open linked/blocking issues — this is a differentiator, not a solved problem elsewhere.

**Conclusions for this product:**
- Named, versioned release = one bounded set of Jira ticket keys, assembled by JQL/filter/manual pick (table stakes, mirrors Jira's own fixVersion pattern).
- Cutoff should be an explicit action that snapshots the ticket set (table stakes) — this is what makes an audit trail meaningful later.
- Post-cutoff additions/removals must be recorded as a delta with timestamp and (optionally) reason, not silently merged — this is a differentiator relative to Jira's own version mechanic, which has no concept of "what changed since freeze."
- Readiness flags (ticket not Done, resolution unset, has open blockers/sub-tasks, missing required custom field) should be advisory warnings surfaced at generation/promotion time, never a hard block — matches how Jira itself behaves, and correctly respects that this tool doesn't own ticket workflow (it's read-only against Jira).

## 2. Release Documentation Generation

**What real release-notes/runbook/change documents contain, in practice:**

- **Jira's own release notes** (Server/DC "Create release notes" and Cloud release-notes generators) group issues by **issue type** (Bug, Story, Task, Improvement) within a fix version, list summary + key, and support project-specific templates using a Java templating engine (Velocity, historically). Third-party Jira release-note apps go further, letting teams group by **epic, component, or any single/multi-select field** — grouping is explicitly a top user need, not just a flat list. [Deviniti — Release Notes for Jira](https://deviniti.com/support/addon/cloud/issue-templates/latest/release-notes/), [Release Management for Jira — Flexible Release Notes](https://releasemanagement.atlassian.net/wiki/spaces/RM4J/pages/1081356/Flexible+Release+Notes), [Confluence — Creating release notes](https://confluence.atlassian.com/adminjiraserver071/creating-release-notes-802592502.html). Custom sections like "Known Issues" and "Upcoming Features" are common additions on top of the ticket list.
- **GitLab Releases** package a tag with release notes, and support associating one or more **milestones** and linking assets — reinforcing that a release note document is a grouped, dated snapshot with links out to supporting artifacts, not free text. [GitLab Releases docs](https://docs.gitlab.com/user/project/releases/).
- **Deployment runbooks**, per multiple independent write-ups (Enov8, oneuptime, Nobl9, techleadmentor), converge on the same essential structure regardless of vendor:
  - Metadata: version/date, owner, applicable environment(s), classification.
  - Pre-deployment checks / preconditions.
  - Step-by-step deployment instructions — specific enough that "an engineer who has never seen this before" can follow them (the recurring theme across sources is **specificity kills ambiguity**: "run X and check for Y," not "verify system is healthy"). [Hacker News — What Makes a Good Runbook?](https://news.ycombinator.com/item?id=21545184), [oneuptime — Effective Runbooks](https://oneuptime.com/blog/post/2026-02-02-effective-runbooks/view).
  - Post-deployment validation steps.
  - Rollback/backout procedure with explicit trigger conditions ("roll back if X"), not just "revert if something goes wrong." [Enov8 — Deployment Runbooks Explained](https://www.enov8.com/blog/deployment-runbooks-aka-runsheets-explained/).
  - **What makes a runbook useless in practice** (consistent across sources): vague steps ("check the system"), no owner, no rollback trigger criteria, stale/never-updated content, and steps that assume tribal knowledge instead of being self-contained.
- **Change/approval documents** (ServiceNow Change Management is the reference implementation most enterprises benchmark against) consistently include: description/justification, **risk assessment** (impact, priority, scope — each rated on a scale), implementation plan (sequenced steps with owners), **backout plan** (explicit conditions + steps to reverse), test plan (pre- and post-implementation), schedule/change window, and an approvals section naming who signed off. [ServiceNow Community — CAB process](https://www.servicenow.com/community/itsm-forum/change-management-in-servicenow-everything-you-need-to-know/m-p/3439058), [Change Management Template — iseoblue](https://www.iseoblue.com/post/change-management-template). ServiceNow's CAB is a **multi-step approval workflow** (assignment-group approval → CAB approval → Scheduled) — this is heavier process than a single-user-role tool needs; see anti-features below.

**Conclusions for this product:**
- All three document types share a spine: metadata header, grouped ticket/change list, and a narrative/procedural body — the template data model should expose release metadata, a grouped-and-sorted ticket collection, and (where available) commit/PR detail uniformly to all three document types, letting the template author decide what appears where.
- A release-notes template needs, at minimum, grouping by issue type and by epic/component as first-class, pre-built groupings (not something the template author must hand-roll from a flat list every time) — this is what every competitor's release-notes feature actually optimizes for.
- A runbook template's value is entirely about specificity and having a real rollback section — if this product's template data model doesn't give the author a natural place to put "trigger condition" and "rollback steps" per deployment/environment, generated runbooks will be as useless as the generic ones the runbook literature warns against.
- The approval document should carry risk/impact metadata and a backout-plan section as data model fields available to templates, but the *workflow* around approval (multi-step CAB-style routing) is explicitly out of this product's scope (single user role) — the document is generated for a human to sign outside the tool, not routed through an in-tool approval chain.

## 3. Template Authoring

**How comparable products let users define documents:**

- **Jira's own release notes** historically used **Velocity** templates server-side (Java ecosystem, consistent with this project's Spring Boot backend) — precedent for a Java-native templating approach without inventing a new syntax. [Confluence — Jira Software release notes](https://confluence.atlassian.com/jirasoftware/jira-software-release-notes-776821069.html).
- **Digital.ai Release / XL Release** exposes reusable "templates" as first-class objects (a release template = predefined phases/tasks + variables), with a library of example templates on GitHub and support for scripting (Jython/Groovy tasks) inside a template for advanced logic. This validates **template-as-versionable-object** and **variables resolved from a defined data model**, though XL Release's templates are pipeline/process templates, not document templates. [Digital.ai Release docs](https://docs.digital.ai/release/docs/how-to/get-started-with-xl-release), [xlr-template-examples](https://github.com/xebialabs-community/xlr-template-examples).
- **Document-generation libraries** (the closest analog to "user authors a template that resolves against a data model"):
  - **Docxtemplater** — user edits a real Word/PowerPoint file with tags like `{user}`; the engine takes JSON data and replaces tags, and explicitly advertises that non-programmers can edit the templates. Supports loops and conditionals for repeating sections (e.g., one row per ticket). [docxtemplater.com](https://docxtemplater.com/), [open-xml-templating/docxtemplater](https://github.com/open-xml-templating/docxtemplater).
  - **Carbone** — similar model (design template in familiar tools, render deterministically to PDF/DOCX/XLSX/PPTX), emphasizing byte-identical, repeatable rendering — relevant because a release/change document often needs to be reproducible evidence, not just "close enough." [carbone.io](https://carbone.io/).
  - General template-engine landscape (Handlebars, Jinja/FreeMarker/Velocity for the JVM side) all converge on `{{ }}`-style placeholders, loops, conditionals, and filters/helpers for formatting — this is the de facto expected placeholder syntax users will recognize regardless of exact engine chosen. [Jinja docs](https://jinja.palletsprojects.com/), [template engine comparison](https://medium.com/@asifazad114/demystifying-template-engines-in-javascript-ejs-handlebars-pug-and-more-69202b78b5b9).

**Conclusions for this product:**
- Placeholder syntax should follow the near-universal `{{ }}`-family convention (Handlebars/Mustache-like) rather than inventing new syntax — every competitor and template library converges here, and it is what a release manager will already recognize from Confluence/Jira templates.
- The data model exposed to templates must include: release metadata (name, version, dates, environment/state), the ticket collection with per-ticket fields (see section 5) groupable/sortable inside the template (loops + a documented "group by" helper, matching what Jira release-notes tools consider essential), and per-ticket optional commit/PR detail that may be absent (must not error when missing — matches the "ADO must degrade gracefully" constraint already in PROJECT.md).
- **Per-document-type templates** are table stakes — release notes, runbook, and approval document are different documents with different data needs (runbook needs environment/rollback fields the release notes template doesn't); a single universal template is not what any real product does.
- **Template versioning** is a genuine differentiator worth taking seriously: Digital.ai Release treats templates as reusable, evolvable objects. For this product, pinning *which template version* produced a *specific* generated document (so a 3-month-old release's generated runbook doesn't silently change if the template is edited later) is the kind of audit-trail-adjacent detail competitors in the document-generation space (Carbone's "byte-identical" framing) treat as core value, and this product's PROJECT.md context already flags the template engine as "the highest-risk design decision" — versioning is how you keep past documents faithful to what was actually used.
- **Live preview** bound to a real release's actual data (not sample/dummy data) is what separates a good authoring experience from a frustrating one — Docxtemplater/Carbone's whole pitch is "design in a familiar tool, see real output"; this product should let the release manager preview against one of their real, in-progress releases while editing a template.

## 4. Deployment Tracking

**How comparable products represent promotion through environments:**

- **Octopus Deploy Lifecycles** are the closest conceptual match to "fixed environment ladder": phases progress strictly linearly (no skipping, no branching), each phase maps to one or more environments, and environments become cumulatively available as a release advances (e.g., reaching Staging means Dev/UAT/Staging are all now valid deploy targets for that release). [Octopus Docs — Lifecycles](https://octopus.com/docs/releases/lifecycles), [RFC — Lifecycles](https://octopus.com/blog/lifecycles-rfc). This maps directly onto "fixed environment ladder" in PROJECT.md and is strong validation that a **linear, non-branching ladder with a per-release "current position"** is the right mental model, not a fully general state machine.
- **Azure DevOps Releases** layer **approvals and gates** on top of environment stages — pre-/post-deployment conditions that can require manual human approval or automated external checks before a stage is considered entered. [Understand release gates, checks, and approvals](https://learn.microsoft.com/en-us/azure/devops/pipelines/release/approvals/?view=azure-devops). This product does not execute or gate deployments, but the **concept of an approval/sign-off recorded against a promotion step** is worth carrying over as a lightweight, single-user-role-appropriate feature: recording *that* a promotion happened and optionally who/when signed off, without any workflow engine behind it.
- **Octopus Deploy auditing**: every deployment activity and system change is captured automatically (who did what, when), scoped by project/environment, with enterprise SIEM export. [Octopus — Auditing](https://octopus.com/docs/security/users-and-teams/auditing), [Octopus blog — Auditing and History](https://octopus.com/blog/auditing-and-history). This is the reference bar for "what audit trail is expected" — a deployment record isn't just current state, it's an append-only history of every state transition with actor and timestamp, independently of who's currently viewing it.
- **ServiceNow Change** shows the enterprise-heavy end of this spectrum (multi-party CAB approval, scheduled implementation windows, mandatory post-implementation review) — useful as an upper bound of complexity to explicitly *not* copy given the single-user-role constraint.

**Conclusions for this product:**
- A deployment record per environment-hop should contain, at minimum: release reference, target environment, timestamp, actor (the release manager), outcome/status, and optional free-text notes — this mirrors what Octopus's audit log and ADO's deployment record both consider baseline.
- The environment ladder should be modeled as Octopus models lifecycles: a strictly ordered list of stages; a release's "current state" is its furthest/most-recent stage, and history retains every prior hop — not just the latest one.
- Given this tool doesn't execute deployments, "gates" (ADO-style automated health checks) are not applicable, but a lightweight recorded acknowledgement/approval note per promotion (not a routed workflow) is a reasonable, low-complexity nod to the approval concept every real product in this space has in some form.

## 5. Jira-Sourced Data

**Which fields matter for release notes in practice, per named tools and Atlassian's own docs:**

- **Issue type** — the primary grouping axis in Jira's own release notes and every third-party generator surveyed (Bug / Story / Task / Improvement sections). Table stakes.
- **Fix Version** — defines release membership itself; the field the whole release-assembly model is built on.
- **Summary + issue key** — the minimum per-ticket line item in every release-notes format found.
- **Components** and **Labels** — explicitly named as supported grouping fields in both Jira's native release-notes feature and third-party generators (Deviniti, Release Management for Jira) — teams commonly group or filter by these after issue type. [Deviniti — Release Notes fields](https://deviniti.com/support/addon/cloud/issue-templates/latest/release-notes/), [Flexible Release Notes](https://releasemanagement.atlassian.net/wiki/spaces/RM4J/pages/1081356/Flexible+Release+Notes).
- **Epic Link / Epic Name** — a common secondary grouping level, letting release notes present "delivered by epic/initiative" rather than flat issue-type buckets; explicitly supported by modern release-notes generators alongside components/labels.
- **Custom fields** — treated as first-class in flexible generators (single/multi-select custom fields are explicitly supported for grouping), reflecting real-world usage where organizations track things like "Customer Impact" or "Release Notes Text" as custom fields specifically to control what appears externally. A "Known Issues"/"Upcoming Features" free-text custom field is a recurring pattern for injecting non-ticket content into generated notes.
- **Resolution vs Status** — a subtlety Jira itself gets tripped up on (see section 1): "Done" status does not imply "Resolved" resolution. Any readiness/completeness check this product implements must check resolution, not just status category, or it will inherit the exact confusion Jira users already complain about.
- **Priority** — used less for grouping and more for surfacing high-impact items (e.g., "Critical fixes in this release") in some generators, secondary to issue type/component.

**Typical grouping/sorting pattern observed across tools:** group by issue type first (Features/Enhancements, Bug Fixes, Known Issues as top-level sections — a pattern also visible in GitLab's conventional-commit-driven changelogs, which bucket by feat/fix/breaking-change, the git-native analog of Jira's issue-type bucketing), then sort within each group by component or epic, with the issue key/summary as the leaf line item. This product's data model should treat issue type as the default/primary group key but allow the template author to choose a different one, since real tools support multiple grouping fields, not just one fixed hierarchy.

## Feature Landscape

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Connect to a Jira instance and browse/filter tickets (by project, fix version, JQL) | Every Jira-adjacent release tool (native Jira Releases, third-party release-note generators) starts here | MEDIUM | Needs Jira Cloud/Server auth (API token/OAuth) behind the provider abstraction already planned |
| Assemble a named, versioned release from a selected ticket set | This is the fixVersion pattern from Jira itself, and the unit every downstream feature (docs, promotion, audit) hangs off | LOW-MEDIUM | Core domain entity |
| Group and sort tickets by issue type, component, label, epic in generated documents | Confirmed as standard across Jira's own release notes and every third-party generator surveyed | MEDIUM | Issue type = default primary group; must be configurable, not hardcoded |
| Per-document-type templates (release notes, runbook, approval doc are separate templates) | No competitor bundles these into one universal template; each has a different data need | MEDIUM | Already an Active requirement in PROJECT.md |
| `{{ }}`-style placeholder syntax with loops/conditionals | Universal convention across Handlebars/Jinja/FreeMarker/Velocity and Jira's own historical Velocity-based release notes | MEDIUM-HIGH | Choosing/embedding a Java template engine is the core technical risk PROJECT.md already flags |
| Live template preview against real release data | Docxtemplater/Carbone's core value proposition; authoring blind is a known source of frustration | MEDIUM | Should preview against an actual selected release, not dummy data |
| Export generated documents (at minimum one durable format) | Every product surveyed treats the generated document as a shareable artifact, not a UI-only view | LOW-MEDIUM | Format choice (PDF/DOCX/Markdown/HTML) is an open question already flagged in PROJECT.md |
| Fixed, linear environment ladder with a visible "current stage" per release | Direct match to Octopus Deploy Lifecycles' phase model | MEDIUM | Cumulative/linear, no branching — mirrors Octopus, not a general state machine |
| Deployment/promotion record per environment hop (who, when, outcome) | Baseline expectation from Octopus's audit log and ADO's deployment record | LOW-MEDIUM | Append-only; this is the audit trail's raw material |
| Full release + deployment history retained as audit trail | Explicit Active requirement; matches Octopus auditing and ServiceNow change history norms | MEDIUM | Every state transition, not just current state |
| Graceful handling of tickets with no Azure DevOps link | Already an Active requirement/constraint; also just good practice — most tickets in most orgs won't have this link | LOW-MEDIUM | Must not error or blank out sections when ADO data absent |
| Advisory "ticket not ready" flags (status not done-like, resolution unset, open blockers) | Confirmed real gap — Jira's own release warning is confusing and binary; third-party checklist add-ons exist because Jira has no native concept of this | MEDIUM | Must be a warning, never a hard block — this tool is read-only against Jira and doesn't own workflow |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Scope-change diff since cutoff (tickets added/removed after freeze, with timestamp) | No competitor surveyed (Jira, XL Release, Octopus) models "what changed since we froze scope" — Jira's version is always current-state only | MEDIUM | Directly addresses the "chasing which tickets are actually in a release" pain named in PROJECT.md's problem statement |
| Template versioning — pin the exact template version used to produce a historical document | Digital.ai Release treats templates as versioned objects; Carbone's "byte-identical" framing shows the market values reproducibility | MEDIUM-HIGH | Prevents a template edit from retroactively changing what a 3-month-old generated document "should" have looked like — supports audit-trail integrity |
| Commit/PR enrichment surfaced per ticket in release notes, sourced via Jira→ADO link discovery | Neither Jira's native release notes nor XL Release/Octopus surface "here's the actual code" alongside the ticket description in the same document | HIGH | Already an Active requirement; the differentiation is doing it without a required ADO integration (link discovery is opportunistic) |
| Configurable grouping/sorting per template (not just a fixed issue-type hierarchy) | Matches what advanced Jira release-note generators (Deviniti, Release Management for Jira) charge for as a premium feature | MEDIUM | Template author picks group-by field(s) rather than being stuck with one hierarchy |
| Environment-ladder promotion history as a visual timeline per release | Builds on Octopus's audit log data but presents it release-centric rather than log-centric | LOW-MEDIUM | Mostly a UI investment on top of table-stakes data already captured |
| Lightweight sign-off note per promotion step (who acknowledged, optional comment) | Nods to ADO/ServiceNow's approval concept without building a routed workflow engine, appropriate to single-user-role reality | LOW | Not a workflow — just a recorded field, since there's only one user role |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| Execute deployments / trigger CI/CD pipelines | Octopus Deploy, Azure DevOps Releases, and XL Release all do this — looks like the "obvious" next step once you track deployments | Explicitly out of scope in PROJECT.md; pulls the tool into the delivery critical path, requires deployment credentials, and turns a read-mostly audit tool into a systems-of-execution with much higher blast radius | Keep the tool as system of record only — a human or the CI/CD system performs the deploy and the release manager records the outcome here |
| Multi-step CAB-style approval routing (assignment group → CAB → scheduled) | ServiceNow's Change Management is the enterprise reference and looks authoritative | Only one user role exists in v1 (release manager); a routed multi-approver workflow has no one to route to and adds an entire workflow engine for zero present value | Generate the approval document with risk/impact/backout fields for a human to sign outside the tool; record a simple sign-off note if needed later |
| Write back to Jira (create/release versions, transition issues, comment) | Feels natural since the tool already reads deeply from Jira, and would "close the loop" | Explicitly excluded in PROJECT.md — this app is meant to become the system of record for release state, and writing to Jira reintroduces two-way sync complexity and write-scope risk this design deliberately avoids | Jira stays read-only; all release/deployment state lives only in this tool |
| Automated release gates / health checks (ADO-style pre/post-deployment gates hitting external monitoring APIs) | Azure DevOps Releases and XL Release both support this and it feels like "safety" | No deployment execution happens here, so there is nothing for a gate to guard; would require integrating with monitoring systems for a purely observational tool | The advisory "ticket not ready" flags cover the equivalent need at the ticket-readiness level; environment promotion itself stays a simple recorded action |
| Per-application configurable environment ladders / multi-tenant channels (Octopus Channels/tenants) | Larger orgs with many products often want per-app paths, and Octopus explicitly supports this | Explicitly deferred in PROJECT.md ("one standard ladder in v1"); adds a config surface and data-model complexity before the single-ladder model is even proven | Ship one fixed ladder; revisit configurability once real usage validates the need |
| Git-commit-driven changelog generation (conventional commits → changelog, GitLab/GitHub style) as the primary release-notes source | GitLab/GitHub-style changelog generators are popular and simple to build | Jira is this product's system of truth for "what is shipping," not the git log; commit-driven changelogs would create two disagreeing narratives of the same release and bypass the whole ticket-curation model | Commits/PRs enrich a ticket's entry (via the ADO link) but never replace the Jira ticket as the unit of release content |
| Real-time multi-user collaboration (comments, mentions, notifications, concurrent editing) on releases/templates | Looks modern; most SaaS PM tools have it | Single user role in v1 (release manager only); approvers/stakeholders are document consumers, not tool users — collaboration infrastructure has no audience yet | Keep single-user assumptions in the data model; documents are the collaboration surface (shared externally), not the tool itself |
| Built-in incident/ITSM ticketing or CAB scheduling calendar | ServiceNow bundles this and it looks like a natural adjacent feature | Out of this product's stated problem (replacing manual Jira release bookkeeping), and duplicates what an org's existing ITSM tool already does | If a change window/schedule needs recording, add it as a data field on the release, not a scheduling subsystem |

## Feature Dependencies

```
Jira ticket browse/select (provider abstraction)
    └──requires──> Jira connection/auth configured

Release assembly (named, versioned ticket set)
    └──requires──> Jira ticket browse/select
    └──enables──> Scope-change diff since cutoff
    └──enables──> Advisory ticket-readiness flags
    └──enables──> Document generation (all 3 types)
    └──enables──> Environment-ladder promotion tracking

ADO commit/PR enrichment
    └──requires──> Jira ticket data (link discovery flows from Jira outward)
    └──enhances──> Release notes / runbook document content
    (must degrade gracefully when absent — not a hard dependency of document generation)

Template authoring (placeholders + data model)
    └──requires──> Defined data model (release, ticket, commit/PR fields)
    └──enables──> Document generation (all 3 types)
    └──enables──> Live preview

Template versioning
    └──requires──> Template authoring
    └──enhances──> Audit trail integrity (documents stay faithful to template-as-used)

Document generation (release notes, runbook, approval doc)
    └──requires──> Release assembly
    └──requires──> Template authoring
    └──enables──> Preview and export

Environment-ladder promotion tracking
    └──requires──> Release assembly (a release must exist before it can be promoted)
    └──enables──> Deployment record per hop
    └──enables──> Promotion history timeline

Audit trail (release + deployment history)
    └──requires──> Release assembly
    └──requires──> Environment-ladder promotion tracking
    └──enhances──> All of the above (append-only history layer)

Lightweight sign-off note per promotion ──enhances──> Environment-ladder promotion tracking

Multi-step CAB approval routing ──conflicts──> Single-user-role reality (v1)
Deployment execution/CI-CD triggering ──conflicts──> Read-only/system-of-record design boundary
```

### Dependency Notes

- **Release assembly requires Jira browse/select:** you cannot name a scoped release without first being able to see and choose tickets — this is the foundational phase-ordering constraint for the roadmap.
- **Document generation requires both release assembly and template authoring:** a template with no release to bind to, or a release with no template to render against, produces nothing — these two features must land in the same phase or template authoring must precede documents with assembly as a hard prerequisite.
- **ADO enrichment enhances but does not gate document generation:** per PROJECT.md's explicit resilience constraint, documents must generate correctly for tickets with no ADO link — this shapes the data model (commit/PR fields must be optional/nullable in the template context) rather than the phase order.
- **Template versioning enhances audit-trail integrity but is not a hard prerequisite of document generation:** it can ship after basic generation works, once there's a real need to distinguish "document generated with template v1" from "v2" — a reasonable v1.x candidate rather than launch-blocking.
- **Scope-change diff and readiness flags both require release assembly but not each other:** they can be built independently and in either order once a release exists.
- **CAB-style approval routing and deployment execution both conflict with explicit v1 boundaries** (single user role; system-of-record, not system-of-execution) — flagged here so the roadmap does not accidentally reintroduce them as "obvious" additions.

## MVP Definition

### Launch With (v1)

This maps directly onto PROJECT.md's Active requirements, refined by research:

- [ ] Jira connection + ticket browse/select behind a provider abstraction — foundation for everything else
- [ ] Release assembly: named, versioned ticket set with an explicit cutoff/freeze action — the freeze action is what gives the later scope-diff feature something to diff against
- [ ] ADO link-follow enrichment (commits/PRs) for tickets that have it, gracefully absent otherwise
- [ ] Template authoring with `{{ }}`-style placeholders resolving against release/ticket/commit data, one template per document type
- [ ] Release notes generation, grouped by issue type by default, configurable to component/epic/label
- [ ] Deployment runbook generation with explicit deploy-steps and rollback-steps sections in the data model
- [ ] Approval/change document generation with risk/impact and backout-plan fields in the data model
- [ ] Preview and export of generated documents
- [ ] Fixed environment ladder with linear promotion and a visible current stage per release
- [ ] Release + deployment history as an append-only audit trail

### Add After Validation (v1.x)

- [ ] Scope-change diff since cutoff (tickets added/removed post-freeze) — high-value but can follow once basic assembly/freeze is proven with real releases
- [ ] Advisory ticket-readiness flags (status/resolution mismatch, open blockers) — valuable but requires validating exactly which Jira fields/states this org's workflow uses first
- [ ] Template versioning (pin template version to generated document) — matters more once there's a body of historical documents to protect
- [ ] Promotion history as a visual timeline — a presentation layer over data already captured in v1
- [ ] Lightweight sign-off note per promotion step — small addition once the promotion mechanic itself is proven

### Future Consideration (v2+)

- [ ] Per-application configurable environment ladders — explicitly deferred in PROJECT.md until the single ladder is proven
- [ ] Additional ticket-source providers (Azure DevOps Boards, GitHub Issues, GitLab) — the abstraction is built in v1, additional implementations are a later stage per PROJECT.md
- [ ] Ingesting ADO build/pipeline run status — deferred per PROJECT.md; deployment state stays recorded, not read from ADO

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Jira browse/select + release assembly with cutoff | HIGH | MEDIUM | P1 |
| Template authoring with placeholder data model | HIGH | HIGH | P1 |
| Release notes generation (grouped) | HIGH | MEDIUM | P1 |
| Runbook generation with rollback section | HIGH | MEDIUM | P1 |
| Approval document generation | MEDIUM | MEDIUM | P1 |
| Environment ladder + promotion tracking | HIGH | MEDIUM | P1 |
| Audit trail (release + deployment history) | HIGH | LOW-MEDIUM | P1 |
| ADO commit/PR enrichment | MEDIUM | HIGH | P1 (already Active, but riskiest single item due to link-discovery mechanism being unconfirmed per PROJECT.md open questions) |
| Scope-change diff since cutoff | HIGH | MEDIUM | P2 |
| Advisory ticket-readiness flags | MEDIUM | MEDIUM | P2 |
| Template versioning | MEDIUM | MEDIUM-HIGH | P2 |
| Promotion history timeline UI | LOW-MEDIUM | LOW | P3 |
| Lightweight promotion sign-off note | LOW | LOW | P3 |
| Per-app configurable ladders | MEDIUM | HIGH | P3 (deferred by PROJECT.md) |
| Additional ticket-source providers | MEDIUM | HIGH | P3 (deferred by PROJECT.md) |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature area | Jira (native) | Octopus Deploy / Azure DevOps Releases | Digital.ai Release (XL Release) | ServiceNow Change | This product |
|---|---|---|---|---|---|
| Release scope definition | fixVersion field, manually maintained, always current-state | Not applicable (assumes artifact/scope already decided) | Release = phases/tasks, not ticket curation | Change record scoped to one change, not a batch | Named/versioned ticket set with explicit freeze + post-freeze diff |
| Ready-to-release check | Binary unresolved-issues warning, confusable status-vs-resolution semantics, overridable | Pipeline gates check external system health, not ticket readiness | N/A | CAB approval is the "readiness" gate, human-driven | Advisory flags on ticket status/resolution/blockers, never a hard block |
| Release notes | Native grouping by issue type; third-party apps add component/label/epic grouping and Velocity templating | Not a core feature | Not a core feature | Not a core feature | Configurable grouping (issue type default) via user-authored templates |
| Runbook / deployment steps | Not a feature | Pipeline definition is the de facto "runbook," but it's executable config, not readable prose | Release template = executable orchestration | Implementation plan is a document field, not executed | User-authored runbook template with explicit deploy/rollback sections, descriptive not executable |
| Approval / change document | Not a feature | Approvals are workflow gates, not a generated document | Not a document; approval is a task type in the pipeline | Full-fidelity change record with risk/impact/backout/approvals as the core object | Generated document with risk/impact/backout fields, signed outside the tool (no routed workflow) |
| Environment promotion model | Not applicable | Linear, cumulative lifecycle phases (Octopus) / staged pipeline with gates (ADO) | Configurable pipeline stages, executable | Change scheduled for one implementation window | Fixed linear ladder, recorded not executed |
| Audit trail | Version release history, issue changelog | Full audit log (who/what/when), SIEM export (Octopus) | Task execution history | Full change history, mandatory in regulated orgs | Append-only release + deployment history, no execution log (since nothing is executed) |
| Template authoring for end users | Fixed formats, or Velocity if self-hosting and technical | Not user-facing (pipeline definitions are for engineers) | Templates exist but are process/pipeline templates, not documents, and need scripting skill | Fixed form fields, not user-templatable | Full user-authored, per-document-type templates with live preview — the core differentiator |

## Sources

- [Managing Versions on Release Board — Release Management for Jira](https://releasemanagement.atlassian.net/wiki/spaces/RMC/pages/92111031/Tracking+versions+on+release+board)
- [Jira versions explained — Titanapps](https://titanapps.io/blog/jira-versions)
- [Learn versions with Jira Tutorial — Atlassian](https://www.atlassian.com/agile/tutorials/versions)
- [Jira Software release notes — Atlassian Confluence](https://confluence.atlassian.com/jirasoftware/jira-software-release-notes-776821069.html)
- [When releasing the version, unresolved issues warning — Atlassian Support](https://support.atlassian.com/jira/kb/when-releasing-the-version-a-warning-regarding-unresolved-issues-is-displayed-even-though-these-issues-are-marked-as-completed/)
- [Releasing version always says unresolved issue — Atlassian Community](https://community.atlassian.com/forums/Jira-questions/Releasing-version-always-says-unresolved-issue/qaq-p/2741931)
- [Option to prevent release of version with unresolved issues — Atlassian Jira issue tracker](https://jira.atlassian.com/browse/JRASERVER-69055)
- [A Free Release Readiness Checklist Template — Titanapps](https://titanapps.io/blog/release-readiness-checklist)
- [How to prevent Closing Issues with Open Sub-Tasks — Atlassian Support](https://support.atlassian.com/jira/kb/how-to-prevent-issues-from-being-closed-while-the-sub-tasks-are-still-open-in-jira/)
- [Release Notes — Issue Templates for Jira (Deviniti)](https://deviniti.com/support/addon/cloud/issue-templates/latest/release-notes/)
- [Flexible Release Notes — Release Management for Jira Data Center](https://releasemanagement.atlassian.net/wiki/spaces/RM4J/pages/1081356/Flexible+Release+Notes)
- [Creating release notes — Atlassian Confluence](https://confluence.atlassian.com/adminjiraserver071/creating-release-notes-802592502.html)
- [Understand release gates, checks, and approvals — Microsoft Learn (Azure Pipelines)](https://learn.microsoft.com/en-us/azure/devops/pipelines/release/approvals/?view=azure-devops)
- [Control Deployments using Release Gates — Microsoft Learn](https://microsoftlearning.github.io/mslearn-devops/Instructions/advanced/04-control-deployments-release-gates.html)
- [ServiceNow Change Management: Everything You Need to Know — ServiceNow Community](https://www.servicenow.com/community/itsm-forum/change-management-in-servicenow-everything-you-need-to-know/m-p/3439058)
- [Change Management Template — iseoblue](https://www.iseoblue.com/post/change-management-template)
- [Digital.ai Release — Getting Started](https://docs.digital.ai/release/docs/how-to/get-started-with-xl-release)
- [Digital.ai Release Example Templates and Demos — GitHub](https://github.com/xebialabs-community/xlr-template-examples)
- [Pros and Cons of Digital.ai Release 2025 — TrustRadius](https://www.trustradius.com/products/digital-ai-release/reviews?qs=pros-and-cons)
- [Octopus Deploy — Lifecycles documentation](https://octopus.com/docs/releases/lifecycles)
- [RFC — Lifecycles — Octopus blog](https://octopus.com/blog/lifecycles-rfc)
- [Octopus Deploy best practices — releases and deployments (GitHub)](https://github.com/OctopusDeploy/docs/blob/main/src/pages/docs/best-practices/deployments/releases-and-deployments.md)
- [Octopus Deploy — Auditing documentation](https://octopus.com/docs/security/users-and-teams/auditing)
- [New Feature — Auditing And History — Octopus blog](https://octopus.com/blog/auditing-and-history)
- [GitLab Releases documentation](https://docs.gitlab.com/user/project/releases/)
- [Deployment RunBooks (aka Runsheets) Explained in Depth — Enov8](https://www.enov8.com/blog/deployment-runbooks-aka-runsheets-explained/)
- [What Makes a Good Runbook? — Hacker News discussion](https://news.ycombinator.com/item?id=21545184)
- [How to Build Effective Runbooks — oneuptime](https://oneuptime.com/blog/post/2026-01-26-effective-runbooks-guide/view)
- [Docxtemplater — official site](https://docxtemplater.com/)
- [open-xml-templating/docxtemplater — GitHub](https://github.com/open-xml-templating/docxtemplater)
- [Carbone — Open Source Report and Document Generator](https://carbone.io/)
- [Jinja — official documentation](https://jinja.palletsprojects.com/)
- [ProjectManagement.com — Scope Freeze](https://www.projectmanagement.com/articles/245003/scope-freeze)

---
*Feature research for: release management / deployment tracking web application*
*Researched: 2026-07-27*
