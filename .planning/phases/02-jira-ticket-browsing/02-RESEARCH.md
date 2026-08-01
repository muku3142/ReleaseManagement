# Phase 02: Jira Ticket Browsing - Research

**Researched:** 2026-07-31
**Domain:** Jira Cloud REST API v3 issue search (cursor pagination), field metadata resolution, ADF flattening, undocumented `dev-status` API, Jira Cloud rate limiting
**Confidence:** MEDIUM-HIGH — the pagination contract (the most expensive thing to get wrong) is cross-verified across official Atlassian docs, an Atlassian-community "how to migrate" article, and multiple independent bug reports that agree on the same shape. The `dev-status` response shape and ADF mark/node list are MEDIUM (undocumented API / training-knowledge-supplemented) and are exactly what the phase's own spike and Wave 0 fixture capture are designed to firm up.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### D-02-01 -- Fix-version-first ticket search

The UI is built around **project -> fix version** pickers. That is the primary and default
path: pick a project, pick a fix version, get that version's tickets with checkboxes for
selection.

JQL remains available as an **"Advanced: edit as JQL"** escape hatch, and project/fix-version
filtering is expressed internally as JQL so both paths share one query mechanism rather than
two.

#### D-02-02 -- Ticket field set: core identity + rich text

Fetch and display: core identity (key, summary, issue type, status, resolution, assignee,
reporter) and description/comments (ADF rich-text fields). Deliberately not in scope:
components, labels, epic/parent, priority, organisation-specific custom fields. Fix version is
fetched regardless, because D-02-01's primary filter is built on it.

Known tension with DOC-02 (Phase 5) flagged and accepted -- grouping by component/epic/label
will require extending the ticket view-model and field selection later; not this phase's
problem to solve.

#### D-02-03 -- Field metadata resolution still applies

Even with no custom fields selected, JIRA-04's requirement holds: fields are resolved **by
name through field metadata**, never by hardcoded `customfield_NNNNN` ids. The resolution
layer is built now so that adding a custom field later is configuration rather than code.

#### D-02-04 -- ADF rendering: basic formatting

Flatten ADF to: paragraphs, bold/italic, ordered and unordered lists, links, inline code and
code blocks. Out of scope: tables, panels, media/attachments, status lozenges, mentions,
emoji. Unhandled node types must degrade to their text content rather than being dropped
silently or throwing.

#### D-02-05 -- Azure DevOps for Jira app IS installed

Confirmed installed on the tenant. The Jira->ADO link-discovery spike targets
`/rest/dev-status/1.0/issue/detail`, which is undocumented and explicitly unsupported by
Atlassian -- must sit behind the provider abstraction, with contract tests built from captured
real responses. Schema drift is expected, not exceptional.

### Carried over from Phase 01

- AUTH-02 becomes real here: every Jira read must go through
  `JiraAccessContextService.currentContext()`.
- The error taxonomy already exists (`JiraAuthFailureReason`): extend it for JIRA-06, do not
  invent a parallel scheme.
- Reuse the `RestClient` setup from `JiraRestClientConfig`.
- `src/test/resources/application.yml` shadows main's -- new config properties must be added to
  both.

### Hard external constraint

Jira's legacy `/rest/api/*/search` already returns HTTP 410 Gone. This phase must be built
against `/search/jql` with cursor pagination from day one. `expand=changelog` silently
truncates at around 100 entries -- use the dedicated paginated `/changelog` endpoint if
changelog data is needed.

### Inputs supplied (2026-07-31)

- **Project key: `CNV`** -- use for all realistic verification.
- **Spike fixture ticket: `CNV-2896`** -- confirmed to have an Azure DevOps connection. Capture
  the raw `/rest/dev-status/1.0/issue/detail` response verbatim first, redact identifying
  values, store as the WireMock contract fixture. Also record which `detail` branches are
  populated (repository/branch/commit/pullrequest), the ADO org/repo names as they appear, and
  whether commit/PR data arrives in one call or needs follow-up requests.

### Claude's Discretion

Not explicitly separated into a distinct section in 02-CONTEXT.md; treat any implementation
detail not covered by a locked decision above (e.g. exact DTO shapes, caching strategy for
field metadata, package/class naming) as open to standard-practice judgement, consistent with
the Standard Stack and Architecture Patterns sections below.

### Deferred Ideas (OUT OF SCOPE)

- Advance-expiry warning for Jira API tokens (carried from Phase 01, not re-opened here).
- Grouping tickets by component/epic/label (DOC-02, Phase 5) -- explicitly flagged as a known
  tension, not solved in this phase.
- Any Phase 6 ADO enrichment beyond the link-discovery spike capture itself.
</user_constraints>

## Summary

This phase replaces Jira's fully-retired `/rest/api/2|3/search` (410 Gone) with `/rest/api/3/search/jql`, which trades offset pagination (`startAt`/`total`) for cursor pagination (`nextPageToken`). The critical, well-documented finding is that **the new endpoint's response contains no `total` field at all** — Atlassian's own answer is a separate `POST /rest/api/3/search/approximate-count` call, not a field on the search response. A second critical finding, load-bearing for JIRA-03's "must not silently truncate" requirement: multiple independent community bug reports (2026) describe `isLast` never flipping to `true` and `nextPageToken` chaining without terminating in certain conditions. The safe termination signal is **the absence of `nextPageToken` in the response**, not `isLast` — and the pagination loop must additionally carry a hard safety cap (max pages / max total issues) so a token-chaining bug degrades to a bounded, loud error instead of an infinite loop, which is the mirror-image failure mode to the truncation JIRA-03 already worries about.

