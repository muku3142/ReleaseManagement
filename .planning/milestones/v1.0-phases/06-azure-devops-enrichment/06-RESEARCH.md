# Phase 6: Azure DevOps Enrichment - Research

**Researched:** 2026-10-08
**Domain:** Jira dev-status discovery + optional Azure DevOps REST 7.1 enrichment, Jira `/changelog` status history, per-item persistence, bounded fan-out
**Confidence:** MEDIUM-HIGH (ADO and Jira endpoint shapes verified against official docs; A1, A3, A4 and A9 locked by the 2026-10-12 real-tenant capture, see "Captured Fixture Findings")

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
(Copied from `06-CONTEXT.md` `## Implementation Decisions`; the 2026-10-07 02-04 findings block governs D-06-09.)

- **D-06-01: Enrichment is stored per item, refreshed, and frozen with the release.** Results are materialised on `release_ticket_item` (or a child table) exactly like D-03-06/D-03-07 ticket fields: written on add-ticket, on "Refresh from Jira", and by a separate **"Refresh code links"** action; never changed after freeze (the existing FROZEN guard covers it). Reading a release, previewing, generating, and the dashboard never call Jira or ADO. Rejected: live-on-view, TTL background refresh (no user Jira credential in the background, AUTH-02), enrich-at-generation.
- **D-06-02: Per-item status values.** `NOT_ATTEMPTED` (existing rows, no backfill), `NO_LINK` (dev-status succeeded with zero links), `LINKED` (links stored), `FAILED` (with a machine-readable reason). Also stored: `enrichment_attempted_at`. Manual items carry no enrichment state. Failure reasons are their own enum, not an extension of `JiraAuthFailureReason` (e.g. `DEV_STATUS_ERROR`, `DEV_STATUS_UNRECOGNISED`, `JIRA_RATE_LIMITED`, `ADO_CREDENTIAL_MISSING`, `ADO_CREDENTIAL_EXPIRED`, `ADO_UNAUTHORIZED`, `ADO_NOT_FOUND`, `ADO_RATE_LIMITED`, `TIMEOUT`). The exact set is the planner's call.
- **D-06-03: Partial success counts as LINKED, flagged incomplete.** If dev-status finds links but some ADO detail lookups fail: `LINKED` with an `incomplete` flag; the release page marks the missing detail; the next refresh retries; Phase 8 counts it as linked.
- **D-06-04: "Refresh from Jira" also enriches, and ADO never blocks it.** ADO or dev-status failures mark the affected items `FAILED`, and the Jira refresh still succeeds. Enrichment must not reuse refresh's all-or-nothing `ReleaseRefreshIncompleteException` path. The standalone "Refresh code links" action stamps `items_changed_at` **only when a stored result actually changed**.
- **D-06-05: Freeze warns and offers a final enrichment.** If any Jira-backed item is `FAILED` or `NOT_ATTEMPTED` at freeze: the dialog states the count, offers "Refresh code links first", and still allows freezing. Freezing is never blocked by ADO state.
- **D-06-06: Release page display.** Compact per-ticket chip ("3 commits · 1 PR") expanding to a list: commit = short sha, message, author, date, link; PR = title, status, link. "No link" and "Couldn't fetch (reason)" visually distinct (ADO-03); the latter offers retry.
- **D-06-07: Documents omit failed enrichment silently.** Populate the existing `CommitView` / `PullRequestView` / `ItemView` lists and the COMMIT / PULL_REQUEST scopes (`PlaceholderCatalog.java:70-111`, currently "Always empty until Phase 6"); `ReleaseViewAssembler` stops hard-coding `List.of()`; `SampleReleaseView` gets sample data; catalog wording loses "until Phase 6". A `FAILED` item renders the same as `NO_LINK`: empty sections render nothing (ADO-02). No failure flag is exposed to templates.
- **D-06-08: Data captured for INSIGHT-03 (Phase 8 consumes it).** Each stored commit keeps its author date (commit data never changes for a sha so is reusable across runs; PR status is re-fetched). Status history: read the paginated `/changelog` only for tickets in a release, on refresh, when the ticket's Jira `updated` differs from the stored one; keep only status-field changes with from/to status and their **status category**; store `first_done_at` and the full transition list (or at minimum reopen intervals); Done = Jira **status category** Done, not names. A `/changelog` failure marks history unavailable for that item and never fails the refresh. **Caused-by bugs: dropped (2026-10-07)** - issue links are NOT captured.
- **D-06-09 (user, 2026-10-07): the ADO PAT is optional; enrichment is Jira-first.** Everything (branches, PRs, key-tagged commits, ADO-01/03, INSIGHT-02, document placeholders) works from Jira dev-status alone. When an org PAT is configured, complete per-ticket commit lists and dates come from ADO's PR commits API, for the ticket's own PRs only. Without a PAT, INSIGHT-03 commit counts are **key-tagged commits only**, and every display of them is labelled that way. Each stored commit list records which source it came from. A PAT that is missing, expired or rejected never fails enrichment: it falls back to the Jira-only data.
- **Environment-merge PRs are excluded from commit counts**; count commits only from PRs whose source branch contains the ticket key as a whole token (optionally after a prefix such as `feature/`). Branch rule confirmed by the user.
- **Credential confirmed:** the user can create an org-scoped Code (Read) PAT, so the shared org PAT stands.

### Claude's Discretion
- **Credential model: one shared org-scoped PAT** (`vso.code`), set in Settings, stored in a singleton row reusing `EncryptedTokenConverter` and the JPQL "upsert without decrypting" rule. The user enters the expiry date (no API lets a PAT read its own `validTo`). Validated on save with one cheap `vso.code` call. Settings help text must say "select one organization" (global PATs can be created until 1 Dec 2026 and then stop working). A 401 at call time marks items `ADO_UNAUTHORIZED` (or `ADO_CREDENTIAL_EXPIRED` past the stored date). A service principal stays the fallback.
- **Expiry warning:** 14 days before stored expiry, app-wide banner (reusing the `credentialStatus.ts` pattern) plus a Settings indicator. Checked on read, no scheduler.
- **Who may set it:** any signed-in user; record `updated_by`.
- **Concurrency:** small fixed pool (single digits) on virtual threads with a semaphore, overall deadline (pending items become `FAILED / TIMEOUT`, partial results returned), one honoured `Retry-After` on 429 per ticket, de-duplication by PR id and commit sha within a run. No cache library: persisted rows are the cache (ADO-05).
- **Ports:** a new dev-status port (Jira adapter under `adapters/jira/devstatus/`) and an ADO port (`adapters/ado/`) with its own `RestClient` bean mirroring `JiraRestClientConfig`. Not methods on `JiraTicketPort`.

### Deferred Ideas (OUT OF SCOPE)
- Files-touched overlap as a bug-tracing signal (needs a per-commit changes call).
- Showing PR review findings (comment threads, rejected iterations).
- Entra service principal / delegated OAuth (kept as fallback; delegated OAuth would break the SameSite=Strict CSRF control).
- Anything in Phase 8 (dashboard aggregation), pipeline/build status, searching ADO independently of Jira links, per-file commit changes, agile reports, caused-by issue links.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ADO-01 | See each ticket's linked commits and PRs | dev-status summary-first discovery (Section "Jira dev-status call plan"), PR/commit child tables, release page chip, template views |
| ADO-02 | Unlinked tickets display and generate documents normally | `NO_LINK`/`FAILED` render as empty lists; enrichment wrapped so it can never throw out of refresh/add; assembler emits lists only for `LINKED` |
| ADO-03 | Distinguish "no link" from "attempted and failed" | `enrichment_status` column + `enrichment_reason`; summary-first discovery makes drift visible as `DEV_STATUS_UNRECOGNISED`, not a silent `NO_LINK` |
| ADO-04 | Org-scoped credential, tracked expiry, pre-expiry warning | Singleton `ado_credential` row, validate-on-save via `GET /_apis/git/repositories`, `GET /api/settings/azure-devops` returning computed state, banner |
| ADO-05 | Bounded-time parallel, cached lookups | Virtual-thread pool + semaphore + overall deadline, run-scoped PR/commit de-dup, persisted rows as cache, shared Retry-After gate |
| INSIGHT-03 (data share) | Commit author dates + status-category history | `release_item_commit.authored_at`; `/changelog` + `GET /rest/api/3/status` category map; `first_done_at`, `reopen_count`, `status_changes` columns |
</phase_requirements>

## Summary

