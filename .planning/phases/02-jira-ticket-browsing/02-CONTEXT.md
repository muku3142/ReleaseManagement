---
phase: 02-jira-ticket-browsing
status: context-gathered
gathered: 2026-07-31
requirements: [JIRA-02, JIRA-03, JIRA-04, JIRA-05, JIRA-06]
depends_on: [01-foundation-authentication]
---

# Phase 02 — Jira Ticket Browsing: Context

Decisions gathered from the release manager on 2026-07-31, before planning.
Live tenant available for verification: `https://evisionegypt.atlassian.net`.

## Locked decisions

### D-02-01 — Fix-version-first ticket search

The UI is built around **project → fix version** pickers. That is the primary and default
path: pick a project, pick a fix version, get that version's tickets with checkboxes for
selection.

JQL remains available as an **"Advanced: edit as JQL"** escape hatch, and project/fix-version
filtering is expressed internally as JQL so both paths share one query mechanism rather than
two.

**Why:** matches how releases are actually cut in Jira, requires no JQL knowledge from the
release manager, and gives the shortest path to a working vertical slice. JIRA-02 still
requires all three filter dimensions, so JQL is not dropped — only demoted from primary.

### D-02-02 — Ticket field set: core identity + rich text

Fetch and display:

- **Core identity** — key, summary, issue type, status, resolution, assignee, reporter
- **Description and comments** — the ADF rich-text fields

**Deliberately not in scope for this phase:** components, labels, epic/parent, priority, and
organisation-specific custom fields.

**Fix version is fetched regardless**, because D-02-01's primary filter is built on it — it is
a filter input here, not a display concern.

> **⚠ Known tension with DOC-02, flagged and accepted.** DOC-02 (Phase 5) requires that "a
> template can group by component, epic or label instead" of issue type. None of those three
> fields are being fetched in this phase. This is not a contradiction *yet* — Phase 5 is where
> grouping is implemented — but Phase 5 will have to extend the ticket view-model and the Jira
> field selection to cover them.
>
> **Assumption being carried forward:** adding those fields later is additive (three more
> field names in the same metadata-resolved fetch, plus view-model entries), not a rework, so
> deferring costs little. If that proves wrong, the cost lands in Phase 5. Revisit at
> `/gsd-discuss-phase 5` at the latest.
>
> Custom fields were explicitly not selected, so JIRA-04's field-metadata resolution is built
> and proven against the standard fields above rather than against named custom fields. The
> mechanism is still required — see D-02-03 — just not yet exercised on a custom field.

### D-02-03 — Field metadata resolution still applies

Even with no custom fields selected, JIRA-04's requirement holds: fields are resolved **by
name through field metadata**, never by hardcoded `customfield_NNNNN` ids. The resolution
layer is built now so that adding a custom field later is configuration rather than code.

### D-02-04 — ADF rendering: basic formatting

Flatten ADF to: paragraphs, bold/italic, ordered and unordered lists, links, inline code and
code blocks.

**Out of scope:** tables, panels, media/attachments, status lozenges, mentions, emoji.
Unhandled node types must degrade to their text content rather than being dropped silently or
throwing — an unknown node is expected, not exceptional.

**Why:** covers essentially all real ticket descriptions and survives export to PDF and DOCX
cleanly (DOC-05/DOC-06), without paying for node types that have no clean Word or PDF
equivalent.

### D-02-05 — Azure DevOps for Jira app IS installed

Confirmed installed on the tenant. The Jira→ADO link-discovery spike therefore targets the
**`/rest/dev-status/1.0/issue/detail`** endpoint, which is what the Marketplace app populates
and what drives the Development panel.

Consequences the spike must respect:

- `dev-status` is **undocumented and explicitly unsupported** — Atlassian's own KB states it
  "may change without notice". It must sit behind the provider abstraction, with contract
  tests (WireMock) built from **captured real responses** from this tenant.
- Schema drift is expected, not exceptional. The ADO enrichment path must degrade gracefully,
  which is already a stated project constraint.
- This resolves the open blocker in STATE.md: Phase 6 is scoped around `dev-status`, not
  around parsing work-item keys out of commit messages and branch names.

The spike needs a nominated ticket **with known commits or pull requests** to capture a
realistic response. Outstanding input from the release manager.

## Carried over from Phase 01

- **AUTH-02 becomes real here.** It is currently vacuously true — no Jira ticket reads exist.
  Phase 2 success criterion 1 ("all ticket reads use the signed-in user's own credential") is
  precisely AUTH-02, so this phase closes it. Every Jira read must go through
  `JiraAccessContextService.currentContext()`, which is keyed strictly on the authenticated
  principal and offers no way to obtain another user's credential.
- **The error taxonomy already exists.** JIRA-06 ("clear, actionable error when Jira is
  unreachable, rate-limits, or rejects the credential — distinguishable from 'no results'")
  maps onto the `JiraAuthFailureReason` codes established in Phase 01:
  `JIRA_UNREACHABLE` (503), `JIRA_RATE_LIMITED` (429), `INVALID_CREDENTIALS` (401),
  `JIRA_FORBIDDEN` (403), `JIRA_ERROR` (502). Extend that enum and the `ProblemDetail`
  mapping; do not invent a parallel scheme.
- **Reuse the `RestClient` setup** from `JiraRestClientConfig` — built on
  `JdkClientHttpRequestFactory` with explicit connect (PT5S) and read (PT10S) timeouts,
  because Boot 4.1's `ClientHttpRequestFactorySettings`/`Builder` are not on this classpath.
- **`src/test/resources/application.yml` shadows main's** rather than merging. Any new config
  property must be added to both or full-context ITs fail on unresolved placeholders.

## Hard external constraint

Jira's legacy `/rest/api/*/search` **already returns HTTP 410 Gone**. This phase must be built
against **`/search/jql` with cursor pagination** from day one. Separately,
`expand=changelog` silently truncates at around 100 entries — use the dedicated paginated
`/changelog` endpoint if changelog data is needed.

JIRA-03 ("the complete ticket set matching a filter, with paging handled to exhaustion rather
than silently truncating at the first page") is the requirement that makes cursor pagination
non-optional. Silent truncation is the specific failure mode to test for.

## Inputs supplied (2026-07-31)

- **Project key: `CNV`** — the project releases are assembled from. Use it for all realistic
  verification: fix-version listing, ticket browsing, pagination-to-exhaustion.
- **Spike fixture ticket: `CNV-2896`** — confirmed by the release manager to have an Azure
  DevOps connection.
  `https://evisionegypt.atlassian.net/browse/CNV-2896`

  The link-discovery spike captures this ticket's real
  `/rest/dev-status/1.0/issue/detail` response, redacts identifying values, and stores it as
  the WireMock contract fixture. Capture the **raw response verbatim first** — the point is to
  learn the actual shape, so do not normalise or reshape it before recording what came back.

  Also record, because Phase 6 depends on all three: which `detail` branches are populated
  (repository / branch / commit / pullrequest), the ADO organisation and repository names as
  they appear, and whether commit and PR data arrive in one call or need follow-up requests.
