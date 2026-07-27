# Pitfalls Research

**Domain:** Release management tool integrating Jira + Azure DevOps, with a user-authored template engine (Spring Boot + React)
**Researched:** 2026-07-27
**Confidence:** HIGH (Jira/ADO API behavior and template-engine CVEs verified against official docs and vendor advisories dated 2026; design/scope pitfalls MEDIUM — pattern-recognition from internal-tooling experience, not a single citable source)

## Critical Pitfalls

### Pitfall 1: Treating Jira's dev-status endpoint as a documented, stable API

**What goes wrong:**
The "Development panel" data on a Jira issue (branches, commits, pull requests, builds) is served by `/rest/dev-status/1.0/issue/detail` (Cloud) or the equivalent Data Center endpoint. Atlassian has never published this as a public, versioned REST API — there is no entry for it in the `developer.atlassian.com` API catalog. Teams find it via community reverse-engineering, wire it into an integration, and then it silently changes shape, moves fields, or requires parameters (`applicationType`, `dataType`) that aren't documented anywhere except forum posts. Atlassian's own bug tracker has open, unresolved reports of the endpoint returning partial data or dropping branch associations entirely (JSWCLOUD-16901, JSWSERVER-15768, and a community report titled "REST API for issue detail no longer returns the associated branches").

**Why it happens:**
It's the only endpoint that returns "what code implements this ticket," which is exactly what this project needs, so it's tempting to treat it as load-bearing infrastructure rather than what it actually is: an internal implementation detail Jira happens to expose over HTTP.

**How to avoid:**
- Do not build the core ADO-link-discovery mechanism solely on dev-status. Treat it as one signal among several.
- The dev-status/"Development panel" only gets populated at all if something is feeding it — either a native VCS app (Bitbucket, GitHub) or, for Azure DevOps specifically, a marketplace-installed connector app ("Azure DevOps Integration for Jira" / "Azure DevOps for Jira") that bridges ADO commits/PRs into Jira's development panel. **If that connector app isn't installed on the target Jira instance, dev-status will return nothing for ADO — not an error, just empty.** This must be verified against a real ticket in the target instance before committing to dev-status as the discovery mechanism (this is already flagged as an open question in PROJECT.md — treat it as a go/no-go spike, not a background research task).
- Build a fallback discovery path that doesn't depend on that connector: parse Jira issue key patterns (`PROJ-123`) out of ADO commit messages and PR titles/descriptions directly via the ADO API (Smart Commit / branch-naming convention: Atlassian's own Smart Commits docs confirm the issue key just needs to appear anywhere in the branch name or commit message — `git checkout -b PROJ-123-fix-thing`). This is more reliable because it's your own query against ADO, not a dependency on a third-party Jira app's internal state.
- Wrap all dev-status calls in a resilience boundary (timeout, empty-result-is-normal, no retry storms) — this aligns directly with the PROJECT.md constraint that ADO being unreachable must degrade gracefully.

**Warning signs:**
- Integration works in a demo Jira Cloud sandbox (which has the Bitbucket/GitHub apps pre-installed) but returns nothing in the client's real instance.
- Field names or nesting in dev-status responses differ between two Jira instances of supposedly the same product tier.