Phase 6 is mostly a persistence-and-orchestration phase, not an API-integration phase. The external surface is small: three Jira calls per ticket at most (dev-status summary, plus `pullrequest` and `repository` detail only when the summary says AzureRepos data exists), an optional changelog read per changed ticket, and, only when an org PAT is configured, **one ADO call per key-branch PR** (Get Pull Request Commits, 100 per page). The discovered key insight is that **two documented ADO endpoints from the brief are unnecessary**: PR title/status/url/source-branch already arrive from dev-status on every refresh (so Get Pull Request by id is not needed), and the PR-commits response (`GitCommitRef`) already carries `commitId`, `comment`, `author{name,date}` and `url` (so Get Commit is not needed). That keeps ADO traffic far below the shared 200 TSTU / 5 min budget.

The riskiest parts are not the HTTP calls. They are (1) the data model that Phase 8 will aggregate with set-based SQL (commits need real rows with `authored_at`; PRs need a "counts toward commits" flag), (2) keeping enrichment strictly outside the existing all-or-nothing refresh path while still writing under the `lockOpen` row lock, (3) making "Refresh code links" stamp `items_changed_at` only when the **document-visible** content changed, and (4) the Jira-side trap that a wrong `applicationType` is indistinguishable from "no link". The wire details the docs left open (A1, A3, A4) were locked by a real-tenant capture on 2026-10-12; it also overturned the cited invalid-PAT behaviour (a 302, not a 203) and showed Jira timestamps carry the tenant's non-zero offset. See "Captured Fixture Findings".

**Primary recommendation:** Build `EnrichmentService` (application) that, given an already-resolved `JiraAccessContext`, fans out per-item work on virtual threads (semaphore 6, overall deadline ~45 s), returns pure results, and then persists them in ONE transaction through a new `ReleaseRepository` method that takes the `lockOpen` lock; call it best-effort (catch-all) after the existing snapshot refresh, after create/add, and from a new "Refresh code links" endpoint. Store commits and PRs as child tables, status history as three scalar/TEXT columns on `release_ticket_item`, and the ADO credential as a singleton row updated with JPQL (never loaded to be replaced).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Link discovery (dev-status) | API / Backend (Jira adapter) | - | Needs the signed-in user's Jira Basic credential; never from browser |
| Commit completion from ADO | API / Backend (ADO adapter) | - | PAT is a server secret; never reaches the client |
| Status history (changelog) + category map | API / Backend (Jira adapter) | Database (cache of categories is in-process) | User's Jira credential; result persisted per item |
| Fan-out, de-dup, deadline | API / Backend (application service) | - | Orchestration; no DB access from worker threads |
| Enrichment state, commits, PRs, history | Database / Storage | API | Read paths (release page, documents, dashboard) never call out (D-06-01) |
| Credential storage + expiry state | Database (encrypted) | API (computed state) | AES-GCM converter; state computed on read, no scheduler |
| Expiry banner, chip, freeze warning | Browser / Client | - | Pure rendering of server-supplied state; freeze count derived from items already in the release payload |
| Key-branch rule, env-merge exclusion | API / Backend (domain) | - | Pure function over dev-status data; unit-testable, no framework imports |
| Template population | API / Backend (`ReleaseViewAssembler`) | - | Existing Jira-free assembly path; only reads the aggregate |

## Standard Stack

No new dependencies. Everything required is already on the classpath (`RestClient`, Jackson 3 `tools.jackson.databind.JsonNode`, Spring Data JPA, Flyway, WireMock 3.13.2, Testcontainers, JUnit 6, vitest 4). [VERIFIED: pom/CLAUDE.md stack table and existing sources]

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `RestClient` + `JdkClientHttpRequestFactory` | Boot 4.1.0 managed | ADO and Jira outbound HTTP | Existing `JiraRestClientConfig` pattern; add a second bean `adoRestClient` |
| `java.util.concurrent` (`Executors.newVirtualThreadPerTaskExecutor`, `Semaphore`) | JDK 25 | Bounded fan-out | Existing `RenderService` pattern. Do NOT use `StructuredTaskScope` (still preview in 25) |
| Spring Data JPA + Flyway SQL | Boot-managed | Child tables, singleton credential row | Existing patterns |
| `EncryptedTokenConverter` | in repo | AES-256-GCM PAT at rest | Reuse as-is |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Per-issue `GET /issue/{key}/changelog` | `POST /rest/api/3/changelog/bulkfetch` | Fewer calls, but it is marked **Experimental** by Atlassian; stick to the stable per-issue endpoint, optimise only if measured [CITED: community.developer.atlassian.com/t/bulk-fetch-changelogs-experimental-api/87240] |
| Child tables for commits/PRs | JSONB column on item | JSONB is simpler to write but Phase 8 needs `commits with authored_at < first_done_at` per release in set-based SQL; child tables make that a plain join |
| `GET Pull Request by id` + `GET Commit` | PR-commits endpoint only | Not needed: dev-status supplies PR fields, PR-commits supplies commit fields |

**Installation:** none.

## Architecture Patterns

### System Architecture Diagram

```
Browser                         API (request thread: user's Jira credential resolved ONCE)
  |                                                    
  | POST /releases/{id}/refresh --------------------> ReleaseService.refresh
  | POST /releases/{id}/code-links/refresh ---------> (existing) fetch+replace snapshots  [all-or-nothing, unchanged]
  | POST /releases (create) / add tickets ----------> then: EnrichmentService.enrich(access, items)   [best effort, catch-all]
  |                                                              |
  |                              +-------------------------------+--------------------------------+
  |                              | per item, virtual thread, Semaphore(6), deadline ~45s           |
  |                              v                                                                  |
  |        JiraDevStatusPort.summary(issueId) --(no AzureRepos key)--> NO_LINK                      |
  |                 | has AzureRepos                                                                |
  |                 v                                                                              |
  |        detail(pullrequest) + detail(repository)  -> PRs, key-tagged commits, repo URLs         |
  |                 | classify PRs: key-branch? (whole-token rule)  env-merge => link only         |
  |                 v                                                                              |
  |        PAT configured, not expired, org matches repo URL?                                       |
  |            yes -> AdoPort.prCommits(org, project, repoId, prId) (de-dup per run, Retry-After gate)|
  |            no  -> keep key-tagged commits (source=KEY_TAGGED)                                   |
  |                 v                                                                              |
  |        history: updated != history_synced? -> JiraHistoryPort.changelog(key) + statusCategories |
  |                 v                                                                              |
  |        ItemEnrichment (pure result: status, reason, incomplete, PRs, commits, history)          |
  |                              +-------------------------------+--------------------------------+
  |                                                              v
  |                              ReleaseRepository.saveEnrichment(releaseId, results)   ONE tx, lockOpen,
  |                                  delete+insert child rows per item; stamp items_changed_at ONLY if
  |                                  document-visible fingerprint changed (standalone action only)
  |<-- ReleaseResponse (items carry codeLinks{status,reason,incomplete,commitsSource,PRs,commits,history}) 
  |
  | GET /releases/{id} / preview / generate / dashboard  ---> DB only (never Jira/ADO)
  |
  | PUT /settings/azure-devops {organization, token, expiresOn} --> validate: GET dev.azure.com/{org}/_apis/git/repositories
  |                                  -> JPQL update-or-insert singleton row (never loads the entity)
  | GET /settings/azure-devops ---> projection (no decrypt): configured, organization, expiresOn, state OK|EXPIRING|EXPIRED
```

### Recommended Project Structure
```
domain/enrichment/        # pure records + rules, no framework imports (ArchitectureBoundaryTest)
  EnrichmentStatus, EnrichmentReason, CommitsSource, ItemEnrichment, LinkedPullRequest, LinkedCommit,
  StatusChange, DevStatusLinks, AdoRepoRef (org/project/repoId parsed+validated), TicketBranchRule
ports/
  JiraDevStatusPort            # summary + detail -> domain DevStatusLinks (no JsonNode)
  JiraIssueHistoryPort         # changelog(key) + statusCategories() -> domain
  AdoRepositoryPort            # prCommits(cred, ref, prId) + validate(cred)
  AdoCredentialRepository      # save (non-decrypting), findStatus (projection), findForUse (decrypts)
application/
  EnrichmentService            # fan-out, de-dup, deadline, classification, history derivation
  AdoCredentialService         # validate-on-save, state computation (OK/EXPIRING/EXPIRED)
adapters/jira/devstatus/       # promote JiraDevStatusSpikeAdapter -> JiraDevStatusAdapter implements port
adapters/jira/                 # JiraIssueHistoryAdapter
adapters/ado/                  # AdoRestAdapter (+ AdoRestClientConfig in config/)
adapters/persistence/          # entities, JPQL credential repo, ReleaseRepositoryAdapter additions
adapters/web/                  # AdoCredentialController, code-links refresh endpoint on ReleaseController
```

