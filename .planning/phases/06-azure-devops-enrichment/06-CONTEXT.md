# Phase 6: Azure DevOps Enrichment - Context

**Gathered:** 2026-10-07
**Status:** Ready for planning, **except** the data-source details blocked on plan 02-04 (see below)
**Source:** `/gsd-discuss-phase 8` session that pulled Phase 6 in at the user's request, because Phase 6
data shapes the Phase 8 dashboard. Backed by two advisor-research reports (enrichment timing, credential
model) and a codebase assumptions scan, all run 2026-10-07.

<domain>
## Phase Boundary

**In scope:**
- Per Jira-backed release item:
  - discover Azure DevOps links via Jira `dev-status` (D-02-05) and fetch commit/PR detail from ADO;
  - **store** the result with a status;
  - show it on the release page;
  - feed it to templates.
- One org-scoped ADO credential with a tracked, user-entered expiry and a pre-expiry warning (ADO-04).
- Bounded, parallel, de-duplicated lookups (ADO-05).
- **Data capture for INSIGHT-03** (Phase 8 computes and displays it, this phase stores it):
  - every linked commit's author date;
  - the ticket's status-change history from a scoped `/changelog` read;
  - the ticket's caused-by issue links.

**Out of scope:**
- Any dashboard aggregation (Phase 8).
- Pipeline/build status (PROJECT.md out of scope).
- Searching ADO independently of Jira links.
- Per-file commit changes.
- Agile reports.