**Phase to address:** Azure DevOps enrichment phase — but the *verification spike* (does this instance's dev-status even carry ADO data, and how) should happen before that phase is scoped, ideally during architecture/provider-design phase.

---

### Pitfall 2: Building the release-notes template engine around a Java engine's sandbox instead of never handing it untrusted templates

**What goes wrong:**
This is the highest-risk area in the project per PROJECT.md, and the domain has a hard, well-documented lesson: general-purpose Java template engines (FreeMarker, Velocity, Thymeleaf, Pebble, JinJava) all include some form of sandbox to stop template authors from reaching arbitrary Java/OS execution, and that sandbox has repeatedly been bypassed in the wild:
- **FreeMarker**: sandbox/class-resolver bypasses, e.g. CVE-2021-25770 (SSTI below 2.3.30), plus a steady stream of further bypasses documented by PortSwigger and independent researchers.
- **Thymeleaf**: CVE-2026-40478 (CVSS 9.1, disclosed 2026) — the SpEL expression sandbox was bypassed using a tab character instead of a space in the `new` keyword check, plus an incomplete class-package blocklist (only `java.*` was blocked; `org.springframework.*`, `ognl.*`, etc. were not). Fixed in 3.1.4. This is a 2026 disclosure — current and squarely in scope for a project starting now.
- **Pebble**: GitHub Security Lab advisory GHSL-2020-050 — arbitrary code execution when Pebble is used with Spring integration, because exposing Spring beans/Servlet context objects gives attackers a path around Pebble's method-call sandbox even though Pebble itself blocks direct dangerous-method calls. Pebble's own maintainers explicitly dispute "vulnerability" framing here: template input is assumed to be developer-authored code, not sanitized user input — i.e., **the vendor's official security stance is "don't hand this to untrusted authors."**
- **FreeMarker's own manual states this in writing**: "you shouldn't allow [users to upload templates] unless those users are system administrators or other trusted personnel... templates should be considered part of the source code just like *.java files."

The common thread verified across all four engines: sandboxes are denylists implemented as string/keyword heuristics layered on top of a real, much more permissive expression parser. Every documented bypass (Thymeleaf's tab-vs-space, FreeMarker's class-resolver escapes, Pebble's Spring-bean reachability) is exactly this mismatch — the filter doesn't match what the underlying parser actually accepts.

**Why it happens:**
The project's actual requirement — "release manager can author templates with placeholders that resolve against release/ticket/commit data" — sounds like it needs a full expression language (conditionals, loops, field access), which pulls teams toward a general-purpose engine and its "just enable the sandbox" configuration flag, which reads like a security control but is a best-effort heuristic with a public exploit history.

**How to avoid:**
- The release manager is a trusted internal user (single-role, per PROJECT.md), which changes the risk calculus but does not eliminate it — a compromised release-manager account or an accidentally-pasted malicious template snippet (copied from a wiki/forum) should not equal RCE on the box that holds Jira/ADO credentials and the release audit trail.
- Do not expose a Turing-complete expression language to templates at all if it can be avoided. Prefer a constrained placeholder/mustache-style substitution model (logic-less, no method invocation, no arbitrary property traversal into live objects) — e.g., a simple `{{release.version}}`, `{{ticket.summary}}` token resolver you own, or a logic-less engine (e.g. a Mustache-style implementation) rather than FreeMarker/Velocity/Pebble/Thymeleaf's full expression languages, if the template needs (loops over tickets, simple conditionals for "has ADO data") can be met with a small closed set of built-in helpers rather than a general scripting surface.
- If a full engine is genuinely required for the loop/conditional needs, resolve placeholders against a **read-only, flattened DTO projection** built specifically for templates (see Pitfall 3) and run the engine with the strictest available sandbox class list, current version, no Spring/context object exposure to the template scope, and a template size/loop-count/execution-time limit (see Pitfall 4). Treat this as defense-in-depth, not the primary control — the primary control is "the expression surface can't reach a classloader."
- Track CVEs for whichever engine is chosen for the life of the project; this is not a one-time check (Thymeleaf's most serious sandbox CVE to date is from 2026).

**Warning signs:**
- Template authoring UI allows arbitrary free-text expressions with no restriction on what's callable (`${...}`-style raw expression fields).
- Templates are stored/executed with the same process privileges that hold Jira/ADO tokens.
- No versioned allowlist of "safe" template functions exists — everything the underlying object graph exposes is reachable.

**Phase to address:** Template engine design phase — this decision (constrained substitution vs. full engine + sandbox) is foundational and expensive to reverse later; it should be settled before any template-authoring UI work begins, not discovered mid-build.

---

### Pitfall 3: Exposing the domain model directly to templates ("the maintenance trap")

**What goes wrong:**
The natural shortcut is to hand the template engine your JPA entities (Release, Ticket, Commit) or their direct DTO mirrors, because it's less code today. This is the same anti-pattern as exposing JPA entities directly in a REST API (well documented: lazy-loading exceptions, N+1 traversal from templates walking relations, serialization surprises) — except worse here, because *user-authored templates* become undocumented, unversioned consumers of your internal object graph. Once a release manager has written twenty templates referencing `ticket.fields.customfield_10047.value.name` or `release.tickets[0].adoLinks.pullRequests`, you can no longer rename a field, restructure an aggregate, add a provider, or fix a lazy-loading bug without silently breaking every template in production — with no compiler to tell you.

**Why it happens:**
Early in a greenfield build, the domain model is small and it feels wasteful to build a second "template model" for what looks like the same data. The cost only shows up after templates exist in the wild and the domain model needs to change to support the next feature (the second ticket provider, additional ADO data, environment-ladder rework).

**How to avoid:**
- Define an explicit, versioned "template context" — a flat, stable set of DTOs purpose-built for template binding, decoupled from persistence entities and from the provider abstraction's internal shapes. This is the contract with template authors; change it deliberately and treat it like a public API (deprecate fields, don't silently rename/remove).
- Never pass a JPA entity, a raw Jira API response fragment, or a live provider client object into template scope.
- Document the template context fields as the actual user-facing "template language reference" — this is also a UX requirement (see UX Pitfalls) since release managers need to know what's available to write templates against.

**Warning signs:**
- Template placeholder syntax mirrors internal JSON field names 1:1 (`fields.customfield_10047` rather than `ticket.qaSignoff`).
- A refactor of the persistence layer requires touching template-resolution code.

**Phase to address:** Template engine design phase, same phase as Pitfall 2 — the context model and the engine choice are one decision, not two.

---

### Pitfall 4: No resource limits on template execution (DoS via runaway templates)

**What goes wrong:**
User-authored templates with loops/conditionals can accidentally (or maliciously) become expensive: unbounded loops over large ticket sets nested inside loops over commits, recursive includes, or pathological string concatenation. Because this is a single-instance internal tool, a runaway template render can pin a CPU core or exhaust memory generating a release note, taking down the app for the one release manager using it — and if templates can be triggered by a scheduled or automated regeneration path, this becomes a repeatable outage.

**Why it happens:**
Template rendering is treated as a cheap, synchronous, in-request operation with no execution budget, because it "worked fine" during development with small test data.

**How to avoid:**
- Cap template execution with a wall-clock timeout and run rendering off the request thread (or at minimum with a strict timeout wrapping the call) so a bad template returns an error instead of hanging a request thread pool.
- Cap iteration counts / collection sizes the template context exposes (e.g., a release with an unexpectedly large ticket count should still render, but nested loops should be bounded or paginated in the template context itself).
- Validate/preview templates against representative data before they're saved as "the" template for a document type — catches runaway templates before they're used in anger, not after.

**Warning signs:**
- No timeout configured on template rendering calls.
- Preview/export endpoints call the template engine synchronously on the web request thread with no isolation.

**Phase to address:** Template engine design phase (limits should be part of the initial engine integration, not bolted on later).

---

### Pitfall 5: Assuming Jira's API returns everything the UI shows (permission/visibility surprises)

**What goes wrong:**
Verified against Atlassian's own documentation and community reports: the REST API enforces the same permission scheme as the UI — issue-level security levels, comment/field visibility restricted to specific groups/roles, and project "Browse" permission — but the *symptom* differs. A restricted comment or field isn't flagged as "restricted" in the API payload; it's simply absent, with no signal to the caller that something was filtered out versus never having existed. A service account with narrower permissions than an interactively-logged-in release manager will silently produce thinner release notes with no error.

**Why it happens:**
Developers test with an admin-equivalent account during development, so nothing appears filtered; the gap only appears once the integration runs under a scoped service account/API token against real project permission schemes in production.

**How to avoid:**
- Use a dedicated integration account/API token with explicit, documented project permissions, and test ticket enrichment against a ticket that has restricted comments/fields to confirm the actual (not assumed) payload shape.
- Design generated documents to be honest about gaps: if a field is empty, that's ambiguous between "field not set" and "you don't have permission to see it" — for a release-notes generator this matters, because a silently-missing QA sign-off field is a different failure than a field the reporter never filled in. Consider surfacing "connected as X — missing permissions may result in incomplete data" rather than pretending completeness.

**Warning signs:**
- Integration tested only against an admin account.
- No handling distinguishes "field absent because permission" from "field absent because empty."

**Phase to address:** Jira integration phase — provider implementation should test against a realistic, non-admin scoped token from the start.

---

### Pitfall 6: Hardcoding Jira custom field IDs

**What goes wrong:**
Jira custom field IDs (`customfield_10047`) are assigned first-come-first-served per instance and are **not portable** across Jira instances — confirmed by Atlassian community documentation on migrations: the same logical field (e.g., "QA Sign-off") will have a different `customfield_NNNNN` ID on a different Jira Cloud site, and IDs can even shift after certain migration paths on the *same* logical instance. A provider implementation that hardcodes field IDs for one client's Jira instance breaks the moment it's pointed at a second instance, and breaks silently after some migration operations.

**Why it happens:**
Field IDs are the only reliable machine key Jira exposes for most custom fields, and resolving them by human-readable name at runtime feels like unnecessary indirection during initial development against one known instance.

**How to avoid:**
- Resolve custom field IDs at connection/configuration time via the `GET /rest/api/3/field` endpoint (matching on field name), cache the resolved ID→name mapping per configured instance, and let the release manager confirm/re-map fields as part of connecting a Jira instance — don't compile field IDs into code or config that's meant to be instance-agnostic.
- Re-validate the mapping (or at least fail loudly) if a previously-resolved field ID stops resolving.

**Warning signs:**
- Custom field IDs appear as literals in provider code or committed configuration.
- No re-validation step exists for when a Jira admin renames/recreates a custom field.

**Phase to address:** Jira integration / provider abstraction phase.

---

### Pitfall 7: Building JQL query construction with naive string concatenation

**What goes wrong:**
JQL has a documented, genuinely inconsistent escaping model: Atlassian's own bug tracker has open reports acknowledging the shipped documentation on escaping special characters is wrong (JRASERVER-27647, JRASERVER-63730), and specific characters (e.g., square brackets) fail to escape correctly even when the documented escape sequence is used. A release ticket-selection UI that lets a release manager filter/search tickets (by fix version, label, free text) and turns that into JQL via string concatenation will break unpredictably the moment a ticket, label, or project name contains a reserved character (space, `.", ,`, `;`, `?`, `|`, `*`, `/`, `%`, `^`, `$`, `#`, `@`, `[`, `]`).

**Why it happens:**
JQL looks like SQL and gets treated like SQL — string-building "works" for the common demo cases (project = PROJ AND fixVersion = "1.2.3") until real project data includes something in the reserved set.

**How to avoid:**
- Prefer structured search parameters over hand-rolled JQL wherever the newer `/rest/api/3/search/jql` and field-based filtering support it; where free-text JQL is unavoidable, use a JQL-safe quoting/escaping helper library rather than ad hoc string concatenation, and test it against project names/labels containing spaces, brackets, and `@`.
- Treat "the JQL escaping edge cases are still buggy in the product itself" as a reason to keep the surface area of hand-built JQL small — the fewer free-text-driven JQL fragments you construct, the fewer escaping bugs you inherit.

**Warning signs:**
- Ticket search/filter feature breaks on projects with spaces or special characters in fix-version names or labels.

**Phase to address:** Jira ticket browsing/selection phase.

---

### Pitfall 8: Building against the deprecated Jira search endpoint and old pagination model

**What goes wrong:**
This is time-sensitive and directly relevant to a project starting now: the legacy `GET/POST /rest/api/{2,3,latest}/search` endpoint (offset-based `startAt`/`total` pagination, implicit "return every field") has been sunset through 2025 and **now returns HTTP 410 Gone on Jira Cloud**. Numerous integrations (including Atlassian's own MCP server, apache/devlake, alertmanager) hit this in production as the sunset rolled out. The replacement, `POST /rest/api/3/search/jql`, requires an explicit `fields` array (nothing is returned by default) and uses cursor-based pagination via `nextPageToken` — `startAt`/total counts are gone entirely. Separately, changelog retrieval has its own, different limitation: `expand=changelog` on an issue/search response silently truncates to roughly the 100 most recent history entries with no proper pagination, while the dedicated `GET /issue/{key}/changelog` endpoint paginates properly via `startAt`. Using the wrong one silently produces incomplete ticket history.
Also verify: general issue-search `maxResults` is capped at 100 per page regardless of what's requested (an intentional Atlassian change for server load, not a bug), except a special-cased "IDs only" query mode that can return up to 5000 — so any bulk ticket-sync logic must paginate, always.

**Why it happens:**
Older tutorials, Stack Overflow answers, and even some SDKs still reference the classic `/search` endpoint and offset pagination because that's what existed for most of Jira's API history; a project starting today can easily copy that pattern from training-data-era knowledge and ship code that 410s.

**How to avoid:**
- Build the Jira provider against `/rest/api/3/search/jql` from day one, with cursor-based pagination and an explicit fields list (which also has the side benefit of not over-fetching data you don't need for release assembly).
- Use the dedicated `/issue/{key}/changelog` endpoint (paginated via `startAt`) for any feature that needs ticket history, never `expand=changelog`.
- Treat both v2 and v3 as currently supported (v3 differs from v2 mainly in Atlassian Document Format support for rich text fields like description/comments) — pick v3 for new development to get ADF, which matters for rendering rich-text ticket fields into generated documents cleanly.

**Warning signs:**
- Any call to `/rest/api/{2,3,latest}/search` (not `/search/jql`) in code written after 2025.
- Ticket sync code assumes `startAt`/`total` exist on the search response.
- Changelog-dependent features (e.g., "when did this move to Done") return suspiciously short histories on old, heavily-transitioned tickets.

**Phase to address:** Jira integration/provider phase — foundational, must be right before anything else builds on it.

---

### Pitfall 9: Long-lived / global Azure DevOps PATs as the auth model

**What goes wrong:**
Two compounding facts, both verified against Microsoft's own docs and dev blog, make PAT-based ADO auth a poor long-term default for a system that's supposed to run unattended: (1) PATs expire (max 1 year, and organizations increasingly enforce much shorter policy maximums — 30/60/90 days is a common org policy), so an integration built assuming "set it once" will go dark on an unannounced schedule unless expiry is tracked and the operator is warned in advance; (2) **Microsoft is retiring "global" (cross-organization) PATs on 1 December 2026** — org-scoped PATs are becoming mandatory, and Microsoft's own recommended replacement is Microsoft Entra-backed service-principal/OAuth credentials, not PATs at all, for anything beyond quick manual scripts. Since this project starts in mid-2026, the PAT retirement date falls within the likely build/early-operation window.

**Why it happens:**
A PAT is the fastest way to get an ADO integration working in a spike, and that expedience path quietly becomes the production auth mechanism if no one revisits it.

**How to avoid:**
- Design the ADO provider's credential model to be swappable from day one (this is consistent with the project's existing "provider abstraction" instinct, just applied to auth, not the ticket source): treat the PAT as one supported credential type, but don't hardcode assumptions (single global token, no expiry) into the domain model.
- Track PAT expiry explicitly and surface an in-app warning to the release manager well before expiry — this is the same "unattended integration silently going dark" risk as the general credential-expiry problem, but with a hard-known cutoff for the "global PAT" flavor.
- Given the org-scoping requirement either way, store credentials per-ADO-organization explicitly rather than assuming one token reaches every project a Jira ticket might link to.

**Warning signs:**
- A single PAT configured once at setup with no expiry tracking or renewal prompt.
- Assuming one ADO credential works across multiple ADO organizations.

**Phase to address:** Azure DevOps enrichment phase.

---

### Pitfall 10: Scope creep from "tracker" into "deployment executor"

**What goes wrong:**
PROJECT.md is explicit and correct that this tool records deployments and never triggers them — but this is exactly the boundary that internal release/deployment tools drift across over time, because the next "obviously useful" feature is always one CI/CD API call away once the tool already models environments, promotions, and audit history ("since we already know a release is ready for PROD, why not just trigger the pipeline from here"). Once that line is crossed, the tool inherits deployment credentials, CI/CD-specific failure modes, and a much larger security/operational surface — the opposite of the scoping decision that made this a safe internal tool to begin with.

**Why it happens:**
The tool ends up sitting at exactly the point in the workflow (post-approval, pre-deploy) where triggering the deploy looks like the natural next click, and stakeholders will ask for it because it's convenient, not because it was ever in scope.

**How to avoid:**
- Keep "record deployment state" and "read from CI/CD" as two clearly separate, currently-out-of-scope capabilities (already reflected correctly in PROJECT.md's Out of Scope list) and treat any future request to add either as a deliberate, re-scoped decision — not an incremental addition to the existing promotion feature.
- Architecturally, don't give the deployment-tracking module a write path to any CI/CD system's credentials at all in v1; there should be no code path where adding "trigger" is a one-line change from "record."

**Warning signs:**
- A feature request phrased as "just add a button to also kick off the pipeline while we're at it."
- Deployment-tracking code starts importing a CI/CD SDK "just to check status" (this is also explicitly out of scope — ingesting build/pipeline run status — for the same reason).

**Phase to address:** Deployment/promotion tracking phase — enforce the boundary in the initial design, and flag it explicitly at any later milestone-boundary review (PROJECT.md's own evolution process is the right place to catch scope drift here).

---

### Pitfall 11: The provider abstraction is either premature or retrofitted too late

**What goes wrong:**
PROJECT.md already commits to a provider abstraction for ticket sources in v1 with only Jira implemented, which is the right call per its own stated rationale (retrofitting is more expensive than defining it up front) — but this is a two-sided trap:
- **Over-abstraction now**: building a fully generic, provider-agnostic interface before Jira is even fully understood tends to produce an abstraction shaped by guesswork about "what a second provider might need," which usually doesn't match what a real second provider (Azure Boards, GitHub Issues) actually needs, forcing a rework anyway when provider #2 arrives — just with extra layers to unwind first.
- **Leaking Jira specifics through the abstraction**: JQL-shaped query objects, Jira-specific field/changelog semantics, or Jira's ADF rich-text format leaking into the "generic" provider interface, so that "provider abstraction" in practice means "Jira interface with a second implementation nobody can actually write."

**Why it happens:**
With only one real implementation to design against, it's easy to either over-generalize speculatively or under-generalize by accident (modeling the interface as "whatever Jira's client library naturally returns").

**How to avoid:**
- Design the provider interface around the *domain concepts this tool actually needs* (ticket, ticket status, ticket fields-as-a-flat-map, ticket history, code links) rather than around Jira's API shape — the same "flat template context" discipline from Pitfall 3 applies here: don't let ADF or JQL leak past the Jira implementation.
- Keep the interface small — resist adding methods/parameters for hypothetical provider #2 capabilities not needed by Jira today; a narrow interface is easier to widen later than a wide one is to narrow.
- Validate the abstraction isn't Jira-shaped by writing (even if not shipping) a rough sketch of what a GitHub Issues or Azure Boards implementation would look like against the same interface, without necessarily building it.

**Warning signs:**
- Provider interface method signatures accept/return Jira-specific types (ADF documents, JQL strings, `customfield_NNNNN` maps) rather than domain types.
- No one can describe in one sentence what provider #2 would need to implement without reading Jira-specific code first.

**Phase to address:** Provider abstraction / Jira integration phase — the interface boundary should be reviewed explicitly once Jira's implementation is drafted, before being considered "done."

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Hardcoding Jira custom field IDs for the first client instance | Faster initial integration | Breaks on every new instance; silent breakage after migrations | Never past a throwaway spike |
| Passing JPA entities / raw Jira JSON into template scope | Skips building a template DTO layer | Templates become unversioned consumers of internal structure; blocks refactors | Never |
| Using a full-expression-language template engine's "sandbox" as the only control on user templates | Fastest path to a flexible template language | Inherits every sandbox-bypass CVE in that engine's history, indefinitely | Only if templates are never user-authored |
| Treating dev-status as the sole ADO-link mechanism without a fallback | Less code to reach MVP | Silent, total feature loss on any Jira instance without the ADO connector app installed | Only after the verification spike confirms dev-status is populated for ADO on the target instance(s) |
| One shared ADO PAT for all environments/orgs | Simple config | Breaks the moment two ADO orgs are in play; expires unpredictably; blocked by Dec 2026 global-PAT retirement | Acceptable only for a single-org proof of concept, never production |
| String-concatenated JQL from UI filter inputs | Quick to build a ticket-picker | Breaks on any reserved character in real project/label data | Never past a demo with sanitized test data |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|-----------------|-------------------|
| Jira REST API | Calling the legacy `/rest/api/{2,3}/search` endpoint (offset pagination) | Use `/rest/api/3/search/jql` with cursor-based `nextPageToken` and explicit `fields` |
| Jira REST API | Using `expand=changelog` and assuming it's complete | Use dedicated `GET /issue/{key}/changelog` with `startAt` pagination |
| Jira REST API | Assuming a 200 response means complete data | Check for permission-filtered gaps (issue security level, restricted comments) — absence isn't an error |
| Jira REST API | Hardcoding `customfield_NNNNN` IDs | Resolve via `GET /rest/api/3/field` by name at connection time, cache per instance |
| Jira REST API | Building JQL via string concatenation | Prefer structured search params; if JQL is unavoidable, use a tested escaping helper |
| Jira REST API | Ignoring 429 / assuming unlimited throughput | Respect `Retry-After`, implement exponential backoff + jitter, design bulk sync to stay under the documented points-based hourly quota and per-second burst limits |
| Azure DevOps (link discovery) | Assuming Jira's dev-status API always carries ADO data | Verify the ADO-Jira connector app is installed on the target instance; build a commit/PR-message key-parsing fallback that queries ADO directly |
| Azure DevOps (auth) | One long-lived/global PAT with no expiry tracking | Org-scoped PAT (or Entra service principal) per ADO organization, with expiry tracked and surfaced before the Dec 2026 global-PAT retirement and any org PAT-lifetime policy |
| Azure DevOps (API) | No backoff on 429 (`TF400733`) | Exponential backoff; keep bulk/bursty calls (e.g., large PR/commit fetches per release) below documented resource-consumption thresholds |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Fetching full changelog per ticket via `expand=changelog` for every ticket in a large release | Slow release assembly, silently truncated history | Fetch changelog lazily/on-demand via dedicated endpoint, only for tickets actually needing history in a document | Releases with tickets that have >100 changelog entries (long-lived tickets) |
| Unbounded template loops over all tickets/commits in a release | Slow or hung document generation | Bound iteration in the template context; render with a timeout | Releases with large ticket counts or tickets with many commits/PRs |
| N+1 dev-status calls (one per ticket, sequential, per release) | Release assembly with ADO enrichment feels sluggish | Batch/parallelize with respect for both Jira and ADO rate limits; cache dev-status results per ticket for the session | Releases with dozens+ of tickets |
| Re-rendering documents from scratch on every preview keystroke | UI feels laggy during template authoring | Debounce preview renders; render against cached/sample data during authoring, not live API calls each time | Any live-preview template editor |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Letting the release manager write raw expression-language template code (`${...}`, SpEL, OGNL-equivalent) with no restriction | RCE via a documented sandbox-bypass class (Thymeleaf CVE-2026-40478, FreeMarker CVE-2021-25770, Pebble GHSL-2020-050 style Spring-bean reachability) | Constrained placeholder/logic-less substitution model; if a full engine is used, no Spring bean/context exposure to template scope, strict class denylist, current patched version, defense-in-depth only |
| Storing Jira/ADO credentials without per-instance/per-org scoping | One compromised credential reaches more systems than intended | Scope credentials per connected Jira instance / per ADO organization explicitly |
| No expiry monitoring on ADO PATs | Integration silently stops working (or is a hard requirement post-Dec-2026 global PAT retirement) | Track and surface PAT/token expiry in-app before it lapses |
| Serving the React SPA and API from Spring Boot without a strict CORS/cookie config, or using `SameSite=None` cookies without also requiring `Secure`+`HttpOnly` | Session/CSRF exposure | Scope CORS to the actual SPA origin (never `*` with credentials), `HttpOnly`+`Secure` session cookies, CSRF protection appropriate to the auth model chosen |
| Treating exported documents (release notes/runbooks) as never containing sensitive data | Restricted-field data (e.g., security-level-protected ticket fields the exporting user *does* have access to) ends up in a widely-shared document | Be deliberate about what ticket data flows into exported documents, independent of what the authenticated user can see live in the app |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| Template authoring with no documented list of available fields/placeholders | Release manager guesses at placeholder names via trial and error | Ship a live, browsable "template context reference" (generated from the same DTO model in Pitfall 3) alongside the editor |
| Generated release notes/runbooks silently omitting tickets with no ADO link, with no visual distinction from tickets that simply have no data yet | Looks like the enrichment failed rather than "this ticket legitimately has no linked code" | Explicitly render "no linked commits/PRs" as a normal, expected state in both the UI and generated documents, not an absence |
| Documents generated from inconsistent ticket data (missing fix version, no assignee, empty description) render with awkward blank sections or raw `null` | Documents look broken/unprofessional, undermining trust in the tool's core value proposition | Templates should have first-class handling for missing/empty fields (fallback text, conditional sections) as a core template-language feature, not an afterthought |
| No preview before export | Release manager discovers formatting/data problems only after sharing a document with stakeholders | Preview is a stated requirement already (PROJECT.md) — make sure preview renders against the *actual* release's real data, not a static mock, so surprises surface before export |

## "Looks Done But Isn't" Checklist

- [ ] **Jira ticket browsing/search:** Often missing pagination beyond the first 100 results and correct handling of reserved JQL characters in real (non-demo) project/label names — verify against a project with spaces/brackets in labels or fix-version names.
- [ ] **ADO enrichment:** Often "done" only against a Jira Cloud sandbox with the Bitbucket/GitHub app pre-wired — verify dev-status actually returns ADO data on the real target Jira instance, and that the fallback (commit/PR key parsing) is exercised, not just written.
- [ ] **Template engine:** Often "done" as soon as one template renders correctly — verify it rejects/limits pathological templates (deep loops, huge datasets) and doesn't expose Spring beans/servlet context/reflection to template scope.
- [ ] **Release notes for a real release:** Often tested only against tickets with complete, tidy data — verify against a release containing at least one ticket with no ADO link, missing fields, and a restricted/permission-filtered field.
- [ ] **ADO auth:** Often "done" with one PAT pasted into config — verify expiry is tracked/surfaced and the credential model isn't assuming a single global token across orgs.
- [ ] **SPA + Spring Boot serving:** Often "done" for the happy path (`/`) but missing a catch-all route forward so React Router deep links (e.g., a shared link to a specific release) don't 404 on refresh.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|----------------|------------------|
| Dev-status found empty for ADO on the real instance after initial build | MEDIUM | Add the commit/PR-message key-parsing fallback against ADO's own API; doesn't require reworking the Jira provider, only the ADO enrichment step |
| Hardcoded custom field IDs discovered when a second instance is connected | LOW–MEDIUM | Introduce the `/rest/api/3/field` name-resolution + mapping cache; existing single-instance behavior is unaffected once mapping is backfilled |
| Template engine found to expose too much (Spring beans, raw entities) after templates already exist | HIGH | Requires migrating existing user templates to a new constrained context — budget for template-syntax migration tooling/communication, not just a code change |
| Legacy `/rest/api/*/search` endpoint hit in production (410 Gone) | LOW–MEDIUM | Swap to `/search/jql` with explicit fields + cursor pagination; isolated to the Jira provider's search method if the provider abstraction (Pitfall 11) was kept clean |
| Global ADO PAT stops working at the Dec 2026 retirement | LOW if anticipated, HIGH if not | Move to org-scoped PATs or Entra service-principal auth; trivial if credential model was already abstracted (Pitfall 9), a full rewrite of ADO auth if not |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| Dev-status as sole ADO discovery mechanism | ADO enrichment phase (preceded by a discovery spike) | Confirm ADO data appears for a real ticket on the real Jira instance before committing to the mechanism; fallback path exists and is tested |
| SSTI / template engine sandbox reliance | Template engine design phase | Security review of the chosen engine/context model before template-authoring UI is built; confirm no Spring bean/reflection exposure |
| Domain model leaking into templates | Template engine design phase | Template context DTOs reviewed independently of persistence entities; a persistence refactor should not touch template resolution |
| Runaway template DoS | Template engine design phase | Load-test a template with intentionally large/nested data; confirm timeout/bound behavior |
| Permission-filtered Jira data treated as complete | Jira integration phase | Integration tested against a non-admin scoped token and a ticket with restricted fields/comments |
| Hardcoded custom field IDs | Jira integration / provider phase | Connect a second (test) Jira instance and confirm field mapping resolves correctly without code changes |
| Fragile JQL string-building | Ticket browsing/selection phase | Test ticket search against project/label data containing reserved JQL characters |
| Deprecated search endpoint / changelog truncation | Jira integration phase (foundational) | Code review confirms `/search/jql` and dedicated `/changelog` endpoint usage, not legacy `/search` or `expand=changelog` |
| ADO PAT lifecycle | ADO enrichment phase | Credential model supports org-scoped tokens with tracked expiry; confirm it isn't a single unscoped global PAT |
| Scope creep into deployment execution | Deployment/promotion tracking phase, and every milestone review after | No CI/CD write credentials or trigger code paths exist in the deployment-tracking module |
| Provider abstraction premature/retrofitted | Provider abstraction / Jira integration phase | Interface reviewed for Jira-shaped leakage (ADF, JQL, field IDs) after the Jira implementation is drafted, before considered final |

## Sources

- [Jira Cloud rate limiting](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/) — official docs, points-based quota, burst limits, `Retry-After`/`X-RateLimit-*` headers
- [Access Git Repo Commit Data via Jira REST API in Development Panel (Data Center) — Atlassian Support](https://support.atlassian.com/jira/kb/access-git-repo-commit-data-via-jira-rest-api-in-development-panel-data/)
- [REST API endpoint provides only a part of a Development info — JSWCLOUD-16901](https://jira.atlassian.com/browse/JSWCLOUD-16901)
- [REST API access to information in Development Panel — JSWSERVER-15768](https://jira.atlassian.com/browse/JSWSERVER-15768)
- [Azure DevOps Integration for Jira (Azure DevOps Connector) — Atlassian Marketplace](https://marketplace.atlassian.com/apps/1223931/azure-devops-integration-for-jira-azure-devops-connector)
- [Process work items with smart commits — Jira Cloud, Atlassian Support](https://support.atlassian.com/jira-software-cloud/docs/process-issues-with-smart-commits/)
- [Reference work items in your development spaces — Jira Cloud, Atlassian Support](https://support.atlassian.com/jira-software-cloud/docs/reference-issues-in-your-development-work/)
- [Atlassian changelog CHANGE-2046 — search endpoint deprecation/migration to `/search/jql`](https://developer.atlassian.com/changelog/#CHANGE-2046)
- [How to use the maxResults API parameter for Jira Issue Search REST API — Atlassian Support](https://support.atlassian.com/jira/kb/how-to-use-the-maxresults-api-parameter-for-jira-issue-search-rest-api/)
- [Handling JQL REST API searching exceeding max_result_window — Atlassian Support](https://support.atlassian.com/jira/kb/handling-jql-rest-api-searching-exceeding-max_result_window-in-jira/)
- [How does changelog pagination work? — Atlassian Community](https://community.atlassian.com/forums/Jira-questions/How-does-changelog-pagination-work/qaq-p/1120774)
- [Custom field ID while migrating Jira project or instance? — Atlassian Community](https://community.atlassian.com/forums/Jira-questions/Custom-field-ID-while-migrating-Jira-project-or-instance/qaq-p/1441762)
- [Changing Field IDs in Jira: A Cautionary Tale — The Jira Guy](https://thejiraguy.com/2022/01/12/changing-field-ids-in-jira-a-cautionary-tale/)
- [Documentation Update for JQL Search with Special Characters — JRASERVER-63730](https://jira.atlassian.com/browse/JRASERVER-63730)
- [Please Fix documentation regarding escaping special characters — JRASERVER-27647](https://jira.atlassian.com/browse/JRASERVER-27647)
- [Requesting comment permissions via REST API — Atlassian Community](https://community.atlassian.com/forums/Jira-questions/Requesting-comment-permissions-via-REST-API/qaq-p/1099087)
- [It's About Thyme: How a Whitespace Character Broke Thymeleaf's Expression Sandbox (CVE-2026-40478) — Endor Labs](https://www.endorlabs.com/learn/its-about-thyme-how-a-whitespace-character-broke-thymeleafs-expression-sandbox-cve-2026-40478)
- [Don't Panic: The Thymeleaf Template Injection That Only Hurts If You Let It (CVE-2026-40478) — NLJUG](https://nljug.org/foojay/dont-panic-the-thymeleaf-template-injection-that-only-hurts-if-you-let-it-cve-2026-40478/)
- [The Thymeleaf Template Injection That Only Hurts If You Let It — Snyk](https://snyk.io/blog/thymeleaf-injection/)
- [Critical sandbox bypass fixed in popular Thymeleaf Java template engine — CSO Online](https://www.csoonline.com/article/4160520/critical-sandbox-bypass-fixed-in-popular-thymeleaf-java-template-engine.html)
- [Freemarker < 2.3.30 Sandbox Bypass — Server Side Template Injection (CVE-2021-25770) — pentest-tools.com](https://pentest-tools.com/vulnerabilities-exploits/freemarker-2330-sandbox-bypass-server-side-template-injection_26564)
- [GHSL-2020-050: Arbitrary code execution in Pebble Templates — GitHub Security Lab](https://securitylab.github.com/advisories/GHSL-2020-050-pebble/)
- [Server-Side Template Injection (Java Pebble) — Invicti](https://www.invicti.com/web-vulnerability-scanner/vulnerabilities/server-side-template-injection-java-pebble)
- [Server Side Template Injection — Java, PayloadsAllTheThings](https://github.com/swisskyrepo/PayloadsAllTheThings/blob/master/Server%20Side%20Template%20Injection/Java.md)
- [Web API Design Anti-Pattern: Exposing your database model — Shekhar Gulati](https://shekhargulati.com/2021/10/15/web-api-design-anti-pattern-exposing-your-database-model/)
- [Don't expose your JPA entities in your REST API — Thorben Janssen](https://thorben-janssen.com/dont-expose-entities-in-api/)
- [Use personal access tokens — Azure DevOps, Microsoft Learn](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops)
- [Manage personal access tokens using policies — Azure DevOps, Microsoft Learn](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/manage-pats-with-policies-for-administrators?view=azure-devops)
- [Retirement of Global Personal Access Tokens in Azure DevOps — Azure DevOps Blog](https://devblogs.microsoft.com/devops/retirement-of-global-personal-access-tokens-in-azure-devops/)
- [Rate and usage limits — Azure DevOps, Microsoft Learn](https://learn.microsoft.com/en-us/azure/devops/integrate/concepts/rate-limits?view=azure-devops)
- General Spring Boot + React SPA CORS/session/static-serving practices — cross-referenced across multiple current community sources (MEDIUM confidence, standard/uncontested pattern)

---
*Pitfalls research for: Release management tool (Spring Boot + React, Jira + Azure DevOps integration, user-authored template engine)*
*Researched: 2026-07-27*