### Pattern 1: Resolve the Jira credential once, pass the value into worker threads
**What:** `JiraAccessContextService.currentContext()` reads the SecurityContext (`CurrentUserPort`). Virtual worker threads do not inherit it. Resolve the `JiraAccessContext` record on the request thread and pass it in. No worker thread touches the DB or the SecurityContext.
**Why:** otherwise worker calls throw `INVALID_CREDENTIALS` for every ticket.

### Pattern 2: Fan-out with deadline and permit-on-completion
```java
// Source: pattern from RenderService (application/RenderService.java) adapted for I/O; Java 25, no preview APIs
List<Future<ItemEnrichment>> futures = new ArrayList<>();
for (EnrichmentTarget t : targets) {
    futures.add(executor.submit(() -> { permits.acquire(); try { return enrichOne(t); } finally { permits.release(); } }));
}
long deadlineNanos = System.nanoTime() + deadline.toNanos();
for (int i = 0; i < futures.size(); i++) {
    try {
        results.add(futures.get(i).get(Math.max(0, deadlineNanos - System.nanoTime()), NANOSECONDS));
    } catch (TimeoutException e) {
        futures.get(i).cancel(true);                       // JDK HttpClient send is interruptible on a virtual thread
        results.add(ItemEnrichment.failed(targets.get(i), EnrichmentReason.TIMEOUT));
    }
}
```
Run-scoped de-dup: a `ConcurrentHashMap<PrKey, CompletableFuture<List<AdoCommit>>>` with `computeIfAbsent` keyed `(org, repositoryId, prId)` so two tickets sharing a PR make one ADO call. Created per run, discarded after (the DB is the cross-run cache).

### Pattern 3: Shared ADO throttle gate
One `AtomicReference<Instant> notBefore` per run (or per adapter instance). After every ADO response, if `Retry-After` is present set `notBefore = now + seconds`; before every ADO call a worker sleeps until `notBefore` (capped by the run deadline). On a real 429, retry once after `Retry-After`, then record `ADO_RATE_LIMITED` as the incomplete reason. ADO documents that throttled-but-delayed responses still return HTTP 200 with the headers, so header handling is required even without a 429. [CITED: learn.microsoft.com/en-us/azure/devops/integrate/concepts/rate-limits]

### Pattern 4: Persist skip-rules (the DB is the cache)
- Commit rows are immutable per sha: upsert by `(item_id, sha)`.
- A PR whose status is not `OPEN` and which already has `commits_fetched_at` is not re-fetched from ADO (its commit list is closed). `OPEN` PRs are always re-fetched. PR status itself comes fresh from dev-status each run.
- Status history is re-read only when `jira_updated_at != history_synced_updated_at` (or never read).

### Anti-Patterns to Avoid
- **Calling enrichment through the all-or-nothing refresh path.** `ReleaseService.refresh` throws `ReleaseRefreshIncompleteException` when `refreshed.size() != keys.size()`; enrichment runs AFTER `replaceItemSnapshots` in its own try/catch, and its failure is logged, not thrown.
- **Holding a DB transaction across HTTP calls.** Fetch first, write once.
- **Guessing `applicationType`.** A wrong value returns HTTP 200 `detail: []` (02-DEVSTATUS-SPIKE section 2). Never treat empty detail alone as proof of "no link".
- **Following URLs from the payload.** Build every ADO URL from a parsed, validated `(org, project, repositoryId)` against the pinned host `dev.azure.com`; never `GET` a `url` string taken from dev-status (SSRF and PAT-leak to an arbitrary host).
- **Overwriting good stored data with a transient failure's emptiness.** On `FAILED` keep prior child rows; only status/reason/attempted_at change (see Open Question 3).
- **Reusing `JiraSiteConfigRepositoryAdapter.save` (findById + mutate) for the ADO credential.** That decrypts. See Pattern in "Credential storage".

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PAT encryption | New crypto | `EncryptedTokenConverter` | Already AES-256-GCM with key-version prefix |
| Non-decrypting write | `findById` then set | JPQL `update ... where id = 1` (+ insert when count is 0), like `JiraCredentialJpaRepository.replaceCredential` | Rotating `RM_CREDENTIAL_KEY` must not lock out the replace flow (locked decision) |
| Credential status read | Loading the entity | Interface projection selecting only `organization, expiresOn, updatedAt, updatedBy` | No converter runs, Settings loads even with a stale key |
| Concurrency limiter | Custom pool | Virtual-thread executor + `Semaphore` | Existing codebase pattern |
| Pagination for ADO | Offset math | Loop on the continuation token (see pitfalls) | Offset is not offered by this endpoint |
| Date parsing | `Instant.parse` | `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx")` | Jira/dev-status timestamps are `2026-07-13T09:41:34.000+0000` (no colon in offset), which `Instant.parse`/`OffsetDateTime.parse` ISO default reject |
| Unreadable-token handling | Throwing out of enrichment | Catch `IllegalStateException` from decrypt at use time and fall back to Jira-only + incomplete `ADO_CREDENTIAL_UNREADABLE` | A rotated key must not break refresh |

**Key insight:** every "hard" part here already has a local pattern. The work is wiring them so that failure of an optional dependency (ADO) or of a best-effort one (changelog) degrades one item's state and never the release operation.

## Captured Fixture Findings (2026-10-12)

Real responses captured by `phase6-capture.sh` (D-06-13) against CNV-2896 and its key-branch PR 3127. Raw files in `spike-capture/` (git-excluded, unredacted).

| Call | Result | Consequence |
|------|--------|-------------|
| `POST /search/jql` | 200; issue has top-level `id` (string); `fields.updated` = `2026-08-13T16:31:06.042+0300`; `fields.status.statusCategory.key` present | A4 resolved; one search gives id, updated and current category |
| `GET /issue/{key}/changelog` (100 and 2 per page) | 200; `startAt`, `maxResults`, `total`, `isLast`, `values`; `nextPage` only on a non-last page; status items match A3 | A3 field names resolved; offset is `+0300`, so never assume UTC |
| `GET /rest/api/3/status` | 200; 54 statuses, many project-scoped; `Resolved` and `DEFERRED` map to different categories by project | Map by id only |
| `GET {org}/_apis/git/repositories` valid PAT | 200 JSON, `count` 62 | PAT validation call works (A9) |
| same, invalid PAT | **302** to `vssps.visualstudio.com/_signin`, `text/html`, `WWW-Authenticate` | Overturns the cited 203; redirects off, 3xx = unauthorized |
| PR commits `$top=3` | 200, `{count, value}`, header `x-ms-continuationtoken` | A1 resolved |
| PR commits, no `$top` | 200, all 34 commits, no continuation header | Default page >= 34; header absent when done |
| Commit shape | `commitId`, `author{name,email,date}`, `committer`, `comment`, `url`; dates UTC `Z` (`2026-07-12T09:35:11Z`) | `Instant.parse` works for ADO dates |

## Verified External API Details