**Blocked on plan 02-04 (human with a live Jira token must capture `CNV-2896`'s dev-status response):**
- the working `applicationType` / `dataType` values;
- whether commits and PRs arrive in one call;
- whether dev-status alone already carries commit message, author, date and PR status;
- how the ADO org/project/repo appear;
- the GO / PARTIAL / NO-GO verdict.

**Phase 6 research/planning must not start until 02-04 has run.** Everything else here is designable
now.
</domain>

<decisions>
## Implementation Decisions

### D-06-01: Enrichment is stored per item, refreshed, and frozen with the release
Results are materialised on `release_ticket_item` (or a child table) exactly like D-03-06/D-03-07 ticket
fields:
- written on add-ticket, on "Refresh from Jira", and by a separate **"Refresh code links"** action;
- never changed after freeze (the existing FROZEN guard covers it).

Reading a release, previewing, generating, and the dashboard never call Jira or ADO.

Rejected:
- live-on-view: breaks Phase 8's "own database only" rule and frozen-release stability;
- TTL background refresh: no user Jira credential exists in the background (AUTH-02);
- enrich-at-generation: the dashboard and release page would have no data.

### D-06-02: Per-item status values
- `NOT_ATTEMPTED`: existing rows, no backfill.
- `NO_LINK`: dev-status succeeded with zero links.
- `LINKED`: links stored.
- `FAILED`: with a machine-readable reason.
- Also stored: `enrichment_attempted_at`.

Manual items carry no enrichment state (they are outside INSIGHT-02's denominator).

Failure reasons are their own enum, not an extension of `JiraAuthFailureReason`, e.g. `DEV_STATUS_ERROR`,
`DEV_STATUS_UNRECOGNISED` (schema drift), `JIRA_RATE_LIMITED`, `ADO_CREDENTIAL_MISSING`,
`ADO_CREDENTIAL_EXPIRED`, `ADO_UNAUTHORIZED`, `ADO_NOT_FOUND`, `ADO_RATE_LIMITED`, `TIMEOUT`. The exact set
is the planner's call.

### D-06-03: Partial success counts as LINKED, flagged incomplete
If dev-status finds links but some ADO detail lookups fail:
- the ticket is `LINKED` with an `incomplete` flag, because traceability exists;
- the release page marks the missing detail;
- the next refresh retries it;
- Phase 8 counts it as linked.

### D-06-04: "Refresh from Jira" also enriches, and ADO never blocks it
One refresh updates tickets and code links. ADO or dev-status failures mark the affected items `FAILED`,
and the Jira refresh still succeeds. Enrichment must not reuse refresh's all-or-nothing
`ReleaseRefreshIncompleteException` path, because that would violate ADO-02.

The standalone "Refresh code links" action stamps `items_changed_at` **only when a stored result
actually changed**, so a no-op refresh never marks documents STALE.

### D-06-05: Freeze warns and offers a final enrichment
If any Jira-backed item is `FAILED` or `NOT_ATTEMPTED` at freeze time:
- the freeze dialog states the count;
- it offers "Refresh code links first";
- it still allows freezing anyway.

Freezing is never blocked by ADO state.

### D-06-06: Release page display
A compact per-ticket chip ("3 commits · 1 PR") expands to a list:
- commit: short sha, message, author, date, link;
- PR: title, status, link.

"No link" and "Couldn't fetch (reason)" are visually distinct (ADO-03), and the latter offers retry.

### D-06-07: Documents omit failed enrichment silently
The existing `CommitView` / `PullRequestView` / `ItemView` lists and the COMMIT / PULL_REQUEST
placeholder scopes (`PlaceholderCatalog.java:70-111`, currently "Always empty until Phase 6") get
populated:
- `ReleaseViewAssembler` stops hard-coding `List.of()`;
- `SampleReleaseView` gets sample data;
- the catalog wording loses "until Phase 6".

A `FAILED` item renders the same as `NO_LINK`: empty sections render nothing (ADO-02). No failure flag is
exposed to templates.

### D-06-08: Data captured for INSIGHT-03 (Phase 8 consumes it)
- **Commit dates.** Each stored commit keeps its author date. Commit data never changes for a sha, so it
  is reusable across runs; PR status is re-fetched.
- **Status history.**
  - Read the paginated `/changelog` only:
    - for tickets in a release;
    - on refresh;
    - when the ticket's Jira `updated` time differs from the stored one.
  - Keep only status-field changes, with from/to status and their **status category**.
  - Store `first_done_at` (first transition into status category Done) and the full transition list, or
    at minimum the reopen intervals, so Phase 8 can count reopens and classify commits.
  - Done means **Jira status category Done**, not status names.
  - This is the scoped `/changelog` reversal recorded in `PROJECT.md` (2026-10-07).
- **Caused-by bugs.** Store the ticket's Jira issue links (standard `issuelinks` field, already
  available from the search call), keeping link type name/direction and the linked issue key/type/status.
  Which link type(s) mean "caused by" is a **planning question**. Check this tenant's configured link
  types (needs a live token, which can be done alongside 02-04) rather than guessing.
- History reads follow the same failure rule as ADO: a `/changelog` failure marks history unavailable
  for that item and never fails the refresh.

### Claude's Discretion (recorded from research, not chosen by the user; flag if you disagree)
- **Credential model: one shared org-scoped PAT** (`vso.code`, Code read), set in Settings, stored in a
  singleton row reusing `EncryptedTokenConverter` and the JPQL "upsert without decrypting" rule
  (locked decision). The user enters the expiry date, because no API lets a PAT read its own `validTo`. It
  is validated on save with one cheap `vso.code` call.
  - **Settings help text must say "select one organization"**: global PATs can still be created until
    1 Dec 2026 and would then stop working.
  - A 401 at call time marks items `FAILED / ADO_UNAUTHORIZED` (or `ADO_CREDENTIAL_EXPIRED` when past
    the stored date).
  - A service principal stays the fallback if the org restricts PAT creation; the storage shape carries
    over.
- **Expiry warning:** 14 days before the stored expiry, as an app-wide banner (reusing the
  `credentialStatus.ts` pattern), plus a Settings indicator. Checked on read, with no scheduler.
- **Who may set it:** any signed-in user, since no role model exists. Record `updated_by`.
- **If dev-status alone carries enough detail** (decided by 02-04), the ADO PAT becomes an optional
  enhancement rather than a requirement. Planning re-checks this after the spike.
- **Concurrency:** a small fixed pool (single digits) on virtual threads with a semaphore, an overall
  deadline (pending items become `FAILED / TIMEOUT`, partial results are returned), one honoured
  `Retry-After` on 429 per ticket, and de-duplication by PR id and commit sha within a run. No cache
  library: the persisted rows are the cache (ADO-05).
- **Ports:** a new dev-status port (Jira adapter under `adapters/jira/devstatus/`) and an ADO port
  (`adapters/ado/`) with its own `RestClient` bean mirroring `JiraRestClientConfig`. Not methods on
  `JiraTicketPort`.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md`: Phase 6, Phase 7 and Phase 8 sections.
- `.planning/REQUIREMENTS.md`: ADO-01..05, INSIGHT-02, INSIGHT-03.
- `.planning/PROJECT.md`: Out of Scope, including the 2026-10-07 scoped `/changelog` reversal.
- `.planning/phases/02-jira-ticket-browsing/02-CONTEXT.md` (D-02-05), `02-04-PLAN.md` (the blocking
  spike), and `02-RESEARCH.md` Pitfall 5 (numeric issue id needed for dev-status).
- `.planning/phases/03-release-assembly/03-CONTEXT.md`: D-03-06, D-03-07, D-03-10.
- `.planning/phases/08-release-insights-dashboard-the-cross-release-views-jira-stru/08-CONTEXT.md`:
  the consumer of this phase's data.
- `src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java:70-111`,
  `domain/template/view/CommitView.java`, `PullRequestView.java`, `ItemView.java`,
  `application/ReleaseViewAssembler.java`.
- `src/main/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverter.java`,
  `JiraCredentialRepositoryAdapter.java` (the JPQL non-decrypting upsert), and
  `JiraSiteConfigEntity` (the singleton-row pattern).
- `src/main/java/com/evision/releasemgmt/config/JiraRestClientConfig.java`,
  `adapters/jira/JiraRestTicketAdapter.java:78-92` (error cascade, no retry on 429).
- `src/main/java/com/evision/releasemgmt/application/ReleaseService.java:99-113` (refresh) and
  `adapters/persistence/ReleaseRepositoryAdapter.java:109-119` (timestamp stamping).
- External: [Retirement of global PATs](https://devblogs.microsoft.com/devops/retirement-of-global-personal-access-tokens-in-azure-devops),
  [Use PATs](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate),
  [ADO rate limits](https://learn.microsoft.com/en-us/azure/devops/integrate/concepts/rate-limits),
  [Jira Cloud rate limiting](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/).
</canonical_refs>

<specifics>
## Specific Ideas

- The ADO rate budget is **one shared identity** (200 TSTU per sliding 5 minutes) across all users of
  this tool, while the Jira budget is per user.
- `release_ticket_item` stores no numeric Jira issue id today (`V3__release_assembly.sql:20-36`). dev-status
  needs one, so add the column during refresh or resolve it then.
- Contract tests for dev-status are built from the redacted 02-04 capture, kept verbatim (D-02-05).
</specifics>

<deferred>
## Deferred Ideas

- Files-touched overlap as a bug-tracing signal: offered 2026-10-07, not chosen (it needs a per-commit
  changes call and is a noisy proxy).
- Showing PR review findings (comment threads, rejected iterations): offered 2026-10-07, not selected.
- Entra service principal / delegated OAuth: kept as the fallback if the org restricts PATs. Delegated
  OAuth would break the SameSite=Strict CSRF control (locked decision).
</deferred>

---

*Phase: 06-azure-devops-enrichment*
*Context gathered: 2026-10-07 (discussed alongside Phase 8)*