Field metadata resolution (JIRA-04) is straightforward: `GET /rest/api/3/field` returns every system and custom field with `id`, `key`, `name`, and `schema` in one unpaginated call; there is no dedicated by-name lookup endpoint, so the adapter must fetch the full list (it is small, effectively static per site) and build a name→id map itself. Because Jira allows two different custom fields to share a display name, exact-name lookup is inherently ambiguous for custom fields — not an issue for this phase's D-02-02 field set (all are system fields with stable, unique names), but the resolver's contract must define what happens on a collision (fail loud, don't silently pick one) since Phase 5 will exercise it against real custom fields.

Project/fix-version browsing (D-02-01's primary UX) is well-trodden, offset-paginated (not cursor-paginated) API surface untouched by the `/search` deprecation: `GET /rest/api/3/project/search` for permission-scoped projects, `GET /rest/api/3/project/{key}/versions` for that project's full (unpaginated) version list. ADF flattening (JIRA-05) has no actively-maintained, general-purpose Java ADF→text/HTML renderer — Atlassian's own official Java library (`adf-builder-java`) is a *builder*, not a parser/renderer, and is not even published to Maven Central. A small, bounded hand-rolled recursive walker over Jackson's `JsonNode` tree is the correct call here, consistent with the project's no-over-engineering rule, not a shortcut around a "should have used a library" gap.

The `dev-status` spike target is `GET /rest/dev-status/1.0/issue/detail?issueId={numericId}&applicationType={type}&dataType={repository|pullrequest|branch}` — it needs Jira's internal numeric issue ID (obtained by first calling `/rest/api/3/issue/{key}` and reading `id`), not the issue key. This is genuinely undocumented and Atlassian says so explicitly; treat every field in its response as provisional until the phase's own captured fixture confirms it.

**Primary recommendation:** Build the whole ticket-read surface on `POST /rest/api/3/search/jql` (POST, not GET, to avoid URL-length limits once the advanced-JQL escape hatch is in play), loop strictly on "does the response contain a `nextPageToken`", cap the loop with an explicit max-pages/max-issues ceiling that surfaces as a clear `JIRA_ERROR`-class failure rather than hanging, use `POST /rest/api/3/search/approximate-count` only for an advisory "about N tickets" UI hint (never for pagination termination), resolve every field name (system and custom alike) through one cached `GET /rest/api/3/field` lookup, and hand-roll a bounded ADF-to-text walker rather than adopting a builder library that solves the wrong problem.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Project / fix-version picker UI, JQL escape-hatch editor, checkbox selection state | Browser / Client | API / Backend | Pure client-side rendering and selection-set bookkeeping over a ticket list the backend already returned in full |
| JQL composition from project+fixVersion picks | API / Backend | — | The backend owns Jira wire semantics (JQL clause escaping, field-id substitution); the client only ever sends structured filter values, never raw JQL for the primary path |
| `/search/jql` cursor pagination to exhaustion | API / Backend | — | Must run entirely server-side: a browser cannot safely hold a Jira credential or retry across an unbounded number of upstream pages inside one HTTP request/response cycle expected by the SPA |
| Field name → field id resolution (JIRA-04) | API / Backend | — | Purely a Jira-wire-format concern; the frontend view-model already expects fields by stable domain name, never by `customfield_NNNNN` |
| ADF → readable text flattening (JIRA-05) | API / Backend | — | Untrusted, Jira-controlled JSON tree; flattening happens once, server-side, so both the browse UI and (later) document generation consume the same rendered text, never re-deriving it independently |
| `dev-status` spike / fixture capture | API / Backend | — | Same reasoning as the auth adapter: the credential and the outbound call must stay server-side |
| Jira error taxonomy → ProblemDetail (JIRA-06) | API / Backend | Browser / Client | Backend classifies the failure into the existing `JiraAuthFailureReason`-style taxonomy; the frontend only needs to branch on the `reason` string, already the established pattern from Phase 1 |

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| JIRA-02 | Browse candidate tickets filtered by project, fix version, and JQL | `/rest/api/3/project/search`, `/rest/api/3/project/{key}/versions`, and `/search/jql` sections below; D-02-01 makes project+fixVersion the primary path, both expressed as JQL internally |
| JIRA-03 | Complete ticket set, paging handled to exhaustion, no silent truncation | `/search/jql` Pagination Contract section — termination-on-absent-token + safety-cap design directly targets this |
| JIRA-04 | Fields resolved by name via field metadata, not hardcoded ids | `GET /rest/api/3/field` section — name→id resolution service design |
| JIRA-05 | ADF rendered as readable text | ADF Flattening section — node/mark inventory, hand-roll recommendation, unknown-node fallback rule |
| JIRA-06 | Clear, actionable error distinguishable from "no results" | Rate Limiting and Common Pitfalls sections — 429/`Retry-After` handling, extending the existing `JiraAuthFailureReason` taxonomy |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Backend: Java 25, Spring Boot 4.1.0, Spring MVC (blocking, virtual threads on) — no WebFlux.
- Outbound HTTP via Spring's `RestClient` only, never `RestTemplate` — reuse `JiraRestClientConfig`'s `JdkClientHttpRequestFactory` pattern, do not introduce `ClientHttpRequestFactorySettings`/`Builder` (confirmed absent from this classpath in Phase 1).
- Hexagonal layering is mechanically enforced by ArchUnit: `domain/`/`ports/` have zero framework imports; `application/` must not depend on `adapters.jira`/`adapters.persistence`/`adapters.web` (exemption for `adapters.security` only, already in place — does not apply to this phase).
- Ticket-source access must go through the existing `JiraAuthPort`-style provider interface pattern — this phase adds ticket-read ports (e.g. `JiraTicketPort`, `JiraFieldMetadataPort`), it does not bypass the pattern established in Phase 1.
- No over-engineering / no speculative abstraction — do not build a generic multi-provider ticket abstraction beyond what a single interface requires; do not add a caching layer beyond what field-metadata staleness genuinely requires.
- Validate only at system boundaries — the Jira API response is the boundary; internal service calls trust already-validated data.
- Error taxonomy: extend `JiraAuthFailureReason` (or an equivalent ticket-read-specific enum reusing the same `ProblemDetail`+`reason` mechanism) rather than inventing a parallel error shape.
- `src/test/resources/application.yml` shadows main's — any new `app.jira.*` config property (e.g. cache TTL, page-size ceiling) must be added to both.
- Comments only where the WHY is non-obvious — e.g., the `dev-status` numeric-id lookup and the "loop on absent token, not `isLast`" choice are exactly the kind of non-obvious constraint worth one comment each; do not narrate what the code already says.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `RestClient` | Boot-managed (Spring Framework 7) | Outbound Jira Cloud HTTP | Already established in `JiraRestClientConfig` (Phase 1); this phase adds calls, not a new client [VERIFIED: codebase, `01-03-SUMMARY.md`] |
| Jackson (`tools.jackson.*`, Boot-managed) | Boot-managed (Jackson 3 line) | JSON parsing of search results and raw ADF trees | Already the project's JSON stack; ADF flattening walks a generic `JsonNode` tree rather than deserializing to a typed hierarchy, because ADF's node/mark polymorphism is deep and only a subset (D-02-04) is actually rendered [VERIFIED: `01-03-SUMMARY.md` notes `Jackson 3 (tools.jackson.*)` is Boot 4.1's managed databind] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.wiremock:wiremock-standalone` | 3.13.2 (already pinned) | Stub `/search/jql`, `/field`, `/project/search`, `/project/{key}/versions`, `/dev-status/1.0/issue/detail` responses | All new Jira adapters; reuse the exact WireMock setup pattern from Phase 1's `JiraRestAuthAdapterTest` |
| Caffeine or a plain in-memory `Map` + manual TTL | N/A (JDK-only for the simple case) | Cache the `GET /rest/api/3/field` name→id map | Field metadata rarely changes; a bare `ConcurrentHashMap` populated on first use and refreshed only on a resolution miss is enough for one JVM, single-tenant, low-traffic tool — do not add Caffeine/Redis for this (no over-engineering) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-rolled bounded ADF walker | `adf-builder-java` (official Atlassian) | It is a document *builder* (constructs ADF to send), not a parser/renderer of received ADF to text — wrong direction entirely, and it is not published to Maven Central (hosted on Atlassian's own Bitbucket-linked repo), adding supply-chain friction for a feature it doesn't provide [VERIFIED: developer.atlassian.com/cloud/jira/platform/apis/document/libs/; Maven Central search returned zero `com.atlassian.adf` artifacts this session] |
| Hand-rolled bounded ADF walker | Third-party community ADF parsers (Python `atlas_doc_parser`, JS `simple-adf-formatter`) | No comparably-maintained Java equivalent surfaced in this research; introducing a Python/JS-ecosystem library into a Java backend isn't an option, and a one-off community Java jar carries unknown maintenance risk versus a ~150-line bounded walker the team fully owns |
| Loop-until-no-`nextPageToken` | Loop-until-`isLast: true` | Multiple 2026 community bug reports describe `isLast` never becoming `true` on this endpoint even at the actual last page — using it as the sole termination signal risks exactly the infinite-loop failure mode this research flags [MEDIUM confidence, cross-corroborated across 3 independent community threads] |
| `POST /search/jql` for every query | `GET /search/jql` | GET is fine for short, simple JQL (Atlassian's own migration doc shows both) but the advanced-JQL escape hatch (D-02-01) has no length bound from the release manager, and URL length limits are a real, silent failure mode; POST has no such ceiling |

**Installation:** No new Maven dependencies required — this phase is additive Java code plus WireMock fixtures against the existing `wiremock-standalone` test dependency.

**Version verification:** No new library versions to pin; reusing Phase 1's `wiremock-standalone:3.13.2` and Boot-managed Jackson. [VERIFIED: `01-RESEARCH.md`, `01-03-SUMMARY.md`]

## Architecture Patterns

### System Architecture Diagram

```
Browser (React SPA)
  │ GET /api/tickets?projectKey=CNV&fixVersionId=...   (primary path)
  │ GET /api/tickets?jql=...                            (advanced escape hatch)
  ▼
adapters/web/TicketController
  │  DTOs only (TicketSummaryResponse, list of selectable tickets)
  ▼
application/TicketSearchService
  │  1. resolve JiraAccessContext (AUTH-02, unchanged mechanism)
  │  2. if project+fixVersion path: compose JQL from validated project key + version id
  │  3. resolve requested field names -> field ids via FieldMetadataService (cached)
  │  4. delegate to JiraTicketPort.searchAll(jql, fieldIds)
  ▼
ports/JiraTicketPort            ports/JiraFieldMetadataPort         ports/JiraProjectPort
  │                                    │                                  │
  ▼                                    ▼                                  ▼
adapters/jira/JiraRestTicketAdapter   adapters/jira/JiraFieldMetadataAdapter   adapters/jira/JiraProjectAdapter
  │  POST /rest/api/3/search/jql        │  GET /rest/api/3/field               │  GET /rest/api/3/project/search
  │  loop: no nextPageToken => done     │  (cached name->id map)               │  GET /rest/api/3/project/{key}/versions
  │  optional: POST .../approximate-count (advisory total only)
  ▼
RestClient (JiraRestClientConfig, existing) ──► Jira Cloud REST v3
  │
  ▼ (per ticket, ADF fields only: description, comments)
adapters/jira/AdfFlattener  (pure function: JsonNode -> plain text / minimal HTML)
```

A reader can trace JIRA-02/03 end to end: the browser sends a project+fixVersion or raw-JQL request → the service composes/validates JQL → the ticket port pages Jira to exhaustion using the cursor contract below → field ids are substituted for names before the call, and ADF fields in each returned issue are flattened before the response DTO is built → the browser receives the complete set in one response, with checkboxes over an already-fully-loaded list (see the Pagination + Selection UX pattern below).

### Recommended Package Additions

```
src/main/java/com/evision/releasemgmt/
├── domain/jira/
│   ├── TicketSummary.java            # record: key, summary, issueType, status, resolution, assignee, reporter, fixVersions
│   ├── TicketDetail.java             # TicketSummary + flattened description/comments
│   ├── JiraField.java                # record: id, name  (resolved metadata entry)
│   └── JiraTicketReadException.java  # extends existing failure-taxonomy pattern
├── ports/
│   ├── JiraTicketPort.java           # searchAll(String jql, List<String> fieldIds) -> List<TicketDetail>, with a page-count/issue-count ceiling parameter
│   ├── JiraFieldMetadataPort.java    # resolve(List<String> fieldNames) -> Map<String,String> name->id
│   └── JiraProjectPort.java          # listProjects(), listVersions(String projectKey)
├── application/
│   ├── TicketSearchService.java
│   └── FieldMetadataService.java     # owns the cached name->id map; the only caller of JiraFieldMetadataPort
├── adapters/jira/
│   ├── JiraRestTicketAdapter.java    # /search/jql pagination loop + approximate-count
│   ├── JiraFieldMetadataAdapter.java # /field
│   ├── JiraProjectAdapter.java       # /project/search, /project/{key}/versions
│   ├── AdfFlattener.java             # pure function, no Spring dependency, unit-testable standalone
│   └── devstatus/                    # spike-only: JiraDevStatusSpikeAdapter + captured WireMock fixture
└── adapters/web/
    └── TicketController.java
```

### Pattern 1: Cursor pagination to exhaustion (JIRA-03)

**What:** Loop while the response body contains a `nextPageToken` field; stop when it is absent. Never terminate on `isLast` alone. Enforce an explicit ceiling (e.g. max 100 pages, or a configurable `app.jira.search.max-results-ceiling`) that throws a distinct, loud failure — never a silent truncation and never an infinite loop.

**When to use:** Every call to `/rest/api/3/search/jql`, for both the project+fixVersion primary path and the advanced-JQL escape hatch.

**Example:**
```java
// adapters/jira/JiraRestTicketAdapter.java
List<TicketDetail> searchAll(String jql, List<String> fieldIds) {
    List<TicketDetail> results = new ArrayList<>();
    String pageToken = null;
    int pagesFetched = 0;
    do {
        JqlSearchResponse page = restClient.post()
            .uri("/rest/api/3/search/jql")
            .body(new JqlSearchRequest(jql, fieldIds, MAX_RESULTS_PER_PAGE, pageToken))
            .retrieve()
            .body(JqlSearchResponse.class);
        results.addAll(page.issues().stream().map(this::toTicketDetail).toList());
        pageToken = page.nextPageToken(); // null/absent => last page, NOT page.isLast()
        if (++pagesFetched > maxPagesCeiling) {
            throw new JiraTicketReadException(JIRA_ERROR); // loud, bounded failure — never hang
        }
    } while (pageToken != null);
    return results;
}
// JqlSearchResponse fields per this session's research: issues, nextPageToken (nullable),
// isLast (present but NOT trusted as the sole termination signal — see Common Pitfalls)
```
[CITED: confluence.atlassian.com/jirakb/run-jql-search-query-using-jira-cloud-rest-api — GET/POST request shape, `nextPageToken` replaces `startAt`]
[MEDIUM: community.atlassian.com/forums/Jira-articles/How-to-use-the-new-Jira-cloud-issue-search-API — loop pattern: `while "nextPageToken" in data`]

**Trade-offs:** No `total` is available mid-loop; if a "Fetching N of ~M" progress indicator is wanted, issue one extra `POST /rest/api/3/search/approximate-count` call up front purely for that advisory number — never use it to decide when to stop paging (it is explicitly "approximate").

### Pattern 2: Field name → field id resolution (JIRA-04)

**What:** One `GET /rest/api/3/field` call returns every system and custom field with `id`/`key`/`name`/`schema`. Build a `Map<String,String>` from (lower-cased, trimmed) display name to field id, cached for the process lifetime with a manual refresh path (not a TTL-based library) since field configuration changes are rare, administrative, out-of-band events.

**When to use:** Before every `/search/jql` call, translate the phase's fixed field-name list (`summary`, `status`, `resolution`, `assignee`, `reporter`, `issuetype`, `description`, `comment`, `fixVersions` — plus whatever Phase 5 adds later) into field ids. For this phase's D-02-02 set, every name is a stable Jira **system** field whose id equals its name (e.g. `"summary"` → `"summary"`), so the mechanism is exercised without yet hitting the ambiguity case.

**Example:**
```java
// application/FieldMetadataService.java
Map<String, String> resolve(List<String> displayNames) {
    Map<String, String> allFields = cachedFieldMap(); // name(lowercased) -> id, refreshed on miss
    Map<String, String> resolved = new LinkedHashMap<>();
    for (String name : displayNames) {
        String id = allFields.get(name.toLowerCase(Locale.ROOT));
        if (id == null) {
            allFields = refreshFieldMap(); // one retry: metadata may have just changed
            id = allFields.get(name.toLowerCase(Locale.ROOT));
        }
        if (id == null) throw new JiraFieldNotFoundException(name); // fail loud, never silently skip a requested field
        resolved.put(name, id);
    }
    return resolved;
}
```
[VERIFIED (partial): `GET /rest/api/3/field` returns id/key/name/schema/custom per multiple cross-corroborated community sources this session — MEDIUM confidence, not fetched verbatim from the OpenAPI page due to fetch truncation; re-confirm exact JSON field names against a real WireMock capture during Wave 0, same discipline as Phase 1's `/myself` fixture]

**Trade-offs:** A duplicate display name across two custom fields is a real, documented Jira possibility (Atlassian's own JRASERVER-62343 backlog item exists precisely because names are not guaranteed unique) — this phase's field set has no such collision, but the resolver must define its collision behavior now (throw, don't guess) so Phase 5 doesn't inherit a silent wrong-field bug.

### Pattern 3: Project and fix-version listing (D-02-01 primary path)

**What:** `GET /rest/api/3/project/search` (offset-paginated, `startAt`/`maxResults`/`total` — this endpoint was **not** part of the `/search` deprecation) for the projects the signed-in user can see; `GET /rest/api/3/project/{projectIdOrKey}/versions` for that project's full, unpaginated version list (id, name, released, archived, releaseDate).

**When to use:** Populating the project picker and, once a project is chosen, the fix-version picker.

**Example:**
```java
// adapters/jira/JiraProjectAdapter.java
List<JiraProjectSummary> listProjects() {
    // GET /rest/api/3/project/search?maxResults=50 — loop on startAt+total (classic offset pagination,
    // untouched by the /search/jql migration — do not apply the cursor pattern here)
}
List<JiraVersion> listVersions(String projectKey) {
    // GET /rest/api/3/project/{projectKey}/versions — single call, full list, no pagination
}
```
[MEDIUM: developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-projects/ and api-group-project-versions/ — endpoint existence and shape cross-corroborated via WebSearch this session; exact JSON field names not fetched verbatim due to page-fetch truncation, recommend confirming against a live CNV call during Wave 0]

### Pattern 4: Pagination + selection UX given cursor pagination forbids page-jumping (research question 7)

**What:** Because `/search/jql` cannot jump to page N, the backend pages to exhaustion **once**, server-side, and returns the complete matching ticket set in a single API response to the frontend. The frontend then does client-side pagination/virtualization and checkbox selection over an already-fully-loaded array — there is no server round-trip per page once the initial fetch completes.

**When to use:** D-02-01's "pick a project, pick a fix version, get that version's tickets with checkboxes for selection" — fix-version-scoped ticket counts are bounded (typically tens to low hundreds), making full server-side pagination-to-exhaustion practical. For the advanced-JQL escape hatch, the same approach applies but is bounded by the safety ceiling in Pattern 1 — a JQL query matching far more issues than the ceiling should surface a clear "narrow your query" error, not attempt infinite scrolling or random-access paging that cursor pagination cannot support anyway.

**Trade-offs:** This pushes latency into one (potentially multi-page-fetching) backend call rather than spreading it across many small frontend-triggered page loads — acceptable because release ticket sets are the target scale (a single fix version's tickets), not Jira-instance-wide result sets. If a future need arose for truly huge result sets, virtualized infinite-scroll re-fetching in JQL-token-order would be the fallback — not attempted here, no requirement calls for it.

### Anti-Patterns to Avoid

- **Using `isLast` as the sole pagination termination condition.** Multiple 2026 community bug reports describe it never becoming `true`; combine "absence of `nextPageToken`" as the authoritative signal and treat `isLast` as informational only.
- **Assuming the requested `maxResults` is what comes back.** Jira may return fewer issues per page than requested when many fields are requested — always use the actual length of the returned `issues` array, never assume a fixed page size when computing progress or ceilings.
- **Building JQL by raw string concatenation of user-supplied project/version display names.** Compose the primary-path JQL from **ids** (`project = 10023` / `fixVersion = 10891`) resolved server-side from the picker selections, not from raw display-name string interpolation — sidesteps JQL quoting/escaping bugs entirely (see Security Domain below).
- **Treating `/rest/api/3/search/approximate-count` as authoritative for loop termination.** It is explicitly approximate; use it only for an advisory "~N tickets" UI hint fetched once, never inside the pagination loop.
- **Hardcoding `customfield_NNNNN` ids anywhere**, even for a "just this once" system-field shortcut — the whole point of JIRA-04/D-02-03 is that every field, including this phase's system fields, goes through the same name-resolution path.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT/session/crypto | (unchanged from Phase 1) | (unchanged from Phase 1) | Not touched by this phase |
| Cursor pagination retry/backoff scaffolding | A generic "pageable Jira client" abstraction layer | The concrete loop in Pattern 1, parameterized only by JQL + field ids | One well-tested loop covers every call this phase needs (tickets); a generic abstraction has no second consumer yet and would be speculative per the no-over-engineering rule |
| ADF rendering | A hand-rolled Markdown-flavored transformer, or adopting a heavyweight rich-text library | The bounded recursive `JsonNode` walker in the ADF Flattening section below | ADF's schema is large; only D-02-04's small subset needs handling, and no maintained Java library targets exactly "ADF in, plain text/minimal-HTML out" |
| Rate-limit backoff | A custom retry-storm-prone `while(true)` retry loop | A single bounded retry (or none — see Rate Limiting section) surfaced as `JIRA_RATE_LIMITED` immediately | JIRA-06 asks for a clear, actionable error, not silent client-side retrying that could itself contribute to the rate-limit problem it's reacting to |

**Key insight:** every "don't hand-roll" item in Phase 1 was a security-primitive concern (crypto, JWT, CSRF). This phase's hand-roll risk is different in kind — it's about *not over-building generic abstractions* for what is, in truth, one Jira instance and five or six call shapes. The one place a hand-rolled implementation genuinely is the correct choice (ADF flattening) is correct specifically because no adequate off-the-shelf alternative exists, not because "just write it" was the path of least resistance.

## Common Pitfalls

### Pitfall 1: Treating `/search/jql`'s `total` as available (it is not)

**What goes wrong:** Code that expects a `total` field on the search response (carried over habit from the deprecated `/search` endpoint) gets a silent `null`/missing-field deserialization issue, or a naive `NullPointerException`.
**Why it happens:** Every pre-2025 Jira integration example uses the old endpoint's `total` field; muscle memory carries over.
**How to avoid:** Do not model `total` on the response DTO at all. If an advisory count is genuinely needed for UI, call `POST /rest/api/3/search/approximate-count` once, separately, and label it as approximate in the UI copy.
**Warning signs:** A DTO field for `total` that is always null in tests; a progress bar that never reaches 100% because it was computed against a value that was never populated.
**Confidence:** HIGH — confirmed by the official Atlassian KB migration article and cross-corroborated by multiple community threads. [CITED: support.atlassian.com/jira/kb/find-the-total-number-of-work-items-in-jira-cloud-using-jql-or-issue-search/]

### Pitfall 2: `isLast` never becoming `true`

**What goes wrong:** A loop written as `while (!response.isLast())` can spin far longer than expected, or forever, against certain JQL/field combinations.
**Why it happens:** Community-reported behavior on this specific endpoint (multiple independent 2026 threads); root cause not disclosed by Atlassian.
**How to avoid:** Loop on "does `nextPageToken` appear in the response body", per Pattern 1. Add the safety-ceiling regardless, since even the "correct" signal is an externally-controlled API contract this project doesn't own.
**Warning signs:** A ticket-browsing request that never completes, or completes only after fetching far more pages than the known ticket count would suggest.
**Confidence:** MEDIUM — community-sourced across 3 independent threads, not an Atlassian-acknowledged bug in official docs; the safety-ceiling mitigation is recommended regardless of whether this specific bug is present in this tenant.

### Pitfall 3: `expand=changelog` silent truncation (already a locked project constraint)

**What goes wrong:** Requesting changelog data via `expand=changelog` on an issue/search call truncates at roughly 100 entries with no error.
**Why it happens:** `expand`-based nested data is not paginated the way the endpoint's primary results are.
**How to avoid:** This phase does not need changelog data (no requirement calls for it), but if a later phase does, use the dedicated `GET /rest/api/3/issue/{issueIdOrKey}/changelog` endpoint, which is itself `nextPageToken`-paginated with a genuinely reliable `isLast` per Atlassian's own docs for that specific endpoint (unlike the search endpoint) — don't assume the search-endpoint pitfall above applies identically here without re-checking.
**Confidence:** Locked project constraint per CONTEXT.md/CLAUDE.md — treat as given, not re-derived here.

### Pitfall 4: Ambiguous field-name resolution silently picking the wrong custom field

**What goes wrong:** Two custom fields share a display name (a real, Atlassian-acknowledged Jira behavior); a naive `Map<name,id>` build silently keeps whichever one iterates last, and the wrong field's data appears in the UI with no error.
**Why it happens:** Display names are not a Jira uniqueness constraint, only `id`s are.
**How to avoid:** Not exercised by this phase's field set (all unique system fields), but the resolver must be written to detect and fail loudly on a genuine collision now — Phase 5 will add the first real custom field, and retrofitting collision-safety after a silent-wrong-field bug has shipped is a worse position than building it correctly from this phase.
**Confidence:** MEDIUM — Jira's own backlog item (JRASERVER-62343) confirms names aren't unique; no verified example of this tenant (CNV / evisionegypt.atlassian.net) actually having a collision, so this is a defensive design requirement, not an observed live issue.

### Pitfall 5: `dev-status`'s numeric-id requirement being missed

**What goes wrong:** Calling `/rest/dev-status/1.0/issue/detail?issueId=CNV-2896` (the issue *key*) instead of the numeric internal id returns an empty/error response that looks like "no ADO link" rather than "wrong parameter."
**Why it happens:** Every other Jira Cloud v3 endpoint in this project accepts the issue key interchangeably with the numeric id; `dev-status` does not.
**How to avoid:** Resolve the numeric id first via `GET /rest/api/3/issue/{key}` (reading the response's `id` field) before calling `dev-status`, and make this two-call sequence explicit in the spike adapter rather than assuming the key works.
**Confidence:** MEDIUM — consistently reported across multiple community sources this session; must be confirmed directly against CNV-2896 during the spike itself, which is exactly what the spike is for.

## Code Examples

### ADF flattening: bounded recursive walker (JIRA-05 / D-02-04)

```java
// adapters/jira/AdfFlattener.java — pure function, no Spring dependency, unit-testable in isolation
public final class AdfFlattener {

    public String flatten(JsonNode adfDoc) {
        StringBuilder out = new StringBuilder();
        walk(adfDoc, out, 0);
        return out.toString();
    }

    private void walk(JsonNode node, StringBuilder out, int listDepth) {
        String type = node.path("type").asText("");
        switch (type) {
            case "doc", "paragraph" -> { walkChildren(node, out, listDepth); out.append('\n'); }
            case "text" -> out.append(applyMarks(node));
            case "bulletList", "orderedList" -> walkChildren(node, out, listDepth + 1);
            case "listItem" -> { out.append("  ".repeat(listDepth)).append("- "); walkChildren(node, out, listDepth); }
            case "codeBlock" -> { out.append("```\n"); walkChildren(node, out, listDepth); out.append("```\n"); }
            default -> {
                // Unknown node type (table, panel, media, mention, emoji, status — all explicitly
                // out of scope per D-02-04): degrade to its own text content, never throw, never drop.
                if (node.has("text")) out.append(node.path("text").asText());
                walkChildren(node, out, listDepth);
            }
        }
    }

    private void walkChildren(JsonNode node, StringBuilder out, int listDepth) {
        for (JsonNode child : node.path("content")) walk(child, out, listDepth);
    }

    private String applyMarks(JsonNode textNode) {
        String text = textNode.path("text").asText("");
        for (JsonNode mark : textNode.path("marks")) {
            text = switch (mark.path("type").asText("")) {
                case "strong" -> "**" + text + "**";
                case "em" -> "*" + text + "*";
                case "code" -> "`" + text + "`";
                case "link" -> text + " (" + mark.path("attrs").path("href").asText("") + ")";
                default -> text; // unknown mark: keep text content, drop only the unrecognised decoration
            };
        }
        return text;
    }
}
```
[MEDIUM: developer.atlassian.com/cloud/jira/platform/apis/document/structure/ — `doc`/`paragraph`/`text`/marks (`strong`/`em`/`code`/`link`/`strike`/`underline`/`subsup`/`textColor`) confirmed; `bulletList`/`orderedList`/`listItem`/`codeBlock` node names confirmed by the same fetch, but no explicit worked JSON example was returned for the list/codeBlock nodes in this session's fetch — verify exact attribute shapes (e.g. `codeBlock`'s `attrs.language`) against a real captured description during Wave 0]

Bound execution/collection size the same way TMPL-05 will require for templates later (a max node count / max recursion depth guard) — cheap insurance against a pathological ADF tree, consistent with the project's general "no unbounded traversal of untrusted structures" posture even though ADF here comes from Jira, not directly from an end user.

### Rate limiting (JIRA-06)

```java
// adapters/jira/JiraRestTicketAdapter.java (error mapping, same pattern as JiraRestAuthAdapter)
catch (HttpClientErrorException.TooManyRequests e) {
    // Retry-After header is present per Atlassian's rate-limiting docs; surface it, don't
    // auto-retry in a loop here — JIRA-06 asks for a clear error, not silent backoff-and-hope.
    throw new JiraTicketReadException(JIRA_RATE_LIMITED);
}
```
[CITED: developer.atlassian.com/cloud/jira/platform/rate-limiting/ — `Retry-After`, `X-RateLimit-Limit/Remaining/Reset`, `RateLimit-Reason` headers on 429]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `/rest/api/2\|3/search` with `startAt`/`total` | `/rest/api/3/search/jql` with `nextPageToken`, no `total` | Legacy endpoint fully removed (410 Gone), confirmed live on this project's tenant | Every existing Jira-integration tutorial predating this change models the wrong pagination contract — do not copy `startAt`-based examples |
| `expand=changelog` on issue/search calls | Dedicated paginated `/rest/api/3/issue/{key}/changelog` | Ongoing, `expand=changelog` truncation is a known, longstanding limitation | Locked project constraint; not exercised by this phase's requirements |

**Deprecated/outdated:** Any Jira client library or tutorial still calling `/rest/api/2/search` or `/rest/api/3/search` will 410 against this tenant — a strong signal to discard rather than adapt any such reference material found during implementation.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `GET /rest/api/3/field` response shape is `[{id, key, name, custom, orderable, navigable, searchable, clauseNames, schema}, ...]` | Pattern 2 | Low — a WireMock fixture captured against the real CNV tenant during Wave 0 (same discipline as Phase 1's `/myself` capture) will correct any field-name mismatch before the resolver ships |
| A2 | `GET /rest/api/3/project/{key}/versions` returns `{id, name, released, archived, releaseDate}` per version, unpaginated | Pattern 3 | Low — same Wave 0 capture-and-correct discipline; a paginated fallback (`/project/{key}/version`, singular) exists if the unpaginated form proves insufficient |
| A3 | `POST /rest/api/3/search/jql`'s JSON response includes `issues`, `nextPageToken` (nullable/absent on last page), `isLast`, `names`, `schema` | Pattern 1 | Medium — if `nextPageToken` is present-but-empty-string rather than absent/null on the last page, the loop condition needs a one-line adjustment (`isBlank()` check) discoverable in the first WireMock/live test run |
| A4 | `dev-status`'s `applicationType`/`dataType` query parameters and the exact response tree (repository/branch/commit/pullrequest sub-objects) match the general shape reported across community sources | Common Pitfalls #5, Code Examples | Medium — this is explicitly what the phase's own spike against CNV-2896 exists to confirm; do not build the Phase 6-facing contract test until the spike's captured fixture is in hand |
| A5 | ADF `codeBlock`'s language attribute and exact `bulletList`/`orderedList` nesting shape match the general schema described in Atlassian's structure docs | ADF Flattening code example | Low — D-02-04's scope is small and forgiving (unknown nodes degrade to text content by design), so a shape mismatch surfaces as slightly-off formatting, not a crash or data loss |

## Open Questions

1. **Does this specific Jira tenant (evisionegypt.atlassian.net) actually exhibit the `isLast`-never-true bug, or was that other tenants' misconfiguration?**
   - What we know: multiple independent community reports across 2026 describe the symptom; no confirmed root cause from Atlassian.
   - What's unclear: whether it's universal to the endpoint or conditional (e.g., specific `fields`/`expand` combinations).
   - Recommendation: the "loop on absent `nextPageToken`, not `isLast`" design in Pattern 1 is correct regardless of whether this tenant exhibits the bug — it costs nothing to be defensive here, so this doesn't block planning.

2. **Exact numeric-id lookup cost for `dev-status`.**
   - What we know: `dev-status` needs the numeric issue id, obtainable via `GET /rest/api/3/issue/{key}`.
   - What's unclear: whether that extra call should be cached/batched when Phase 6 later enriches many tickets in one release (ADO-05's "bounded time... parallel, cached lookups" requirement lives in Phase 6, not here).
   - Recommendation: this phase's spike only needs to prove the mechanism against one ticket (CNV-2896); defer the batching/caching design to Phase 6 planning, which already inherits this open dependency per STATE.md.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Jira Cloud tenant (`evisionegypt.atlassian.net`) | Real-instance verification of `/search/jql`, `/field`, `/project/*`, `/dev-status` | Yes — confirmed live, project key `CNV`, spike ticket `CNV-2896` | — | WireMock stubs cover automated tests; live calls needed only for fixture capture and UAT, same pattern as Phase 1 |
| Azure DevOps for Jira Marketplace app | `dev-status` spike having real repository/PR data to capture | Yes — confirmed installed on this tenant (D-02-05) | — | — |
| `org.wiremock:wiremock-standalone` | Contract tests for all new Jira adapters | Yes — already a project dependency (3.13.2) | 3.13.2 | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None — all dependencies for this phase are already present from Phase 1's setup.

## Security Domain

ASVS Level 1 enforcement is on (`security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: "high"` per config).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V4 Access Control | Yes | Every Jira read goes through the existing `JiraAccessContextService.currentContext()` — this phase adds callers, never a new credential path. AUTH-02 becomes load-bearing (no longer vacuous) here. |
| V5 Input Validation | Yes | Advanced-JQL escape hatch is user-supplied text forwarded to Jira as a query — it cannot execute anything locally (Jira's own parser rejects malformed JQL), but the primary-path JQL composed from project/fixVersion picks must use Jira **ids**, not raw display-name string interpolation, to avoid quote-breaking/JQL-clause-injection bugs that could let a crafted fix-version name widen the effective query beyond the picker's intended scope |
| V7 Error Handling / Logging | Yes | Extend the existing `ProblemDetail`+`reason` pattern; never echo a raw upstream Jira error body (which could contain the caller's own JQL/ADF content) verbatim into a response, consistent with the "catch-all never leaks internals" rule already established |
| V12 (implicit) Untrusted-data traversal bounds | Yes (project-specific, maps loosely to ASVS resource-consumption controls) | The ADF flattener must bound recursion depth/node count — the same posture TMPL-05 will require for templates, applied here because ADF is Jira-controlled but still a nested structure of arbitrary depth in principle |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| JQL clause injection via unescaped project/version display names composed into internally-generated JQL | Tampering | Compose JQL from Jira **ids** resolved server-side from validated picker selections, never from raw display-name string concatenation |
| Cursor-pagination infinite loop turning into a resource-exhaustion vector (a single browse request pinning a thread/virtual-thread indefinitely against a misbehaving upstream) | Denial of Service | Hard page/issue-count ceiling in the pagination loop (Pattern 1), surfaced as a clear error rather than left unbounded |
| `dev-status`/ADF response schema drift silently producing wrong or truncated data rather than an error | Tampering (data integrity) | WireMock contract tests built from captured real responses (already the plan per D-02-05); unknown ADF nodes degrade to text content rather than being silently dropped, so drift is visible as "odd formatting" rather than invisible data loss |
| Jira rate-limit (429) responses triggering an unbounded client-side retry storm that itself worsens throttling | Denial of Service (self-inflicted) | Surface `JIRA_RATE_LIMITED` immediately per JIRA-06's own wording ("clear, actionable error") rather than auto-retrying in a loop |

## Sources

### Primary (HIGH confidence)
- https://developer.atlassian.com/cloud/jira/platform/rate-limiting/ — 429 headers (`Retry-After`, `X-RateLimit-*`, `RateLimit-Reason`) and recommended client backoff behavior
- https://confluence.atlassian.com/jirakb/run-jql-search-query-using-jira-cloud-rest-api-1289424308.html — official migration guidance, GET/POST request shapes, `nextPageToken` replacing `startAt`
- https://support.atlassian.com/jira/kb/find-the-total-number-of-work-items-in-jira-cloud-using-jql-or-issue-search/ — `/search/jql` has no `total`; use `POST /search/approximate-count` instead
- https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/ — ADF `doc`/`paragraph`/`text`/mark/list/codeBlock node names
- https://developer.atlassian.com/cloud/jira/platform/apis/document/libs/ — official ADF client libraries are builders, not text/HTML renderers
- Codebase: `01-03-SUMMARY.md`, `01-06-SUMMARY.md`, `docs/architecture.md` — existing `JiraAuthPort`/`JiraAccessContextService`/`ProblemDetail` patterns this phase extends

### Secondary (MEDIUM confidence)
- https://community.atlassian.com/forums/Jira-articles/How-to-use-the-new-Jira-cloud-issue-search-API/ba-p/3006109 — worked pagination loop example (`while "nextPageToken" in data`)
- https://community.atlassian.com/forums/Jira-questions/REST-The-new-rest-api-3-search-jql-endpoint-is-a-complete/qaq-p/3101716 and https://github.com/atlassian/atlassian-mcp-server/issues/118 — cross-corroborated `isLast`-never-true bug reports
- https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-projects/ and .../api-group-project-versions/ — `/project/search` and `/project/{key}/versions` existence/shape (page-fetch truncated; not read verbatim)
- https://support.atlassian.com/jira/kb/how-to-retrieve-pull-request-and-bitbucket-repo-and-branch-information-from-an-issue-using-rest-api/ and community threads on `/rest/dev-status/1.0/issue/detail` — numeric-id requirement, `applicationType`/`dataType` params
- https://jira.atlassian.com/browse/JRASERVER-62343 — confirms Jira custom field display names are not guaranteed unique

### Tertiary (LOW confidence)
- General community commentary on `/search/jql` maxResults ceilings (up to 5000, fewer if many fields requested) — directionally useful for the safety-ceiling design but not verified against this tenant's actual behavior; treat the exact number as a starting configuration value, not a hard fact

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; reuses Phase 1's verified `RestClient`/Jackson/WireMock setup
- Pagination contract (JIRA-03, the highest-cost-if-wrong item): MEDIUM-HIGH — the "no `total`", "use `approximate-count` separately", and "`nextPageToken` replaces `startAt`" facts are HIGH (official docs); the `isLast` reliability finding is MEDIUM (community-sourced but consistent across independent reports) and the mitigation (loop on absent token + safety ceiling) is robust regardless of which is true
- Field metadata / project-versions shape: MEDIUM — endpoint existence is HIGH confidence, exact JSON field names are best-effort from cross-corroborated search (official doc pages returned truncated on fetch); flagged for Wave 0 fixture-capture confirmation, same discipline Phase 1 used for `/myself`
- ADF flattening: MEDIUM — node/mark names confirmed against official structure docs; exact attribute shapes for list nesting and code blocks not returned verbatim, but D-02-04's unknown-node-degrades-to-text rule makes this low-risk even if slightly off
- `dev-status` spike: MEDIUM — genuinely undocumented per Atlassian's own admission; this research narrows the mechanism (numeric id, `applicationType`/`dataType` params) but the phase's own captured fixture from CNV-2896 is the actual source of truth, not this document

**Research date:** 2026-07-31
**Valid until:** 30 days for the pagination/field-metadata contract (stable, documented-if-quirky API surface); re-verify before Phase 5 if the field-collision path is actually exercised against a real custom field then, since that's genuinely untested territory today.