### Azure DevOps REST 7.1
- **Get Pull Request Commits:** `GET https://dev.azure.com/{organization}/{project}/_apis/git/repositories/{repositoryId}/pullRequests/{pullRequestId}/commits?$top={n}&continuationToken={t}&api-version=7.1`. `project` is optional; `repositoryId` accepts the GUID or name; `pullRequestId` is int32; scope `vso.code`. Response `GitCommitRef[]` with `commitId`, `comment`, `commentTruncated`, `author{name,email,date}`, `committer{...}`, `url`, `remoteUrl`. [CITED: learn.microsoft.com/en-us/rest/api/azure/devops/git/pull-request-commits/get-pull-request-commits?view=azure-devops-rest-7.1]
- Wrapping: ADO list endpoints return `{ "count": n, "value": [...] }` (see the Repositories-List sample below). [CITED: same docs family]
- **Pagination:** the page documents `$top` and `continuationToken` but not the maximum page size or the response header carrying the next token. The next token arrives in the response header `x-ms-continuationtoken`, absent on the last page. [VERIFIED: 2026-10-12 capture, A1] The default page size is at least 34 (an unpaged call on PR 3127 returned all 34 commits with no token); the maximum was not probed. Implement: always send an explicit `$top=100`, read `x-ms-continuationtoken` (Spring `HttpHeaders` is case-insensitive), loop until absent, and enforce a hard per-PR page ceiling.
- **Get Pull Request:** `GET .../pullrequests/{id}?api-version=7.1`, `status` enum `notSet|active|abandoned|completed|all`, plus `closedDate`, `creationDate`, `sourceRefName` (`refs/heads/...`), `targetRefName`. Not needed in v1 because dev-status already supplies title/status/url/branches. [CITED: learn.microsoft.com/en-us/rest/api/azure/devops/git/pull-requests/get-pull-request?view=azure-devops-rest-7.1]
- **Cheap PAT validation (recommended):** `GET https://dev.azure.com/{org}/_apis/git/repositories?api-version=7.1` (project is optional; sample at `https://dev.azure.com/fabrikam/_apis/git/repositories?api-version=7.1`; scope `vso.code`). It proves token validity, `vso.code` scope AND that the org name is correct in one call. [CITED: learn.microsoft.com/en-us/rest/api/azure/devops/git/repositories/list?view=azure-devops-rest-7.1] Do NOT use `_apis/projects` (needs `vso.project`, which a Code-Read PAT lacks) or rely on `connectionData` (does not prove `vso.code`).
- **Auth:** `Authorization: Basic base64(":" + PAT)` (empty username). Same as the project's stack notes. [CITED: learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate]
- **Rate limits:** global consumption limit is **200 TSTUs per sliding 5-minute window, per identity** (so one shared PAT = one shared budget for all users of this tool). Delays up to 30 s per request; blocked requests get HTTP 429 `TF400733`. Headers: `Retry-After` (seconds), `X-RateLimit-Delay`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (epoch), `X-RateLimit-Resource`, `X-RateLimit-Cost`. Best practice from the docs: honour `Retry-After` even on HTTP 200; track `Remaining`. The tool's identity can get higher limits with Basic + Test Plans access. [CITED: learn.microsoft.com/en-us/azure/devops/integrate/concepts/rate-limits, page updated 2026-07-31]
- **Invalid-PAT behaviour:** an invalid PAT on `dev.azure.com` is answered with **HTTP 302 Found**, `Location` pointing to a `vssps.visualstudio.com/_signin` page, `Content-Type: text/html` and `WWW-Authenticate` headers - not 401, and not the 203 that community answers report. [VERIFIED: 2026-10-12 capture; overturns learn.microsoft.com/en-us/answers/a/559812] Build the ADO `HttpClient` like `JiraRestClientConfig` does (`HttpClient.newBuilder()` defaults to `Redirect.NEVER`; never enable redirects, or the 302 becomes a 200 HTML sign-in page). Spring's default error handler ignores 3xx, so register `onStatus` for any 3xx and for 203, 401 and 403, all mapping to `ADO_UNAUTHORIZED`, so the HTML body is never decoded as JSON.
- **Global PAT retirement:** creation of global PATs is still allowed until 1 Dec 2026; all existing global PATs stop working on **1 Dec 2026** (today is 2026-10-08, 54 days away). [CITED: devblogs.microsoft.com/devops/retirement-of-global-personal-access-tokens-in-azure-devops] The Settings help text must say "select one organization", and the expiry date entered must be the real PAT expiry.

### Deriving org / project / repo from dev-status (fixture-verified)
Repository URL shape (from `devstatus-cnv-2896-detail.json`): `https://dev.azure.com/{org}/{project}/_git/{repo}`, plus a GUID `repositoryId` on every PR and `repositories[].id`. PR `url` = `{repoUrl}/pullrequest/{id}`; commit `url` = `{repoUrl}/commit/{sha}`; branch `url` = `{repoUrl}?version=GB{branch}`. Parse with `java.net.URI`: host must equal `dev.azure.com`; path segments `[org, project, "_git", repo]`; percent-decode project/repo; validate org against `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` before it is ever put in a URL path. **Use the `repositoryId` GUID, not the name, for the API path.** Legacy `https://{org}.visualstudio.com/...` URLs were not observed; an unparseable URL means "no ADO call for this item" (Jira-only data, not an error). [ASSUMED for the visualstudio.com form; A2]

### Jira Cloud
- **Changelog:** `GET /rest/api/3/issue/{key}/changelog?startAt=&maxResults=` returns a paginated list sorted oldest-first; `maxResults` defaults to 100; the response has `startAt`, `maxResults`, `total`, `isLast`, `values[]`. [CITED: community summary of developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issues/ (official page too long to fetch)] Each history entry has `created` and `items[]`; a status item has `field: "status"`, `fieldId: "status"`, `fieldtype: "jira"`, `from`/`to` (status ids, as strings) and `fromString`/`toString` (names). [VERIFIED: 2026-10-12 capture, A3] `created` carries the tenant's local offset with no colon (`2026-07-05T08:48:47.457+0300`); parse with the Pitfall 3 pattern (`SSSxx`), never by assuming UTC. A non-last page also carries `nextPage`; `isLast` was correct on both captured pages. Paginate with `startAt += values.size()` and stop when `isLast`, an empty page, or `startAt >= total` (belt and braces, given the project's own note that `isLast` misbehaves on `/search/jql`), with a page ceiling.
- **Status -> category:** changelog items carry only status id/name. The category map comes from `GET /rest/api/3/status`, which returns each status with `id`, `name`, `statusCategory{key,name}`; results are limited to statuses visible to the user. [CITED: developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-workflow-statuses/ (fetch summary)] **Status names are not unique:** the capture returned 54 statuses, many `scope.type: PROJECT`, and the same name maps to different categories (`Resolved` is `new`, `indeterminate` and `done` in different projects). Map by status **id** only, never by name. [VERIFIED: 2026-10-12 capture] Cache the id-to-category map in a `volatile` field exactly like `FieldMetadataService.fieldsByLowerCasedName`, refreshing once when an id is missing. A historical status that no longer exists in an active workflow can be absent: store its category as `UNKNOWN`, never guess Done from the name, and mark history `UNAVAILABLE` if the first-Done decision depends on an unknown (see Pitfall 6). The **current** status's category is free: `fields.status.statusCategory.key` (`new|indeterminate|done`) is already inside the status field returned by `/search/jql`.
- **Cheap "changed since last fetch":** the `updated` system field from the existing `/search/jql` call. Add the literal field id `updated` to the requested ids (system field ids are fixed; no need to route it through `FieldMetadataService` name resolution). Store it on the item as `jira_updated_at`; compare against `history_synced_updated_at` (the `updated` value in force when history was last read, captured from the SEARCH response, not from the changelog time, so a change between search and changelog read is caught next run).
- **Numeric issue id:** `/search/jql` issue objects carry top-level `id` (the existing adapter already reads the sibling `key` from the same object). `TicketDetail` has no id today; add `jiraIssueId`, `updatedAt` and `statusCategory` to `TicketDetail` (17 `new TicketDetail(` sites across 8 files, mostly tests; mechanical). [VERIFIED: grep]. The id is a JSON string (`"id": "…"`), alongside `expand`, `self`, `key` and `fields`. [VERIFIED: 2026-10-12 capture, A4]
- **Rate limiting:** points-based model, burst limits GET 100 rps per endpoint; 429 carries `Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `RateLimit-Reason`; backoff 2 s doubling, jitter. [CITED: developer.atlassian.com/cloud/jira/platform/rate-limiting/] How API-token (non-app) traffic is metered was not confirmable from the page; treat as per-user and keep concurrency single-digit. [ASSUMED, A5]

### Jira dev-status call plan (per ticket, signed-in user's credential)
1. `GET /rest/dev-status/1.0/issue/summary?issueId={numericId}`. If none of `summary.pullrequest|repository|branch.byInstanceType` contains `AzureRepos` => `NO_LINK` (1 call). (Fixture-verified key; `build` is `cloud-providers` and out of scope.)
2. Else `GET .../detail?issueId=&applicationType=AzureRepos&dataType=pullrequest` (branches + PRs arrive in one payload; `dataType=branch` returns an identical payload, so one call) and, if `repository.byInstanceType` has `AzureRepos`, `dataType=repository` for key-tagged commits.
3. Validate the envelope: `detail` array, `_instance.type == "AzureRepos"`. If the summary advertised AzureRepos but the detail is empty or `_instance.type` differs => `FAILED / DEV_STATUS_UNRECOGNISED` (this is the guard the spike asked for; it converts the silent wrong-key trap into a visible failure).
Cost: 1 call for unlinked tickets, 3 for linked. Alternative (pin `AzureRepos` in config, 2 calls) is acceptable but loses the drift guard; summary-first is recommended.

## Persistence Design (migration V8)

**Migration numbering:** latest is `V7__template_user_default.sql`. Phase 7's context also says it "starts at V8". Phase 6 executes first (roadmap order), so Phase 6 takes `V8` (and `V9` if split) and **Phase 7's plans must renumber to V10+ when planned**; add that as an explicit note to the Phase 7 planner. Flyway aborts at startup on duplicate versions, so the failure is loud but late. Check `ls src/main/resources/db/migration` at each plan start.

```sql
-- V8__ado_enrichment.sql (sketch)
ALTER TABLE release_ticket_item
  ADD COLUMN jira_issue_id            VARCHAR(32),
  ADD COLUMN jira_updated_at          TIMESTAMPTZ,
  ADD COLUMN jira_status_category     VARCHAR(16),             -- TO_DO|IN_PROGRESS|DONE (current, from search)
  ADD COLUMN enrichment_status        VARCHAR(16) NOT NULL DEFAULT 'NOT_ATTEMPTED'
             CHECK (enrichment_status IN ('NOT_ATTEMPTED','NO_LINK','LINKED','FAILED')),
  ADD COLUMN enrichment_reason        VARCHAR(40),             -- FAILED cause, or why LINKED is incomplete
  ADD COLUMN enrichment_incomplete    BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN enrichment_attempted_at  TIMESTAMPTZ,
  ADD COLUMN commits_source           VARCHAR(16) CHECK (commits_source IN ('KEY_TAGGED','ADO_COMPLETE')),
  ADD COLUMN history_state            VARCHAR(16) CHECK (history_state IN ('AVAILABLE','UNAVAILABLE')),
  ADD COLUMN history_synced_updated_at TIMESTAMPTZ,
  ADD COLUMN first_done_at            TIMESTAMPTZ,
  ADD COLUMN reopen_count             INTEGER,
  ADD COLUMN status_changes           TEXT;                    -- JSON array, display/audit only, never queried by SQL

CREATE TABLE release_item_pull_request (
  id UUID PRIMARY KEY,
  item_id UUID NOT NULL REFERENCES release_ticket_item (id) ON DELETE CASCADE,
  repository_id VARCHAR(64) NOT NULL, pr_id VARCHAR(32) NOT NULL,
  title VARCHAR(500) NOT NULL, status VARCHAR(32) NOT NULL, url VARCHAR(1000) NOT NULL,
  source_branch VARCHAR(500) NOT NULL, target_branch VARCHAR(500),
  counts_toward_commits BOOLEAN NOT NULL,               -- key-branch rule result; false = env-merge, shown as link only
  commits_fetched_at TIMESTAMPTZ,
  CONSTRAINT rpr_unique UNIQUE (item_id, repository_id, pr_id));

CREATE TABLE release_item_commit (
  id UUID PRIMARY KEY,
  item_id UUID NOT NULL REFERENCES release_ticket_item (id) ON DELETE CASCADE,
  sha VARCHAR(64) NOT NULL, message VARCHAR(1000) NOT NULL,
  author_name VARCHAR(255), authored_at TIMESTAMPTZ NOT NULL, url VARCHAR(1000) NOT NULL,
  CONSTRAINT ric_unique UNIQUE (item_id, sha));
CREATE INDEX ric_item_authored_idx ON release_item_commit (item_id, authored_at);

CREATE TABLE ado_credential (                            -- singleton, mirrors jira_site_config
  id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  organization VARCHAR(64) NOT NULL,
  encrypted_token TEXT NOT NULL, key_version SMALLINT NOT NULL,
  expires_on DATE NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by VARCHAR(255) NOT NULL);
```

Decisions behind the shape:
- **Commits and PRs are rows, history is columns.** Phase 8 needs only `first_done_at`, `reopen_count`, and `release_item_commit.authored_at` joins; the full transition list (`status_changes`) is kept as audit/display JSON per D-06-08 ("full transition list") but is never an SQL predicate. Branches are not stored (only needed transiently to classify PRs).
- **Commit set when a PAT works:** commits of every `counts_toward_commits` PR from ADO (source `ADO_COMPLETE`) UNION the key-tagged commits from dev-status not already present. Without a usable PAT: key-tagged commits only (`KEY_TAGGED`). One `commits_source` per item, because a stored list is either complete or not. (Open Question 2.)
- **`LINKED` means at least one PR or commit.** Branch-only tickets are `NO_LINK` (Open Question 1).
- **Reason column semantics:** one column. For `FAILED` it holds the cause (dev-status/Jira/timeout); for `LINKED` + `incomplete` it holds the ADO cause (`ADO_UNAUTHORIZED`, `ADO_CREDENTIAL_EXPIRED`, `ADO_NOT_FOUND`, `ADO_RATE_LIMITED`, `ADO_CREDENTIAL_UNREADABLE`, `COMMIT_LIMIT`). **ADO failures can never produce `FAILED`**: D-06-03/D-06-09 (dev-status succeeded => LINKED) supersede the D-06-02 example list. A never-configured PAT sets no flag and no reason (by design Jira-only); a configured-but-expired/rejected PAT sets `incomplete` + reason so the user is prompted to fix it.
- **Manual items:** untouched (separate table, no state).
- **Freeze warning count:** derive on the client from the items already in `ReleaseResponse` (`FAILED` + `NOT_ATTEMPTED` among ticket items). No new endpoint.
- **`ReleaseItem`** gains an `ItemEnrichment`/code-links component; `ReleaseItemResponse` gains a nested `codeLinks` object (status, reason, incomplete, attemptedAt, commitsSource, pullRequests[], commits[], firstDoneAt, reopenCount, historyState).
- **Render cap hazard:** `RenderService.enforceCollectionCap` throws `RenderBoundExceededException` when any item's `commits` list exceeds `app.template.render.max-collection-size` (500), which would fail document generation for a ticket with many commits and violate ADO-02. Cap ingestion (`app.ado.max-commits-per-ticket`, e.g. 400) and mark `incomplete` + `COMMIT_LIMIT` on truncation. Truncate commit message to its first line, max ~500 chars.

### Credential storage (ADO-04)
- Entity `AdoCredentialEntity` with `@Convert(converter = EncryptedTokenConverter.class)` on the token.
- JPQL `@Modifying update AdoCredentialEntity e set e.organization=:org, e.token=:token, e.keyVersion=:kv, e.expiresOn=:exp, e.updatedAt=:now, e.updatedBy=:by where e.id = 1`; if the count is 0, insert a new entity (nothing to decrypt when there is no row). A separate JPQL `update ... set e.expiresOn` supports changing only the expiry without re-entering the token.
- Status read = Spring Data interface projection (no entity, no converter), like `findEmailsByAccountIdIn`.
- Use-time read decrypts; catch `IllegalStateException` and degrade (see Don't Hand-Roll).
- Compute state on read: `EXPIRED` if `expiresOn` is before today (UTC), `EXPIRING` if within 14 days, else `OK`. No scheduler.
- Endpoints: `GET/PUT /api/settings/azure-devops`, plus optionally `DELETE` to clear. Never return the token. PUT validates (org regex, `expiresOn` not in the past, one `GET repositories` call) and saves only on success; a rejected token returns a ProblemDetail with a machine-readable reason and writes nothing.
- Frontend: the `credentialStatus.ts` pattern is an **event-driven external store** set when a 401 occurs; the ADO banner is different (state is known from a query). Implement as a `useAdoCredentialStatus()` TanStack Query consumed by a small banner component rendered beside `CredentialAlert` in `AppShell`, plus a Settings section; reuse the same amber banner styling. `SettingsRoute.tsx` is 151 lines of form-state-per-section; add a third section the same way.
- **No CSRF work needed:** new POST/PUT routes sit under the existing `/api/**` rule and SameSite=Strict cookie; do not relax SameSite (locked decision).

## Common Pitfalls

### Pitfall 1: Wrong `applicationType` looks like "no link"
**What goes wrong:** HTTP 200, `{"errors":[],"detail":[]}` for `azure`, `AzureDevOpsGit`, `Azure Repos`, `cloud-providers`.
**How to avoid:** summary-first (above); `DEV_STATUS_UNRECOGNISED` when summary and detail disagree; log the application type queried.
**Warning signs:** a release where every ticket suddenly becomes `NO_LINK`.

### Pitfall 2: Env-merge PRs inflate commit counts
**What goes wrong:** PR 1001 (fixture) is `develop -> testing`, linked only by title; its commits contain other tickets' work.
**How to avoid:** `TicketBranchRule`: source branch matches `(?<![A-Za-z0-9])` + `Pattern.quote(KEY)` + `(?![0-9])`, case-insensitive (so `feature/CNV-2896-x` and `cnv-2896` match, `CNV-28961` and `XCNV-2896` do not). Non-matching PRs are stored with `counts_toward_commits=false` and still shown as links. Never request their commits from ADO.

### Pitfall 3: Timestamps with no-colon offsets (`+0000`, `+0300`)
**What goes wrong:** `Instant.parse("2026-07-13T09:41:34.000+0000")` throws. Applies to dev-status `authorTimestamp`/`lastUpdate` and Jira changelog `created`/`fields.updated`, which the 2026-10-12 capture showed in the tenant offset (`+0300`), not UTC.
**How to avoid:** one shared formatter (`yyyy-MM-dd'T'HH:mm:ss.SSSxx`); ADO PR-commit `author.date` is ISO-8601 with `Z` and parses normally. Unit-test both.

### Pitfall 4: Stamping `items_changed_at` on a no-op code-links refresh
**What goes wrong:** every "Refresh code links" marks documents STALE.
**How to avoid:** compute a fingerprint of exactly what `ReleaseViewAssembler` would emit for the item (commit id/message/author/url list in order, PR id/title/status/url list in order, applying the `LINKED`-only rule) before and after; stamp only if any item's fingerprint differs. `attempted_at`, reason, history and dates do not stamp. Notably `FAILED -> NO_LINK` does not stamp (both render empty) while `LINKED -> FAILED` does (commits disappear from documents).

### Pitfall 5: Freeze race and the stale pre-check
**What goes wrong:** the service's `requireOpen()` check runs on an earlier snapshot; fan-out takes up to ~45 s.
**How to avoid:** `saveEnrichment` must call `lockOpen(releaseId)` inside its transaction like every other mutator in `ReleaseRepositoryAdapter`, and the best-effort wrapper swallows `ReleaseFrozenException` (enrichment lost, release unharmed). For the standalone action surface it as the normal 409.

### Pitfall 6: Status categories for vanished statuses
**What goes wrong:** a status that existed in a past transition is no longer in an active workflow, so `/rest/api/3/status` lacks it; mapping by name would be wrong.
**How to avoid:** `UNKNOWN` category; compute `first_done_at` only from transitions whose `to` category is known `DONE`; if any transition has an unknown category, set `history_state=UNAVAILABLE` rather than publish a possibly wrong figure (Phase 8 shows "no data"). Reopen = a transition whose `from` category is `DONE` and `to` category is not `DONE`.

### Pitfall 7: Worker threads lose the user context and exceptions leak
**How to avoid:** Pattern 1; every worker body catches `RuntimeException`, maps to an `ItemEnrichment` with a reason, and never rethrows. The only thing allowed to escape `enrich(...)` is nothing.

### Pitfall 8: `updated` bumps on comments
Comment edits change `updated`, causing extra changelog reads. Acceptable (one cheap call); do not try to be cleverer.

### Pitfall 9: ADO org mismatch
The PAT is org-scoped. If a ticket's repo URL org differs from the configured organization, make no ADO call (Jira-only data) and mark `incomplete` with reason `ADO_CREDENTIAL_MISSING`? Recommended: no flag when no credential is configured at all; `incomplete` + `ADO_ORG_MISMATCH` when a credential exists for a different org, so the gap is visible.

### Pitfall 10: Test config shadowing
`src/test/resources/application.yml` shadows main's (env landmine). Every new `app.ado.*` / `app.enrichment.*` property must be added to both files or full-context ITs fail on unresolved placeholders.

### Pitfall 11: Untrusted text into documents and links
Commit messages, PR titles and author names are user-controlled strings that flow into Mustache variables and Markdown. Keep the existing Mustache variable escaping path (do not add triple-mustache usage to starter templates), truncate, and on the frontend render a stored `url` as an `href` only when it starts with `https://dev.azure.com/` (reject `javascript:`). Store only URLs that pass the same check.

### Pitfall 12: Environment landmines (from project memory)
`export JAVA_HOME="$HOME/.jdks/jdk-25"` for every Maven call; no Vite dev server while Maven's frontend profile runs `npm ci` (EPERM); use `mvn clean` when changing public APIs (`TicketDetail`, `ReleaseItem` constructors change in this phase, so incremental compilation will hide broken tests); worktree merges produce CRLF.

## Code Examples

### ADO PR commits page loop (sketch)
```java
// Source: ADO REST 7.1 Get Pull Request Commits; header name and 302-on-bad-PAT verified by the 2026-10-12 capture
String token = null;
int pages = 0;
do {
    ResponseEntity<JsonNode> res = adoRestClient.get()
        .uri("https://dev.azure.com/{org}/{project}/_apis/git/repositories/{repo}/pullRequests/{pr}/commits"
             + "?$top={top}&api-version=7.1" + (token == null ? "" : "&continuationToken={t}"), vars)
        .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((":" + pat).getBytes(UTF_8)))
        .retrieve()
        .onStatus(s -> s.is3xxRedirection() || s.value() == 203 || s.value() == 401 || s.value() == 403,
                  (rq, rs) -> { throw new AdoUnauthorizedException(); })
        .toEntity(JsonNode.class);
    gate.observe(res.getHeaders());                       // Retry-After / X-RateLimit-Remaining
    for (JsonNode c : res.getBody().path("value")) { /* commitId, comment, author.name, author.date, url */ }
    token = res.getHeaders().getFirst("x-ms-continuationtoken");
} while (token != null && ++pages < MAX_PAGES);
```

### Status history derivation (pure, domain)
```java
// inputs: List<StatusChange(at, fromCategory, toCategory)> sorted ascending
Instant firstDone = changes.stream().filter(c -> c.to() == DONE).map(StatusChange::at).findFirst().orElse(null);
int reopens = (int) changes.stream().filter(c -> c.from() == DONE && c.to() != DONE).count();
```

### Key-branch rule
```java
static boolean isTicketBranch(String sourceBranch, String key) {
    return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(key) + "(?![0-9])", CASE_INSENSITIVE)
                  .matcher(sourceBranch).find();
}
```

## Integration Points (existing code to change)

| Concern | Where | Change |
|---------|-------|--------|
| Search returns id/updated/category | `JiraRestTicketAdapter.toTicketDetail` (reads `issue.path("key")` at line 98), `TicketDetail` | Add `jiraIssueId`, `updatedAt`, `statusCategory`; request literal `updated`; map `fields.status.statusCategory.key` (`new`->TO_DO, `indeterminate`->IN_PROGRESS, `done`->DONE; reuse `domain/jira/StatusCategory`) |
| Snapshot overwrite | `ReleaseRepositoryAdapter.overwriteSnapshot` | Also write `jira_issue_id`, `jira_updated_at`, `jira_status_category` (never touch `history_synced_updated_at`) |
| Refresh | `ReleaseService.refresh` (lines 99-113) | After `replaceItemSnapshots`, call `enrichmentService.enrich(...)` inside try/catch(Exception), then `return get(releaseId)`. Keep the existing size check for snapshots only |
| Create/add | `createWithTickets`, `addTickets` | Enrich the newly inserted items best-effort after the insert transaction commits (create must return the persisted release) |
| New action | `ReleaseController` (`/refresh`, `/freeze` at lines 90-97) | `POST /{releaseId}/code-links/refresh` with optional ticket key filter for per-item retry; service calls `requireOpen()`; resolve `JiraAccessContext` once; if issue ids are null (pre-Phase-6 rows) run a minimal `key in (...)` search requesting only `updated` and `status` via a small new `JiraTicketPort` method returning `(key,id,updated,statusCategory)` rather than the heavy full-field search |
| Assembler | `ReleaseViewAssembler.mapTicketItem` (hard-coded `List.of()`, line 112-113) | Emit `CommitView(shortSha, firstLine, author, url)` and `PullRequestView(id,title,status,url)` only when `enrichment_status == LINKED`; `CommitView.id` = first 7 chars of sha; order commits by `authored_at` then sha, PRs by id numeric |
| Views | `CommitView`, `PullRequestView` | Do not add fields (keeps `PlaceholderCatalogTest` bidirectional check stable); update their "always empty until Phase 6" comments |
| Catalog | `PlaceholderCatalog.java:70-111` | Remove the `"Always empty until Phase 6"` tag on 8 entries; reword (`"The pull request status, such as OPEN or MERGED."` - dev-status values, not ADO `active/completed`); add a note that without an Azure DevOps credential commits are those whose message names the ticket key |
| Catalog test | `PlaceholderCatalogTest.commitsAndPullRequestsAreTaggedAlwaysEmptyUntilPhaseSix` | Delete/replace with an assertion that the tag is gone; the six bidirectional record-component tests stay green because no component changes. `descriptions end with "."` test still applies. Also update `frontend/src/components/PlaceholderDrawer.tsx` which references the Phase 6 tag |
| Sample view | `SampleReleaseView` | Keep `TICKET_NO_LINKED_CODE`; add commits/PRs to one other sample ticket (or add a third) so save-time validation exercises `{{#commits}}` sections; check `TemplateServiceTest`/`MustacheFlexmarkRenderAdapterTest` item-count expectations |
| ArchUnit | `ArchitectureBoundaryTest` | Domain/ports must stay free of Spring/Jackson/jakarta; `application` must not import adapters (use ports); mapping `JsonNode` -> domain records happens inside the adapters; web adapter must not depend on `JiraCredentialRepository` (do not add an ADO-token-bearing port to controllers either: the status controller uses a projection-only method) |
| Frontend | `ReleaseDetailRoute.tsx` (602 lines; refresh button line ~215, freeze `ConfirmDialog` ~263), `api/releases.ts`, `SettingsRoute.tsx`, `AppShell` | Chip + expandable list, "Refresh code links" button (+ per-item retry), freeze dialog count and "Refresh code links first", Settings ADO section with "select one organization" help, expiry banner; invalidate the release query after code-link refresh; add dashboard query-key invalidation when Phase 8 lands (D-08 note) |

Existing config: `app.jira.connect-timeout PT5S`, `read-timeout PT10S` (a 45 s overall deadline covers several sequential calls per ticket). New properties suggested: `app.ado.connect-timeout`, `app.ado.read-timeout`, `app.enrichment.max-concurrent: 6`, `app.enrichment.deadline: PT45S`, `app.enrichment.max-commits-per-ticket: 400`, `app.ado.max-pages-per-pr`. Reusing the existing `jiraRestClient` bean for dev-status and changelog calls is correct (same host, same timeouts); `adoRestClient` needs its own bean because the host, credential and tuning differ, and because two `RestClient` beans of the same type mean existing constructors injecting `RestClient jiraRestClient` resolve by parameter name; keep the bean names explicit. [VERIFIED: existing constructors use parameter name `jiraRestClient`]

## Open Questions

1. **Branch-only tickets (a branch but no PR and no commit naming the key).**
   - What we know: INSIGHT-02 counts "linked Azure DevOps commits or pull requests"; the spike fixture shows a branch can have a `lastCommit` the repository list omits.
   - Recommendation: `NO_LINK` (nothing displayable), and mention it in the chip tooltip is unnecessary. Confirm with the user if branch-only tickets should count as linked.
2. **Union vs replace when a PAT works.**
   - Recommendation: ADO commits of key-branch PRs UNION key-tagged commits not already present (a commit naming the key but landed outside a PR still belongs to the ticket). Impact: only commit counts/post-Done classification. Planner may flip to ADO-only if the user prefers a purely PR-scoped count.
3. **Failed re-fetch of a previously `LINKED` item.**
   - Recommendation: keep prior child rows, flip status to `FAILED` (ADO-03 visibility), render "couldn't fetch, showing last known" on the release page, and emit empty lists to documents (D-06-07). The alternative (delete rows) loses good data to a transient error.
4. **Timeout of the synchronous refresh request.** 45 s may exceed some reverse-proxy timeouts. If the deployment proxy is stricter, lower `app.enrichment.deadline`; unfinished items become `FAILED/TIMEOUT` and are retried by the next refresh.
5. **ROADMAP.md Phase 6 criterion 6 still says "and its issue links".** CONTEXT D-06-08 dropped them. Treat the criterion as satisfied without links; the planner should not create an issue-link task. (ROADMAP edit is outside this research.)
6. **Per-item retry shape:** reuse the single endpoint with an optional key parameter (recommended, least code) versus a per-item route.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | ~~PR-commits continuation in `x-ms-continuationtoken` or `x-ms-continuation-token`~~ **VERIFIED 2026-10-12:** `x-ms-continuationtoken`, absent on the last page; default page size >= 34, max not probed | Verified External API / Code Examples | Resolved. Explicit `$top=100` plus page ceiling still applies |
| A2 | dev-status repo URLs are always `https://dev.azure.com/{org}/{project}/_git/{repo}`; legacy `*.visualstudio.com` form is unobserved | Deriving org/project/repo | Legacy-URL orgs get Jira-only data with no error. Low impact |
| A3 | **VERIFIED 2026-10-12** for field names (`field`/`fieldId` = `status`, `from`/`to` ids, `fromString`/`toString`). **Corrected** for timestamps: `created` uses the tenant offset (`+0300`), not `+0000` | Jira Cloud | Resolved. The Pitfall 3 `SSSxx` pattern handles it; a parser that hard-codes `+0000` or UTC would shift every transition by 3 h |
| A4 | **VERIFIED 2026-10-12:** `/search/jql` issue objects carry top-level `id` (JSON string) | Jira Cloud | Resolved. The `resolveNumericIssueId` extra call is not needed |
| A5 | Jira API-token traffic is limited per user and single-digit concurrency stays well under burst limits | Jira Cloud | Throttling shows as `JIRA_RATE_LIMITED` per item, never failing refresh; tune `max-concurrent` down |
| A6 | dev-status PR `status` values beyond `OPEN` and `MERGED` (likely `DECLINED`) are unobserved | Integration Points / catalog wording | Store the raw string; wording says "such as OPEN or MERGED" |
| A7 | An ADO PR's commit list is closed once the PR is completed/abandoned, so skipping re-fetch is safe | Pattern 4 | A late-pushed commit to a merged PR's branch is not part of the PR; acceptable. If wrong, drop the skip rule (more ADO calls, still bounded) |
| A8 | ADO org name charset `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` is a safe superset | Credential/URL parsing | Rejecting a valid org name at save time; loosen the regex |
| A9 | **VERIFIED 2026-10-12:** `GET .../_apis/git/repositories` returns 200 with a valid Code (Read) PAT (62 repos, ~50 KB); an invalid PAT returns **302** to a sign-in page (not 401/203) | PAT validation | Resolved. Treat 3xx/203/401/403 as unauthorized with redirects disabled |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Jira tenant + user API token | Capturing the changelog/status fixtures (A3, A4) | **captured 2026-10-12** (`spike-capture/`, git-excluded, unredacted) | - | Redact into test fixtures like plan 02-04 |
| Live ADO org + `vso.code` PAT | Capturing the PR-commits headers/pagination fixture (A1) | **captured 2026-10-12** (`spike-capture/`, git-excluded, unredacted) | - | Redact into test fixtures |
| Docker (Testcontainers Postgres) | Persistence ITs | assumed available (existing ITs run) | - | - |
| JDK 25 / Maven | Build | requires `export JAVA_HOME="$HOME/.jdks/jdk-25"` per shell | 25 | - |

**Missing with no fallback:** none. The human capture (D-06-13) is done; the first plan only needs to redact the raw files into committed fixtures.

## Validation Architecture

`workflow.nyquist_validation` is `false` in `.planning/config.json`, so no Nyquist artifacts are required; this is the concise test plan the planner should still follow.

| Property | Value |
|----------|-------|
| Backend framework | JUnit 6 + AssertJ + WireMock 3.13.2 + Testcontainers 2.0.5 (existing) |
| Frontend framework | vitest 4 + jsdom, tests under `frontend/tests/**` (existing; includes `tests/api` and `tests/components`) |
| Quick run | `export JAVA_HOME="$HOME/.jdks/jdk-25"; mvn -P '!frontend' -Dtest=<Class> test` |
| Full suite | `mvn -P '!frontend' clean test` (641 green baseline after 02-04) and `cd frontend && npm test` |

| Req | Behavior | Test type | File / note |
|-----|----------|-----------|-------------|
| ADO-01 | Summary then detail parsed from the three redacted fixtures into PRs/commits; env-merge PR 1001 excluded from counts but kept as link; 1002/1003 counted | unit + WireMock | promote `JiraDevStatusSpikeAdapterTest` to `JiraDevStatusAdapterTest`; keep the envelope-key assertions (schema-drift guard) |
| ADO-01 | Key-branch rule: `feature/EXAMPLE-1`, `example-1-x` match; `EXAMPLE-12`, `XEXAMPLE-1` do not | unit (domain) | `TicketBranchRuleTest` |
| ADO-02 | NO_LINK and FAILED items give empty `commits`/`pullRequests` in `ReleaseViewAssembler`; generation succeeds | unit | extend `ReleaseViewAssemblerTest` |
| ADO-02 | Enrichment exceptions never escape `refresh`/`create`/`add`; refresh with ADO 500 still returns the release | service test with failing ports | `ReleaseServiceTicketsTest` additions |
| ADO-03 | Summary advertises AzureRepos but detail empty => `FAILED/DEV_STATUS_UNRECOGNISED`; zero byInstanceType keys => `NO_LINK` | WireMock | adapter/service |
| ADO-04 | Save validates (200 ok, 302/203/401/403 rejected, wrong org 404), writes nothing on failure; state OK/EXPIRING(<=14d)/EXPIRED; token never in any response | controller test + `AdoCredentialPersistenceIT` | IT must include **key-rotation case**: write with key A, read status via projection and replace under key B without exception (locked decision) |
| ADO-05 | N tickets sharing a PR make one ADO call; 6-permit cap respected; deadline turns pending items into `FAILED/TIMEOUT` with partial results; `Retry-After` honoured once | unit with latch/stubs + WireMock `withFixedDelay` | `EnrichmentServiceTest` |
| INSIGHT-03 | Pagination of changelog (>100 entries) and status-id to category mapping; first_done/reopen derivation incl. unknown category => UNAVAILABLE; `+0000` and `+0300` no-colon offset timestamp parse; status mapped by id where names collide; changelog skipped when `updated` unchanged | WireMock + unit | `JiraIssueHistoryAdapterTest`, `StatusHistoryTest` |
| D-06-04 | Standalone refresh stamps `items_changed_at` only on fingerprint change (no-op, FAILED->NO_LINK: no stamp; LINKED->FAILED: stamp) | `ReleasePersistenceIT` | uses Testcontainers |
| D-06-01 | Frozen release rejects enrichment writes (`lockOpen`) | persistence IT | |
| Catalog | `PlaceholderCatalogTest` bidirectional tests still pass; "until Phase 6" tag gone | unit | |
| Frontend | chip text ("3 commits · 1 PR"), "No link" vs "Couldn't fetch (reason)" distinct, freeze dialog count, expiry banner at 14 days/expired, Settings help text, `href` rejects non-`https://dev.azure.com/` | vitest | `frontend/tests/components/*` |

### Wave 0 Gaps
- [ ] Redact the captured `search-jql`, `changelog-*` and `statuses` responses into committed fixtures (captured 2026-10-12; redact names, emails and account ids)
- [ ] Redact the captured ADO `pr-commits-*` responses and their headers into committed fixtures, plus the invalid-PAT 302 (`Location` and HTML body need no detail beyond status and content type)
- [ ] Add `app.ado.*` / `app.enrichment.*` to BOTH `application.yml` files
- [ ] Update the 17 `new TicketDetail(` call sites when the record gains components

## Security Domain

`security_enforcement: true`, ASVS level 1.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (service credential) | PAT sent only server-side as Basic; Jira calls use the signed-in user's credential only (AUTH-02) |
| V3 Session Management | no change | existing JWT/cookie; SameSite=Strict stays |
| V4 Access Control | yes | New endpoints under `/api/**` authenticated rule; any signed-in user may set the PAT (decided), `updated_by` recorded |
| V5 Input Validation | yes | Bean Validation on the PUT (org regex, token non-blank, `expiresOn` future date); strict parsing of URLs from dev-status |
| V6 Cryptography | yes | `EncryptedTokenConverter` (AES-256-GCM); never log or return the token; non-decrypting writes |
| V12/V13 SSRF / API | yes | Pin host `dev.azure.com`; build URLs from validated `(org, project, repoGuid, prId)`; never fetch payload-supplied URLs |

| Threat | STRIDE | Mitigation |
|--------|--------|------------|
| PAT exfiltration via attacker-controlled URL in Jira data | Information disclosure | Host pin + validated components; send `Authorization` only to `dev.azure.com` |
| PAT in logs / error bodies / ProblemDetail | Information disclosure | Never include in exception messages; redact `Authorization` in any HTTP logging; test that responses never contain the token |
| Stored XSS via commit message/PR title/`url` | Tampering | React text rendering, `href` allow-list `https://dev.azure.com/`, Mustache escaping, truncation |
| Markdown/template injection through commit text into documents | Tampering | Variables stay escaped; templates are untrusted-source with no expression language (existing TMPL-02 constraint) |
| Resource exhaustion (huge PRs, many tickets) | DoS | Page ceiling, commits-per-ticket cap, concurrency cap, overall deadline |
| Key rotation locking users out of Settings | DoS | Non-decrypting write/projection paths (IT with rotated key) |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Global PAT | Org-scoped PAT (or Entra) | Global PATs stop working 2026-12-01 | Settings stores org + expiry; help text says select one organization |
| Jira `/rest/api/*/search` | `/search/jql` with `nextPageToken` | already 410 | existing adapter; terminate on token absence |
| `expand=changelog` | dedicated `/changelog` endpoint | truncates at ~100 | per-issue paginated read |

**Deprecated/outdated:** `RestTemplate`, preview `StructuredTaskScope`, Get Commit per sha (unneeded here).

## Sources

### Primary (HIGH confidence)
- https://learn.microsoft.com/en-us/rest/api/azure/devops/git/pull-request-commits/get-pull-request-commits?view=azure-devops-rest-7.1 - URI, params, `GitCommitRef` fields, `vso.code`
- https://learn.microsoft.com/en-us/rest/api/azure/devops/git/pull-requests/get-pull-request?view=azure-devops-rest-7.1 - PR fields and status enum
- https://learn.microsoft.com/en-us/rest/api/azure/devops/git/repositories/list?view=azure-devops-rest-7.1 - org-level repositories list (PAT validation call)
- https://learn.microsoft.com/en-us/azure/devops/integrate/concepts/rate-limits?view=azure-devops - TSTU, headers, best practices (page dated 2026-07-31)
- https://devblogs.microsoft.com/devops/retirement-of-global-personal-access-tokens-in-azure-devops - 1 Dec 2026 decommission
- https://developer.atlassian.com/cloud/jira/platform/rate-limiting/ - cost model, headers, burst limits
- Repo files read: 06-CONTEXT.md, 02-DEVSTATUS-SPIKE.md, 02-04-SUMMARY.md, 08-CONTEXT.md, REQUIREMENTS.md, PROJECT.md, the three dev-status fixtures, `JiraDevStatusSpikeAdapter`, `JiraRestTicketAdapter`, `ReleaseService`, `ReleaseRepositoryAdapter`, `RenderService`, `RenderContextMapper`, `PlaceholderCatalog(+Test)`, view records, `EncryptedTokenConverter`, `JiraCredentialJpaRepository`, `JiraSiteConfig*`, `ArchitectureBoundaryTest`, `credentialStatus.ts`, `SettingsRoute.tsx`

### Secondary (MEDIUM confidence)
- https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issues/ and https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-workflow-statuses/ - changelog and status endpoints (page too long for full fetch; shape corroborated by community threads)
- https://community.developer.atlassian.com/t/bulk-fetch-changelogs-experimental-api/87240 - bulk changelog is Experimental
- https://learn.microsoft.com/en-us/answers/a/559812 - HTTP 203 for bad credentials/scopes (contradicted by the 2026-10-12 capture, which returned 302)

### Real-tenant capture (HIGH confidence)
- `phase6-capture.sh` run 2026-10-12 against the eVision Jira Cloud tenant (CNV-2896) and the ADO org (PR 3127); raw files in `spike-capture/` (git-excluded). Locks A1, A3, A4, A9.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - no new dependencies; all patterns exist in the repo
- Architecture / persistence / integration points: HIGH - derived from reading the actual code and Phase 8's consumption needs
- External API details: MEDIUM - ADO and rate-limit docs fetched directly; Jira changelog/status shapes and ADO pagination header rely on secondary sources and need fixtures
- Pitfalls: HIGH - most are confirmed by the spike fixtures or by reading the existing code

**Research date:** 2026-10-08
**Valid until:** 2026-11-07 (ADO PAT policy changes on 2026-12-01; re-check before execution if slipping)
