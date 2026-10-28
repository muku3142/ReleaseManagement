# Phase 12: ReleaseManagement as First Client - Research

**Researched:** 2026-10-15
**Domain:** Main app (`src/main/java/com/evision/releasemgmt`) as the first caller of `rag-service`: post-commit indexing, backfill sweep, read-only tools, tool-loop orchestrator, ArchUnit isolation
**Confidence:** HIGH for the code mapping and rag-service behaviour (read from the repo), MEDIUM for the Phase 11 wire shapes (Phase 11 is 3/9 executed; shapes come from `11-RESEARCH.md`, not from a published `openapi.json`)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- The RAG client adapter sits behind a port in the main app's hexagon. rag-service is reached only through its published `/v1` API with ReleaseManagement's own client API key (Phase 9 D-09-01). The browser never holds that key (ASST-02, Phase 13).
- Index only what the screens show, and only the latest revision of each document type. Answers are shared as in v1, with no per-user Jira filtering. The assistant must never claim to honour per-user Jira permissions. Store `project_key` in metadata now, for possible later filtering.
- Tools call existing application services in-process, never controllers or repositories. They are read-only, with no Jira, Azure DevOps, credential or index-writer dependency, enforced by ArchUnit with a positive case so the rules are not vacuous (ROADMAP success criterion 5).
- The tool loop follows Phase 11's protocol: stateless resend with the signed tool-call turn, at most 4 rounds, tool failures returned as error results, 413 for oversized results (D-11-01..10).
- An `rm.assistant.enabled` switch (D-12-09).
- **D-12-01: One collection per source type:** `rm-tickets`, `rm-documents`, `rm-manual-items`, `rm-code-links`. Every item carries `release_id`/`project_key`/status metadata. A question can search all four (Phase 10 multi-collection, D-10-05). Each source can be re-indexed or dropped on its own. Phase 13's "Ask about this release" becomes a `release_id` filter.
- **D-12-02: Stable natural document ids.** These are `ticket:{key}` (one per ticket, shared across releases), `document:{releaseId}:{type}` (the latest revision, replaced on regeneration), `manual:{itemId}` and `codelinks:{ticketKey}`. A re-push replaces in place, and deletes are exact (ROADMAP success criterion 3). **Research must solve:** rag-service metadata is flat strings and numbers with no arrays (D-09-07), so a ticket in several releases cannot carry a list of release ids. Propose an encoding that keeps `release_id` filtering working without changing rag-service, if possible.
- **D-12-03: Seconds, best effort.** Push asynchronously right after the originating transaction commits (refresh, add or remove ticket, manual item edit, document generation). It must never run inside, or block, the user's transaction (ROADMAP research flag).
- **D-12-04: A failed push is dropped and the sweep heals it.** A push that fails or times out is logged as metadata only and forgotten. There is no outbox table, no retry machinery, and no Flyway migration for this.
- **D-12-05: The sweep runs at startup and every 30 minutes, configurable.** It compares and re-pushes anything missing or stale, and deletes what was removed. It is cheap, because unchanged content is a no-op in rag-service through its content hash. It runs only when the switch is on.
- **D-12-06: Tools return structured fields only:** stage, dates, counts, statuses, keys, titles, readiness flags and deployments. They never return free text such as descriptions, comments or document bodies. Free text reaches the model only through fenced retrieval.
- **D-12-07: The full proposed set, about 10 coarse flat tools:** `list_releases`, `get_release`, `get_ticket`, `get_promotion_history`, `get_ladder`, `get_dashboard_summary`, `list_recent_deployments`, `get_ticket_churn`, `get_document_readiness`, `list_document_revisions`. Names and argument schemas may be refined in research, but not expanded beyond this scope.
- **D-12-08: All releases are indexed,** including frozen, shipped and rolled-back ones, with their status in metadata.
- **D-12-09: `rm.assistant.enabled` defaults to OFF.** Nothing is pushed and no tools are exposed until it is turned on. It is turned on on this machine for Phase 12's live check.
- **D-12-10: Ticket comments only as the screens show them.** Research checks whether restricted-visibility Jira comments are stored today, and excludes anything the screens don't show.

### Claude's Discretion
- The RAG client adapter's shape: a port in `ports/`, a `RestClient` adapter with explicit timeouts, and configuration (`rm.assistant.*`: base URL, API key, collection names, sweep interval, timeouts) in both `application.yml` files, `compose.app.yaml` and `.env.example`.
- Where the tool loop lives: an application-layer orchestrator that calls rag-service `/v1/chat`, executes tools, and resends. It must record which tools ran and which sources were used, for Phase 13's "how I answered" panel (ASST-06), without building any UI.
- Exact tool argument and return schemas, the text formatting of pushed items (title plus body; ADF already flattened), and the metadata keys.
- How the after-commit hook is wired, and how the sweep is scheduled. Do not let a new scheduler or executor bean replace Boot's defaults (Phase 10, 10-01).
- How collections are created on first use (`PUT /v1/collections/{c}` with `nomic-embed-text`) when the switch turns on.

### Deferred Ideas (OUT OF SCOPE)
- A durable outbox for failed pushes (D-12-04), only if the sweep proves too slow to heal.
- Per-user Jira project filtering on `project_key`: deferred, the data is stored now.
- Tool results with free-text excerpts (D-12-06).
- The per-document AI prompts with implementation notes and commits: Phase 14.
- Changes to `rag-service` itself (Phase boundary).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ASST-01 | Ticket text, manual items, latest generated documents and code-link text are pushed and kept current, without slowing v1 actions or breaking them when the service is down | Per-release document ids (Finding 1), adapter-level AFTER_COMMIT event (Finding 2), coalescing worker + sweep with in-memory diff (Finding 3), switch-off wiring (Finding 8) |
| ASST-03 | Exact facts come from ReleaseManagement's own read-only tools, which it runs | Tool-to-service map (Finding 5), orchestrator over the Phase 11 protocol (Finding 6) |
| ASST-04 | Tools are read-only, enforced by architecture tests | Allowlist ArchUnit rules with a non-vacuity case (Finding 7) |
</phase_requirements>

## Summary

The main app has no service-level transactions: every `@Transactional` sits on the persistence adapters (`ReleaseRepositoryAdapter`, `DocumentSnapshotRepositoryAdapter`, ...), and `ReleaseService`/`DocumentGenerationService` are plain beans that call several adapter methods, each committing on its own [VERIFIED: grep `@Transactional` in `src/main`]. So "after commit" can only be implemented correctly by publishing the event from inside the adapter's transactional method and handling it with `@TransactionalEventListener(AFTER_COMMIT)`. A service-level publish would not be inside any transaction, and a `@TransactionalEventListener` would silently drop it. One handler that only enqueues to a private virtual-thread executor keeps the user's thread free; no `TaskExecutor`/`TaskScheduler` bean is needed (house precedent: `EnrichmentService` and `RenderService` own a plain `Executors.newVirtualThreadPerTaskExecutor()` field).

Three findings change the CONTEXT as written and need a user decision (see "Decisions needed from the user"): (1) the ticket and code-link document ids must include the release id, because each release stores its own ticket snapshot (`release_ticket_item` is per release, a ticket may be in several releases), so `ticket:{key}` cannot be both shared and correct; (2) Phase 11 limits a request to **5 tools** and **1,500 characters** of definitions in total (shipped in `rag-service/src/main/resources/application.yml`), so ten tools do not fit unless the ten capabilities are consolidated into 5 tools or Phase 11's limits are raised; (3) rag-service has **no list-documents endpoint**, so the sweep can re-push everything idempotently and delete what it knows it pushed, but it cannot find orphans left by a dropped delete across an app restart without a small rag-service addition.

Restricted-visibility Jira comments: the app stores every comment body Jira returned, flattened to a string, with the visibility object discarded, and both the ticket browser and the release detail screen show them all [VERIFIED: `JiraRestTicketAdapter.java:149-154`, `ReleaseDetailRoute.tsx:510-515`]. "As the screens show them" therefore means all stored comments, and a restricted one cannot be told apart. Recommended: index them (same exposure as v1 screens, consistent with "shared as in v1") and record that as an accepted, user-confirmed risk.

**Primary recommendation:** Publish a `ReleaseIndexInvalidated(releaseId)` event from the persistence adapters inside their transactions; handle it AFTER_COMMIT by enqueueing a coalesced per-release sync on a private virtual-thread executor under one lock; rebuild the release's documents from the DB on each sync and the sweep, re-push everything idempotently, and delete ids that were pushed earlier but are no longer desired; build the five (or ten) tools as classes implementing one `AssistantTool` interface that call a fixed allowlist of read methods, enforced by ArchUnit.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Change detection (post-commit signal) | Database / Storage adapter (`adapters.persistence`) | Application (listener) | Transactions exist only here; AFTER_COMMIT needs a transaction-bound publish |
| Desired-index computation (release to documents) | Application (`application.assistant.index`) | Domain (records) | Pure mapping from `Release`/`DocumentSnapshot`; no IO |
| Push, delete, collection create | Adapter (`adapters.rag`) behind `RagIndexPort` | - | Only the adapter knows `RestClient` and the `/v1` shapes |
| Sweep scheduling | Application (`@Scheduled`) | Config (`@EnableScheduling` only) | Same pattern as rag-service `IndexWorker` |
| Tool definitions and execution | Application (`application.assistant.tools`) | - | In-process calls to existing services; free of ports/adapters |
| Tool loop | Application (`AssistantService`) | Adapter (`RagChatAdapter`, SSE) | Orchestration is domain-aware, wire parsing is not |
| Read-only and isolation enforcement | Test (`ArchitectureBoundaryTest`) | - | Build fails on violation |
| API key custody | Config / env | - | Key in `.env`, never in a response or log |

## Decisions needed from the user (found by research, not in CONTEXT)

| # | Decision | Recommendation | If declined |
|---|----------|----------------|-------------|
| U1 | **Document ids for tickets and code links** change from `ticket:{key}` / `codelinks:{ticketKey}` to `ticket:{releaseId}:{key}` / `codelinks:{releaseId}:{ticketKey}` (D-12-02 amended). | Accept. Each release holds its own ticket snapshot, so one shared document cannot be right for a frozen and an open release. It also gives a flat scalar `release_id` per document, so the equality filter works with no rag-service change. | A single shared `ticket:{key}` must pick one release's snapshot and store only one `release_id`, so a ticket in two releases is invisible to "this release" filtering for all but one. |
| U2 | **Tool count vs Phase 11 limits.** rag-service accepts at most 5 tools and 1,500 characters of definitions together (`rag.tools.max-tools: 5`, `definitions-max-chars: 1500`; descriptions at most 300 chars; flat schemas of at most 8 properties) on a 4096-token context. | Consolidate the ten D-12-07 capabilities into **5 tools** with optional arguments (Finding 5). No rag-service change, and fewer definitions help a 4B model. | Raise the two limits in rag-service config (the yml has no env override for either) and re-measure; about +500 prompt tokens per round for 1,500 more characters. Touches Phase 11 config after 11-01. |
| U3 | **Sweep cannot discover orphans across restarts** (no list endpoint). | Accept the in-memory diff and a drift WARN log in this phase. Orphans arise only when a DELETE was dropped (rag-service down at that moment) and the app restarted before the next sweep. | Add `GET /v1/collections/{c}/documents` (ids, paged) to rag-service in a small rag-service plan; the sweep then diffs exactly. |
| U4 | **Restricted-visibility comments** cannot be identified (visibility is discarded at ingest). | Index stored comments as the screens show them; record as accepted. | Exclude comments from ticket text entirely, or change the Jira adapter and add a column (a migration; D-12-04 says none). |
| U5 | **Promotion `note` and `correctionReason`** are free text, so D-12-06 excludes them from tool results; they are also not indexed (not in ASST-01). | Exclude; return `hasNote` / `corrected` booleans. | Return notes (injection channel, larger results). |
| U6 | **Config prefix.** CONTEXT locks `rm.assistant.*`; every existing key is `app.*` (env vars `RM_*`). | Keep `rm.assistant.*` as locked; note the mismatch in the plan. | Rename to `app.assistant.*`. |

## Finding 1: release_id encoding (D-12-02)

**Facts.** rag-service metadata is a flat object of strings and numbers, at most 50 keys, keys `[A-Za-z0-9_.-]{1,64}`, string values at most 1,000 chars. Filters are equality or an `in` list per key, ANDed, and match by Postgres jsonb containment `d.metadata @> {"key": value}` OR-ed over the list, type-sensitive (string `"3"` does not match number `3`) [VERIFIED: `MetadataRules.java`, `ChunkSearchRepositoryAdapter.java:44-46`]. An upsert always replaces metadata, but the content hash covers only title and text, so a metadata-only change (for example `release_status`) does not trigger re-embedding [VERIFIED: `DocumentRepositoryAdapter.upsert`, `ContentHash.of(title,text)`].

A ticket can sit in several releases (`release_ticket_item_key_unique (release_id, ticket_key)`, STATE roadmap evolution 05.2) and every release stores its own snapshot (`release_ticket_item` is per release; refresh overwrites only that release's rows) [VERIFIED: `V3__release_assembly.sql`, `ReleaseRepository.replaceItemSnapshots`].

| Option | `release_id` filter | Exact deletes | Cost | Verdict |
|--------|--------------------|---------------|------|---------|
| A. Per-release ticket documents `ticket:{releaseId}:{key}` | scalar equality, works | removing a ticket from release R deletes exactly its one document | text duplicated per release a ticket is in (typically 1-3) | **Recommended** |
| B. One shared `ticket:{key}` with a delimited `release_ids` string | containment cannot do substring match; filter fails | unclear when the last release drops it | - | Rejected |
| C. Shared document carrying only one release's id | wrong for all other releases | ok | - | Rejected |
| D. Add array containment to rag-service | works | ok | rag-service change, out of scope | Rejected for this phase |

A also makes the index truthful: a frozen release's snapshot stays as frozen, an open release's snapshot follows its refreshes. Apply the same to code links (stored per release item: `release_item_commit`, `release_item_pull_request`). Manual items and documents are already release-scoped.

**Ids** (pattern `[A-Za-z0-9._:-]{1,200}`; a UUID of 36 chars plus a Jira key fits) [VERIFIED: `openapi.json` document path pattern]:
`ticket:{releaseId}:{ticketKey}`, `codelinks:{releaseId}:{ticketKey}`, `manual:{itemId}` (the item id is already globally unique, keep as locked), `document:{releaseId}:{TYPE}` (types `RELEASE_NOTES|RUNBOOK|APPROVAL`).

**Metadata keys** (flat; omit a key instead of sending null):

| Key | tickets | code-links | manual-items | documents |
|-----|:---:|:---:|:---:|:---:|
| `release_id` (string UUID) | x | x | x | x |
| `release_name`, `release_version` | x | x | x | x |
| `release_status` (`OPEN`/`FROZEN`) | x | x | x | x |
| `project_key` | key prefix of the ticket | key prefix | `Release.jiraProjectKey` if set | `Release.jiraProjectKey` if set |
| `ticket_key` | x | x | | |
| `ticket_status`, `issue_type` | x | | | |
| `category` | | | x | |
| `doc_type`, `revision` (number), `generated_at` (ISO string) | | | | x |

`release_status` is the only mutable key; `freeze` therefore invalidates the whole release (it does, because freeze goes through `ReleaseRepositoryAdapter`). Stage and shipped state are deliberately not in metadata: they change on promotions and come from tools (ASST-03). Phase 13 chips link by `release_id`, `ticket_key`, `doc_type` plus `revision`.

**Text per source** (title plus body, plain text or Markdown; the service chunks, 1 MB cap, NUL rejected [VERIFIED: `DocumentService.java:44-47`]):
- tickets: title `KEY summary`; body lines for type, status, resolution, assignee, reporter, fix versions, component, story points, description, then comments as stored. Optional `url` = `{jiraSiteBaseUrl}/browse/KEY` from `JiraSiteConfigService` (a DB read, no Jira call; must match `https?://\S{1,2040}`; omit if unset).
- documents: the snapshot `markdown` as is (post-Mustache Markdown, see the `DocumentSnapshot` Javadoc), title `{Type label} - {name} {version} (revision N)`.
- manual items: title, description, category.
- code links: PR title/status/branches and commit first-line messages with short sha and author, built from `CodeLinks` (the release page shows all PRs and commits). Skip and delete the document when there are none.
- A 413 for an oversized document is logged and dropped.

## Finding 2: after-commit push path (D-12-03)

**Where each trigger happens today** [VERIFIED: read of `ReleaseService`, `DocumentGenerationService`, `ReleaseRepositoryAdapter`, `DocumentSnapshotRepositoryAdapter`]:

| Trigger | Service path | Committing adapter method |
|---------|--------------|---------------------------|
| Create release with tickets | `ReleaseService.createWithTickets` | `ReleaseRepositoryAdapter.create` (+ later `saveEnrichment`) |
| Add tickets | `addTickets` | `addItems` (+ `saveEnrichment`) |
| Remove ticket | `removeTicket` | `removeItem` |
| Refresh | `refresh` | `replaceItemSnapshots` (+ `saveEnrichment`) |
| Refresh code links | `refreshCodeLinks` | `saveEnrichment` |
| Manual item add, edit, remove | `addManualItem`, `updateManualItem`, `removeManualItem` | same-named adapter methods |
| Freeze | `freeze` | `freeze` |
| Delete release | `delete` | `delete` |
| Document generation | `DocumentGenerationService.generate` | `DocumentSnapshotRepositoryAdapter.create` |

None of the services is `@Transactional`; a service call is several independent commits, with Jira HTTP calls (`fetchTickets`, `enrichQuietly`) between them. `spring.jpa.open-in-view: false`, so no request-wide transaction exists either [VERIFIED: `application.yml`].

**Recommended wiring.** No service constructor changes, so `ReleaseServiceTicketsTest`, `ReleaseServiceManualItemsTest` and `DocumentGenerationServiceTest`, which call `new ...Service(...)`, are untouched.

1. Domain record `ReleaseIndexInvalidated(UUID releaseId)` (no framework types).
2. `ReleaseRepositoryAdapter` and `DocumentSnapshotRepositoryAdapter` take `ApplicationEventPublisher` (no test calls `new ReleaseRepositoryAdapter(`, they are only built by Spring [VERIFIED: grep]) and call `publisher.publishEvent(new ReleaseIndexInvalidated(releaseId))` as the last statement of each mutating `@Transactional` method (about ten call sites). Publish on no-op calls too; the sync is idempotent.
3. `IndexInvalidationListener` (application, `@ConditionalOnProperty` on `rm.assistant.enabled=true`) with `@TransactionalEventListener(phase = AFTER_COMMIT)`: `if (queued.add(releaseId)) executor.execute(() -> { queued.remove(releaseId); syncRelease(releaseId); });`. The handler body is two collection operations, so although AFTER_COMMIT handlers run on the committing thread it costs microseconds and never waits on HTTP.
4. `executor` is a private field `Executors.newVirtualThreadPerTaskExecutor()` (precedent `EnrichmentService.java:66`, `RenderService.java:42`); no bean. `syncRelease` takes one shared `ReentrantLock` for the read-DB-then-push unit, so an event push and a sweep push of the same release cannot interleave (an older read would otherwise overwrite a newer push; last write wins on the server, D-09-11).
5. Coalescing by the `queued` set turns the burst of events from one refresh (`replaceItemSnapshots` plus per-batch `saveEnrichment`) into at most one queued sync per release.

**Why not the alternatives.** Publishing from the service plus a plain `@EventListener` runs on whatever thread publishes and must be hand-placed in ten service methods. `@Async` needs `@EnableAsync` and relies on Boot's `applicationTaskExecutor`; it works, but the private executor has precedent and needs no config. A `@TransactionalEventListener` on a service-published event is silently dropped without `fallbackExecution = true`, because the service holds no transaction.

**Proof it never runs inside the user's transaction** (tests to plan):
- IT (Testcontainers Postgres, existing pattern): call a mutating adapter method inside `TransactionTemplate`; assert the listener has not run before commit; assert it ran after commit on another thread where `TransactionSynchronizationManager.isActualTransactionActive()` is false; assert it did not run on rollback.
- Blocking test: with a `RagIndexPort` fake that blocks forever, `ReleaseService.refresh` (Jira stubbed) returns promptly with an unchanged result.
- Table test: every mutating method of both adapters publishes exactly one event (guards a future method without `@Transactional`, where AFTER_COMMIT would drop the event).
- Failure test: the fake port throws or times out; the v1 call still succeeds and only a metadata log line appears (D-12-04).
- Switch-off test: with `rm.assistant.enabled=false` no `RagIndexPort`, listener, sweep or `AssistantService` bean exists (`getBeanNamesForType` is empty) and mutations still work.

## Finding 3: sweep (D-12-05)

**rag-service surface** [VERIFIED: `openapi.json`, `DocumentController`, `DocumentService`]: `PUT/GET/DELETE /v1/collections/{c}/documents/{id}`, `GET /v1/collections/{c}` (counts `pending/indexed/failed`, `lastIndexedAt`), `PUT /v1/collections/{c}` (idempotent for the same model, 409 for a different one; body optional, default model `nomic-embed-text`), `POST .../search`, `POST .../reindex`. There is no list-documents endpoint and no delete-collection. `PUT` document returns 202; `DELETE` of an absent id returns 404 (treat as success). Search `k` is at most 50 and `query` at most 2,000 chars, so search cannot enumerate documents either.

**Authoritative desired set (DB):** `ReleaseRepository.search("")` (summaries), then `findById` per release (items, manual items, code links), plus one `DocumentSnapshotRepository.findLatestForAllReleases()` for the latest revision per (release, type). This is the data the screens read, with no Jira or ADO call. The N+1 on `findById` is acceptable for a 30-minute job at a few hundred releases; do not optimise (CLAUDE.md).

**Design (consistent with D-12-04: no outbox, no migration, no rag-service change):**
1. `IndexSyncer.sync(releaseId)` computes the desired documents (pure mapper, Finding 1), PUTs every one, and records the pushed ids in an in-memory `Map<UUID, Set<DocRef(collection,id)>>`.
2. For ids recorded but no longer desired (removed ticket, removed manual item, emptied code links): DELETE (404 counts as done), then drop from the set. A dropped DELETE stays in the set and is retried at the next sync of that release or the next sweep, so the sweep heals dropped deletes within the process lifetime.
3. A deleted release (`findById` empty): DELETE everything recorded for it.
4. The sweep is `@Scheduled(initialDelayString, fixedDelayString)` calling `sync` for every release (each under the lock), after `ensureCollections()` (four idempotent PUTs, so it also recovers a rag-service that lost its data). `@EnableScheduling` goes in one small config class, exactly as rag-service `SchedulingConfig`, and no scheduler or executor bean is defined [VERIFIED: rag-service `SchedulingConfig`, and the `GenerationConfig` comment on why].
5. Do not keep a local content-hash cache: skipping unchanged pushes would stop the sweep from healing a rag-service that lost documents. An unchanged re-push costs one HTTP call; embedding is skipped server side by the hash.
6. Drift detection (cheap, optional): after a sweep compare the desired count per collection with `GET /v1/collections/{c}` `indexed+pending+failed`; log a WARN with the two numbers (no ids) when remote exceeds desired. This surfaces the residual orphan case of U3.

**Residual risk (accepted unless U3 is taken):** an orphan survives only if a DELETE was dropped because rag-service was unreachable and the app restarted before the next sweep re-ran with the id still recorded. A ticket removed in the first seconds after startup, before the startup sweep has recorded ids, has the same shape. The exact fix is U3.

## Finding 4: restricted-visibility comments (D-12-10)

- `JiraRestTicketAdapter` maps `fields.comment.comments[].body` through `AdfFlattener` into `List<String> comments` and keeps `comment.total` as `commentTotal`; the comment's `visibility` (role or group restriction) and author are never read [VERIFIED: `JiraRestTicketAdapter.java:149-154`, grep `visibility` in `src/main` and `src/test` finds nothing relevant].
- The strings are stored in `release_ticket_item.comments` (JSON list) with `comment_total` [VERIFIED: `V3__release_assembly.sql:32-33`, `ReleaseTicketItemEntity`], rendered by the ticket browser and the release detail screen, which show "showing N of commentTotal" and every stored comment, and exposed to templates as `{{#comments}}` [VERIFIED: `TicketBrowser.tsx:421-429`, `ReleaseDetailRoute.tsx:510-515`, `ReleaseViewAssembler.java:100`].
- Jira returns only comments the fetching user may see, and each user fetches with their own token (`JiraAccessContext`). So a comment restricted to a group the fetching user belongs to is stored and then shown to every release manager of this app, today, in v1. Whether a stored comment was restricted in Jira cannot be told from the data.
- **Consequence:** indexing "as the screens show them" means indexing all stored comments; excluding restricted ones is impossible without a Jira adapter change plus a new column (a migration). Raise as U4. The recommended default keeps comments and records the risk in the Security section (T-12-04). Comments are the largest injection channel in ticket text; they are fenced by rag-service like all passages and never returned by tools (D-12-06).

## Finding 5: tools (D-12-06/07)

### Mapping of the ten capabilities to existing service methods

| Capability (D-12-07) | Wraps | Structured fields returned (no free text) | Gap |
|----------------------|-------|-------------------------------------------|-----|
| `list_releases` | `ReleaseService.search(query)` returns `ReleaseSummary` | id, name, version, status, project key, createdAt, frozenAt, lastRefreshedAt, itemCount; capped list (for example 25) with `truncated` flag | none |
| `get_release` | `ReleaseService.get(id)` + `PromotionService.history(id)` (`currentStage()`, `nextStage()`, `firstProductionSuccess()`) | status, dates, ticket count, manual item count, tickets by status category, current and next stage name, shippedAt, lastRefreshedAt, itemsChangedAt | none |
| `get_ticket` | `ReleaseService.get(releaseId).items()` (`TicketDetail`, `CodeLinks`, `StatusHistory`) | key, summary (a title), issueType, status, statusCategory, assignee and reporter display names, fixVersions, component, storyPoints, commentTotal, updatedAt, enrichment status, PR count, commit count, firstDoneAt, reopenCount, historyState | **Finding: no service method finds the releases that contain a ticket key.** Add `ReleaseService.releasesContaining(String ticketKey)` over a new `ReleaseRepository` read method and one `ReleaseTicketItemJpaRepository.findByTicketKey` (read only, no migration; `release_ticket_item` already indexes the key through its unique constraint). Without it the tool needs a release argument. |
| `get_promotion_history` | `PromotionService.history(releaseId)` (`PromotionHistory`: entries, stages, effective rule via `isEffective`) | per entry: stage name, outcome, deployedAt, recordedAt, recordedBy display name (via `ActorResolutionService.resolve`), `corrected`, `superseded`; **not** `note`, `correctionReason` (U5) | none |
| `get_ladder` | `EnvironmentLadderService.ladder()` | stage name, position, production flag, retired flag | none |
| `get_dashboard_summary` | `DashboardService.releases(all)` returns `DashboardView` | summary (visible and total releases, median created-to-frozen and frozen-to-production in seconds with sample counts, releases per stage name, not-promoted count) plus up to N release rows (name, version, status, current stage name, shippedAt, composition, story points) | none |
| `list_recent_deployments` | `DashboardService.deployments(stageId, from, to, limit, offset)` returns `DeploymentPage`; stage name resolved through `EnvironmentLadderService.ladder()`; `from`/`to` from the injected `Clock` | release name and version, stage name, outcome, deployedAt, recordedBy display name, `corrected`, `hasMore`; same bounds as `DashboardController` (limit at most 100, span at most 366 days) | none |
| `get_ticket_churn` | `DashboardService.ticketChurn(releaseId)` returns `List<TicketChurn>` | key, commitsToDone, postDoneCommits, reopens (null means no data, never 0), keyTaggedOnly, outlier; outliers first, capped | none |
| `get_document_readiness` | `DocumentReadinessService.all()` filtered by release (states `NO_TEMPLATE`, `NOT_GENERATED`, `STALE`, `UP_TO_DATE`) | per type state | none (`all()` is the only public aggregate; `state(...)` is a pure comparison) |
| `list_document_revisions` | `DocumentGenerationService.revisions(releaseId, type)` | revision, generatedAt, generatedBy display name, templateName; **not** `markdown` (the service returns whole snapshots, so the tool must map explicitly and a unit test asserts no body text in the JSON) | none |

Release arguments: accept a UUID, or a name/version string resolved with `ReleaseService.search(text)` to an exact match on name, version or `name version`; an ambiguous or unknown value returns an error result listing at most five candidates (names only). One shared `ReleaseRef` helper does this for every tool.

### Consolidation to 5 tools (recommended, U2)

Phase 11 intake rejects more than 5 tools, more than 1,500 characters of definitions in total, descriptions over 300 chars, nested or more than 8 properties, and any schema construct beyond flat `string|integer|number|boolean` with optional `enum` and `maxLength` [VERIFIED: `rag-service/src/main/resources/application.yml:78-85`, `11-AI-SPEC.md:310`]. Ten definitions of useful size do not fit, and `11-AI-SPEC.md` budgets about 580 tokens for 1,500 chars out of 4,096. Consolidating keeps every D-12-07 capability and stays inside the "refine, do not expand" rule:

| Tool | Arguments (all optional strings or integers) | Covers |
|------|----------------------------------------------|--------|
| `releases` | `release` | omitted: list releases; given: stage, dates, counts, status (list_releases, get_release) |
| `tickets` | `release`, `key` | `key`: one ticket with its releases and churn figures; `release` only: its tickets with status and churn, outliers first (get_ticket, get_ticket_churn) |
| `deployments` | `release`, `stage`, `days` | `release`: its promotion history; otherwise recent deployments, optionally per stage (get_promotion_history, list_recent_deployments) |
| `dashboard` | `scope` (`active`/`all`) | summary figures and the ladder stages with per-stage release counts (get_dashboard_summary, get_ladder) |
| `documents` | `release` | per type readiness and revision history; omitted: every release whose documents are not up to date (get_document_readiness, list_document_revisions) |

Constraints for the planner: a unit test serialises the five definitions exactly as the adapter will send them and asserts total length at most 1,500 and each description at most 300 (constants copied from rag-service config with a comment naming the source); every tool returns small structured JSON well under 6,000 characters (rag-service caps 8,000 and 413s beyond; the app replaces an oversized result by `{"error":"result too large, narrow the arguments"}` before sending); list results are capped and carry `truncated`; absent data is JSON `null`, never `0` (INSIGHT-03 rule already in `TicketChurn`). If U2 goes the other way, the same classes split back to ten without other changes.

Tool result `source` (Phase 11 optional field, becomes a TOOL citation): `id` = tool name plus argument key, `title` = for example `ReleaseManagement: release 2.4`, no `url` in this phase.

## Finding 6: tool-loop orchestrator

**Placement.** `application/assistant/AssistantService` (a `@Service` conditional on the switch), depending on `RagChatPort` (new port), the tool registry and `AssistantProperties`. It must not depend on `RagIndexPort`, so the chat path cannot write the index (an ArchUnit rule, Finding 7). Records live in `domain/assistant` (no framework types, as `domain` rules require).

**Protocol (as the caller, per Phase 11 research; shapes marked MEDIUM until 11-06 publishes `openapi.json`):**

```
ask(question, history, collections, filter?, listener):
  turns = []                          // opaque signed turns, never edited
  for attempt in 1..(maxRounds + 1):  // app-side guard; service also caps at 4 rounds
     outcome = chatPort.chat(question, history, collections, filter, tools, turns, listener)
     if outcome is Done:   return Answer(text, citations, refusal, finishReason, toolsRun)
     // outcome is ToolRound(opaqueTurn, calls)
     results = calls.map(execute)     // sequential, in-process, all calls of the round
     turns += (opaqueTurn, results)   // resend the turn UNCHANGED plus results by callId
  return honest failure (service violated its cap)
```

- Stateless resend (D-11-01): every POST carries the original question, history, all earlier signed turns and their results; the signature is returned exactly as received, so the port exposes the turn as an opaque value and only the adapter knows its fields.
- Tokens streamed before a `tool_call` are provisional (11-RESEARCH Pitfall 7): the listener gets `token(String)` and `roundDiscarded()`; the orchestrator calls `roundDiscarded()` when a round ends in a tool call. Phase 13 decides how the UI uses it; Phase 12 only collects.
- Execution of a call: unknown tool or malformed arguments become `{"error":"..."}` (D-11-09), as does any exception (message generic, no stack, no internals); success is a Jackson-serialised object. Calls of one round run sequentially on the calling thread (cheap local reads); their exceptions never escape.
- Errors from the port map to a small outcome enum (OFFLINE, BUSY(429), MODEL_MISSING(503), DEADLINE, FAILED) so Phase 13 can render ASST-08 states without parsing text.
- **Recording for ASST-06, no UI, no storage:** `AssistantAnswer` carries `toolsRun: List<ToolRun(round, name, argumentsSummary, error, durationMillis)>` and `sources: List<Source(n, collection, documentId, title, url, kind PASSAGE|TOOL, metadata)>` copied from the final `citations` event. It is returned to the caller and kept nowhere (no saved conversations, no migration). Logs carry only tool name, round, outcome and duration, never arguments, results, question or answer text (rag-service SAFE-03 precedent).
- Collections searched: all four `rm-*`; `release_id` is an optional filter argument, so Phase 13's "Ask about this release" needs no new code here. Because tools take a `release` argument the model must name, Phase 13 should put the release name into the question text; flag for Phase 13.

**HTTP client.** Two `RestClient` beans in one `config/RagRestClientConfig`, house style of `AdoRestClientConfig` (JDK `HttpClient`, `JdkClientHttpRequestFactory`, explicit connect and read timeouts, redirects not followed): `ragIndexRestClient` (connect 2 s, read 5 s) and `ragChatRestClient` (connect 2 s, read above the per-POST deadline of 120 s, for example 130 s). The `Authorization: Bearer {apiKey}` header is set as a default header on the builder from `rm.assistant.api-key`. SSE is read with `retrieve().body(InputStream)` or `exchange` and a small line parser (`event:` / `data:` / comment heartbeat lines) in the adapter; no new dependency (SSE events are the five named events of the `/v1/chat` contract plus `tool_call`). [ASSUMED: JDK `HttpRequest.timeout` bounds the wait for response headers, not the body, so a streaming read is bounded by rag-service's own deadline and heartbeats; the live plan must confirm that a 60 s generation does not hit the client read timeout.]

## Finding 7: ArchUnit rules (ASST-04)

Existing file: `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` (ArchUnit 1.4.2, `ClassFileImporter` with `DO_NOT_INCLUDE_TESTS`, rules written with `allowEmptyShould(true)`, which is exactly the vacuity risk: a rule on an empty package passes). Findings that shape the rules [VERIFIED: file read]:
- Tools must depend on application services (they are the thing to call), and those services are not read-only classes: `ReleaseService` has `freeze`, `delete`, `refresh`, and it depends on `TicketSearchService` (Jira port) and `EnrichmentService`. `DocumentGenerationService` has `generate`. `PromotionService` has `record`/`correct`. So "does not depend on mutating classes" is wrong; the correct rule is a **method allowlist**, plus a **class allowlist** for what a tool may depend on, so Jira, ADO, credential and index-writer types are out by construction.
- `applicationDoesNotDependOnAdapters` lists `adapters.jira|persistence|web|render`; add `adapters.rag` to it so the application layer cannot reach the new adapter.

Rules to add (select tools by an `AssistantTool` interface, plus a package rule, so a tool in the wrong package is also caught):

1. `toolsCallOnlyAllowlistedReadMethods`: a custom `ArchCondition` over `classes().that().implement(AssistantTool.class)`; for each `JavaMethodCall` whose target owner resides in `..application..` and is not a tool class, require `Owner.method` in an explicit set: `ReleaseService.get|search|releasesContaining`, `PromotionService.history`, `EnvironmentLadderService.ladder`, `DashboardService.releases|deployments|ticketChurn`, `DocumentReadinessService.all`, `DocumentGenerationService.revisions`, `ActorResolutionService.resolve`. Adding a tool call means editing the set, in a reviewable test file.
2. `toolsDependOnlyOnAllowedTypes`: `onlyDependOnClassesThat()` resides in `java..`, `org.slf4j..`, `tools.jackson..` (or whichever Jackson package the app uses, check imports), `..domain..`, the tools package, or is one of the allowlisted service classes above (including `java.time.Clock`). This excludes every `ports..` type (Jira, ADO, credential repositories, `RagIndexPort`, `RagChatPort`), `adapters..`, `config..`, `EnrichmentService`, `TicketSearchService`, `AdoCredentialService`, `JiraAccessContextService`, `AuthenticationService`, and the indexer classes.
3. `toolsHaveNoTransactionOrWriteAnnotations`: tools carry no `@Transactional`. Cheap, optional.
4. `chatPathCannotWriteTheIndex`: `AssistantService` does not depend on `RagIndexPort` or the indexer package; the indexer package does not depend on tools.
5. `ragClientTypesStayInTheRagAdapter`: `RestClient` and `java.net.http` used for rag-service only in `adapters.rag` and `config.RagRestClientConfig` (mirror of the existing render-library rule).

**Positive (non-vacuity) cases, both required:**
- `toolRulesApplyToTheRealTools`: the set `classes().that().implement(AssistantTool.class)` has exactly the expected size (5, or 10 under U2) and equals the registry's tool classes; an empty set fails the test. The rule methods are static helpers taking `JavaClasses`, so the same code runs on the real set and on a fixture.
- `toolRulesRejectViolations`: fixture classes in `src/test/java` (for example `...archfixture.MutatingTool` calling `ReleaseService.freeze`, and `...JiraTool` depending on `JiraTicketPort`) are imported explicitly with `new ClassFileImporter().importClasses(...)` (the main import excludes tests, so they do not pollute it) and the test asserts each rule throws `AssertionError` on them (`assertThatThrownBy(() -> rule.check(fixtureClasses)).isInstanceOf(AssertionError.class)`).

Also keep the existing `*RepositoryExposesNoMutation` style: add `RagIndexPort` ownership as the only place with `put`/`delete` index methods, and a `RagChatPort` with no write method names.

## Finding 8: config, compose, client registration

**Keys** (`rm.assistant.*`, a `@ConfigurationProperties("rm.assistant")` record `AssistantProperties` registered by the conditional config; add to **both** `src/main/resources/application.yml` and `src/test/resources/application.yml`, which shadows main's [VERIFIED: CONTEXT landmines]; the yml defaults must let the context start with the switch off and no secrets):

| Key | Default | Env var | Purpose |
|-----|---------|---------|---------|
| `enabled` | `false` | `RM_ASSISTANT_ENABLED` | the switch; gates every assistant bean (`@ConditionalOnProperty`) |
| `base-url` | `http://localhost:8081` | `RM_ASSISTANT_BASE_URL` | compose sets `http://rag-service:8081` |
| `api-key` | empty | `RM_ASSISTANT_API_KEY` | the registered client's key; never logged, never in `toString` |
| `collections.tickets` etc. | `rm-tickets`, `rm-documents`, `rm-manual-items`, `rm-code-links` | - | collection names |
| `embedding-model` | `nomic-embed-text` | - | sent in `PUT /v1/collections/{c}` (or omit, the service default is the same) |
| `sweep.initial-delay` / `sweep.interval` | `PT10S` / `PT30M` | `RM_ASSISTANT_SWEEP_INTERVAL` | D-12-05 |
| `index.connect-timeout` / `index.read-timeout` | `PT2S` / `PT5S` | - | push, delete, collection calls |
| `chat.read-timeout` | `PT130S` | - | above rag `RAG_CHAT_DEADLINE` (120 s) |
| `max-rounds` | `4` | - | app-side guard, rounds plus one POSTs at most |
| `tool-result-max-chars` | `6000` | - | below rag-service 8,000 |

`AssistantProperties` must fail fast when `enabled=true` and `base-url` or `api-key` is blank, so a half-configured switch is a startup error, not silent no-ops.

**Compose** (`compose.app.yaml`, `app` service): add `RM_ASSISTANT_ENABLED: ${RM_ASSISTANT_ENABLED:-false}`, `RM_ASSISTANT_BASE_URL: http://rag-service:8081` (compose-network DNS; the container port is 8081, not the published `${RAG_PORT}`), `RM_ASSISTANT_API_KEY: ${RM_ASSISTANT_API_KEY:-}`. Do **not** add `depends_on: rag-service` to `app`: v1 must start and run without it, and rag-service is independent of `app` by design (D-09-15). `.env.example` gains the three vars with a comment on how to obtain the key; `docs/docker.md` gains a short section.

**Client registration for the live check:** `POST /v1/admin/clients` with `Authorization: Bearer $RAG_ADMIN_KEY` and body `{"name":"releasemanagement"}` returns `{id, name, apiKey}` once; name pattern `[A-Za-z0-9._-]{1,100}` [VERIFIED: `openapi.json` `CreateClientRequest`, `docs/docker.md:343`]. The live plan stores `apiKey` into `.env` as `RM_ASSISTANT_API_KEY` without printing it (read the JSON into a shell variable, `sed -i` into `.env`, never echo), then recreates the `app` container (`docker compose -f compose.app.yaml up -d --force-recreate app`, a human step; this research did not touch docker).

## Project Constraints (from CLAUDE.md)

- Minimal code, no over-engineering or abstraction, official Spring patterns, no new dependencies unless justified (none needed: Spring events, `RestClient`, JDK `ExecutorService`/`ReentrantLock`, Jackson 3 `tools.jackson`, ArchUnit 1.4.2 and WireMock 3.13.2 are already present [VERIFIED: `pom.xml`]).
- Validate only at system boundaries (the tool arguments chosen by the model and rag-service responses are boundaries); no dead or commented-out code; comments only for a non-obvious WHY.
- Never commit secrets (`.env`, `key.txt` stay untracked); never `--no-verify`; stage files by name; no `Co-Authored-By` trailer in commits (the project rule in the global CLAUDE.md overrides the harness attribution reminder).
- Work only through GSD workflow commands.
- Environment: `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25` on every Maven call, `-P '!frontend'` for backend-only runs, one Maven build at a time, `mvn clean` after public API changes, new yml keys in both `application.yml` files, never run the Vite dev server during Maven builds, do not touch docker or Ollama from research or automated plans.

## Standard Stack

No new dependency.

| Need | Use | Notes |
|------|-----|-------|
| Event publish and handle | `ApplicationEventPublisher`, `@TransactionalEventListener(AFTER_COMMIT)` | Spring Framework 7 (Boot 4.1.0) [VERIFIED: `pom.xml` parent] |
| Async hand-off | private `Executors.newVirtualThreadPerTaskExecutor()` field | precedent `EnrichmentService`, `RenderService`; `spring.threads.virtual.enabled: true` |
| Sweep | `@Scheduled` + one `@EnableScheduling` config class | Boot auto-configures the scheduler; define no scheduler bean |
| Outbound HTTP | `RestClient` over `JdkClientHttpRequestFactory` | two beans (index, chat) as in `AdoRestClientConfig` |
| JSON | `tools.jackson.databind.ObjectMapper` / `JsonNode` | the main app is on Jackson 3 |
| Conditional wiring | `@ConditionalOnProperty(prefix = "rm.assistant", name = "enabled", havingValue = "true")` | every assistant bean; no `@Profile` |
| Tests | JUnit, AssertJ, Mockito, WireMock (HTTP adapter), Testcontainers Postgres (after-commit IT), ArchUnit | existing |

## Architecture Patterns

### System architecture diagram

```
 v1 action (refresh / add / remove / manual edit / freeze / delete / generate)
   -> controller -> service (no tx) -> RepositoryAdapter @Transactional method
        |  writes rows, then publishEvent(ReleaseIndexInvalidated(releaseId))   [inside the tx]
        v  COMMIT  (user's thread continues, response returns)
   IndexInvalidationListener  @TransactionalEventListener(AFTER_COMMIT)  [committing thread, microseconds]
        |  queued.add(releaseId)? -> executor.execute(...)   (virtual thread, off the user's thread)
        v
   IndexSyncer.sync(releaseId)  [lock] -> DB read (Release, snapshots) -> IndexDocumentMapper (pure)
        |-> RagIndexPort.put(...) x N   -> adapters.rag RestClient -> rag-service PUT /v1/collections/{c}/documents/{id} (202)
        |-> RagIndexPort.delete(...) for recorded-but-undesired ids (404 = done)
        '-> failure: log metadata (collection, id, status) and forget            (D-12-04)

   Sweep @Scheduled(startup + 30 min) -> ensureCollections -> for every release: IndexSyncer.sync(releaseId)

 Question (Phase 13 later; Phase 12 live test now)
   AssistantService.ask -> RagChatPort.chat(question, history, collections, filter, tools, turns)
        -> adapters.rag RestClient SSE -> rag-service POST /v1/chat
        <- tool_call{turn} : execute each call via AssistantTool -> services (read-only allowlist) -> JSON result
        -> resend with signed turn UNCHANGED + results   (max 4 rounds, app guard rounds+1)
        <- token... citations done  -> AssistantAnswer(text, sources, toolsRun)
```

### Recommended structure

```
domain/assistant/        ReleaseIndexInvalidated, IndexDocument, DocRef, ToolCall, ToolResult, AssistantAnswer, ToolRun, Source, ChatOutcome ...
ports/                   RagIndexPort (put, delete, ensureCollection, collectionCount), RagChatPort (chat)
application/assistant/   IndexDocumentMapper, IndexSyncer, IndexInvalidationListener, IndexSweep, AssistantService
application/assistant/tools/   AssistantTool, ToolRegistry, ReleaseRef, ReleasesTool, TicketsTool, DeploymentsTool, DashboardTool, DocumentsTool
adapters/rag/            RagIndexAdapter, RagChatAdapter (SSE), wire records
adapters/persistence/    ReleaseRepositoryAdapter, DocumentSnapshotRepositoryAdapter (publish events)
config/                  AssistantProperties, RagRestClientConfig, AssistantSchedulingConfig
```

Keep the number of types small; the indexer pieces can be two classes (`IndexSyncer` containing the mapper calls and recorded-id map, and the listener plus sweep) if the planner finds the split ceremonial.

### Anti-patterns to avoid

- Publishing from services, or a plain `@EventListener`: not post-commit, and ten hand-placed call sites.
- A `TaskExecutor`, `TaskScheduler` or `@Bean Executor`: replaces Boot's auto-configured ones (Phase 10 lesson).
- Local content-hash cache for pushes: defeats healing of a rag-service that lost data.
- Tools calling repositories, controllers, `TicketSearchService`, `EnrichmentService` or any `Jira*`/`Ado*` type; returning descriptions, comments, notes or document bodies.
- Logging the question, answer, tool arguments or results, or the api key (metadata only; `toString` of properties and requests without secrets or text).
- A conversation store or an assistant table (no saved conversations, no migration).
- A `depends_on: rag-service` in compose, or any code path that calls rag-service when the switch is off.

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| After-commit hook | transaction-synchronisation by hand | `@TransactionalEventListener(AFTER_COMMIT)` | handles rollback (drops) and ordering |
| Scheduling | own timer thread | `@Scheduled` with `initialDelayString` / `fixedDelayString` | Boot's scheduler, config-driven, covers startup and interval |
| HTTP client | raw sockets | `RestClient` + JDK `HttpClient` | house style, explicit timeouts |
| Tool-turn signing, JSON Schema validation | any client-side check | pass the signed turn back opaque; keep definitions to the flat subset | rag-service owns trust (T-11-01) |
| Read-only proof | code review | ArchUnit allowlist rules with a violation fixture | fails the build |
| SSE | a library | a ~40-line line parser in the adapter | five event names; no new dependency (`RestClient` has no SSE reader) |

## Common Pitfalls

### Pitfall 1: AFTER_COMMIT with no transaction
**What goes wrong:** the event is published and never handled. **Why:** services hold no transaction; `@TransactionalEventListener` without an active transaction is skipped unless `fallbackExecution = true`. **Avoid:** publish only from the `@Transactional` adapter methods; table test that every mutating adapter method yields one event.

### Pitfall 2: Event storm and out-of-order pushes
**What goes wrong:** a refresh fires `replaceItemSnapshots` plus one `saveEnrichment` per batch, and each would push the whole release; two syncs could finish in the wrong order and leave an older read on the server. **Avoid:** coalescing `queued` set plus one lock around read-and-push, and read the DB inside the lock.

### Pitfall 3: Scheduler or executor beans
**What goes wrong:** defining one makes Boot's `taskScheduler` back off. **Avoid:** none defined; `@EnableScheduling` only; test that the context has exactly Boot's scheduler beans (assert no user-defined `TaskScheduler` bean in the assistant config).

### Pitfall 4: Switch off must mean not constructed
**What goes wrong:** `@Value("${rm.assistant.base-url}")` on an always-created bean, or a `RestClient` bean built unconditionally. **Avoid:** `@ConditionalOnProperty` on every assistant bean and the `RagRestClientConfig`; the persistence adapters keep publishing (harmless without a listener). Context test with `enabled=false` and an empty api key.

### Pitfall 5: Tool-result size and token budget
**What goes wrong:** a list tool returns 25 releases of full detail and rag-service answers 413, or the 4,096-token context overflows (4 rounds of results). **Avoid:** small structured results, caps with `truncated`, an app-side size check, and a test that every tool's maximum-size result stays under `tool-result-max-chars`.

### Pitfall 6: Definitions over the Phase 11 intake limits
**What goes wrong:** 10 tools or over 1,500 chars gives 400 at intake and every question fails. **Avoid:** U2, and a unit test on the serialised definitions.

### Pitfall 7: Null versus zero
**What goes wrong:** the model reads a missing churn figure as 0. **Avoid:** keep `null` in the JSON (`TicketChurn` and `ReleaseChurn` already encode "no data"), and add `noData` counts the dashboard already computes.

### Pitfall 8: Resending an edited turn
**What goes wrong:** re-serialising the signed turn reorders keys or changes number formatting and the signature fails (400). **Avoid:** keep the turn as the `JsonNode`/`Map` received and write it back untouched; add a round-trip test with the fake service.

### Pitfall 9: Streaming read timeout
**What goes wrong:** the chat client times out mid-answer, or never times out on a hung stream. **Avoid:** separate chat client, read timeout above the 120 s deadline, plus an overall bound in the orchestrator; confirm in the live plan.

### Pitfall 10: Collection model conflict
**What goes wrong:** `PUT /v1/collections/{c}` with another model returns 409 and every push 404/fails. **Avoid:** send `nomic-embed-text` (the only allowed model), log a conflict as an ERROR once, and let pushes fail quietly.

## Code Examples

```java
// Persistence adapter: publish inside the transaction; the listener acts only after commit.
@Override
@Transactional
public Release removeItem(UUID releaseId, String ticketKey) {
    ReleaseEntity entity = lockOpen(releaseId);
    if (itemJpa.deleteByReleaseIdAndTicketKey(releaseId, ticketKey) > 0) {
        stampItemsChanged(entity);
        jpa.save(entity);
    }
    publisher.publishEvent(new ReleaseIndexInvalidated(releaseId));
    return load(releaseId);
}
```

```java
// Listener: enqueue only. Never HTTP here, so the committing thread is never blocked.
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
void onInvalidated(ReleaseIndexInvalidated event) {
    if (queued.add(event.releaseId())) {
        executor.execute(() -> {
            queued.remove(event.releaseId());
            syncer.sync(event.releaseId());
        });
    }
}
```

```java
// Sweep: same pattern as rag-service IndexWorker.tick(); no scheduler bean.
@Scheduled(initialDelayString = "${rm.assistant.sweep.initial-delay}",
           fixedDelayString = "${rm.assistant.sweep.interval}")
void sweep() {
    index.ensureCollections();
    releases.search("").forEach(r -> syncer.sync(r.id()));
}
```

```java
// ArchUnit method allowlist (sketch)
private static final Set<String> READ_ONLY = Set.of(
        "ReleaseService.get", "ReleaseService.search", "ReleaseService.releasesContaining",
        "PromotionService.history", "EnvironmentLadderService.ladder",
        "DashboardService.releases", "DashboardService.deployments", "DashboardService.ticketChurn",
        "DocumentReadinessService.all", "DocumentGenerationService.revisions",
        "ActorResolutionService.resolve");

static ArchCondition<JavaClass> onlyCallAllowlistedReadMethods() {
    return new ArchCondition<>("only call allowlisted read-only service methods") {
        @Override
        public void check(JavaClass tool, ConditionEvents events) {
            tool.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().getPackageName().startsWith("com.evision.releasemgmt.application")
                        && !call.getTargetOwner().getPackageName().contains(".assistant.tools"))
                .filter(call -> !READ_ONLY.contains(call.getTargetOwner().getSimpleName() + "." + call.getName()))
                .forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
        }
    };
}
```

## State of the Art

| Old approach | Current approach | Impact |
|--------------|------------------|--------|
| Service-level event publish with `@EventListener` | `@TransactionalEventListener(AFTER_COMMIT)` published inside the committing transaction | the only way to guarantee "after commit" |
| Hand-built executor beans for async | Boot's auto-configured virtual-thread executors or a private `newVirtualThreadPerTaskExecutor()` field | no replacement of Boot defaults |
| In-process tool frameworks | caller-run tool loop over a signed stateless protocol (Phase 11) | the app owns tool execution, rag-service never calls in |

## Runtime State Inventory

Not a rename or migration phase. Omitted per template.

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | User accepts amending D-12-02 to per-release ticket and code-link ids (U1) | Finding 1 | Planner must drop per-release filtering for tickets |
| A2 | User accepts consolidating to 5 tools (U2) rather than raising Phase 11 limits | Finding 5 | A rag-service config change and a re-measure are added |
| A3 | User accepts the in-memory orphan limitation instead of a rag-service list endpoint (U3) | Finding 3 | One extra rag-service plan |
| A4 | User accepts indexing comments that may have been restricted in Jira, as v1 screens already show them (U4) | Finding 4 | Ticket text must drop comments, or a Jira adapter plus migration is added |
| A5 | JDK `HttpRequest.timeout` bounds time to response headers, not the streamed body | Finding 6 | Chat client needs a different timeout strategy; the live plan checks |
| A6 | Phase 11 wire shapes (`tools`, `toolTurns`, `tool_call{round, questionId, calls, signature}`, result `source`) will match `11-RESEARCH.md` once 11-06 publishes `openapi.json` | Finding 6 | `RagChatAdapter` wire records change; orchestrator is unaffected because the turn is opaque |
| A7 | A sequential push of a few thousand documents per sweep is acceptable on the target host | Finding 3 | Add a bounded parallelism or a `since` cut; measure in the live plan |
| A8 | Postgres `release_ticket_item` supports a by-key lookup without a new index at current sizes | Finding 5 | Add an index in a migration (contradicts "no migration" only for that read path) |
| A9 | `Release.jiraProjectKey` is optional, so `project_key` for manual items and documents is sometimes absent | Finding 1 | Later per-user filtering would miss those documents; consider the key prefix of the release's first ticket |

## Open Questions

1. **Do releases mix Jira projects?** `Release.jiraProjectKey` is nullable. For tickets the key prefix is exact; for documents and manual items `project_key` may be missing. Recommendation: store what exists now, omit otherwise (A9).
2. **Does the model need the release name inside the question for "this release"?** Tools take a `release` argument; Phase 13 should inject the name. Recommendation: note it in the Phase 13 handoff.
3. **Event placement for `ReleaseIndexInvalidated` in the domain package** is a convenience (the persistence adapter may not import `application`); acceptable because the record has no framework types.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | build | yes | `C:/Users/ucs/.jdks/jdk-25` (also ms-17, ms-21, ms-25.0.4) | none needed |
| Maven | build | yes | 3.9.11 | none needed |
| rag-service container | live plan only | not probed (docker must not be touched) | image `rag-service:0.1.0-SNAPSHOT` per compose | hermetic tests use WireMock and fakes |
| Host Ollama, `qwen3:4b`, `nomic-embed-text` | live plan only | not probed (Ollama must not be touched) | per Phase 11 research: Ollama 0.34.0, models installed | none |
| Phase 11 executed (11-04..11-06) | `RagChatAdapter`, live plan | no (3/9; waits for the Phase 10 model decision) | - | orchestrator is built and tested against a fake `RagChatPort` |

**Blocking for the live plan:** Phase 10 decision, Phase 11 through 11-06 (and 11-08 for the measured model). **Not blocking for plans 12-01..12-07.**

## Validation Architecture

> `workflow.nyquist_validation` is `false` in `.planning/config.json`; included because the orchestrator requested it.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit (Boot-managed), AssertJ, Mockito, WireMock 3.13.2, Testcontainers 2.0.5, ArchUnit 1.4.2 |
| Config file | `pom.xml` surefire includes `*Test`, `*Tests`, `*IT` |
| Quick run | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -P '!frontend' test -Dtest='Assistant*Test,Index*Test,*ToolTest,ArchitectureBoundaryTest'` |
| Full suite | `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -P '!frontend' test` (baseline about 962 tests; one Maven build at a time) |

### Behaviour to Test Map

| Req | Behaviour | Type | Command or class | File exists |
|-----|-----------|------|------------------|-------------|
| ASST-01 | mapper: ids, metadata keys, text per source, latest revision only, empty code links skipped | unit | `IndexDocumentMapperTest` | Wave 0 |
| ASST-01 | event published once per mutating adapter method; handled only after commit; dropped on rollback; handler thread has no active tx | IT (Testcontainers) | `IndexAfterCommitIT` | Wave 0 |
| ASST-01 | v1 call unaffected when the port blocks, throws or times out | unit/IT | `IndexSyncerTest`, `IndexAfterCommitIT` | Wave 0 |
| ASST-01 | coalescing, lock, delete of recorded-but-undesired ids, 404 on delete is success, deleted release removes all | unit | `IndexSyncerTest` | Wave 0 |
| ASST-01 | sweep calls ensureCollections and syncs every release; runs only when enabled | unit + context test | `IndexSweepTest`, `AssistantSwitchOffIT` | Wave 0 |
| ASST-01 | HTTP: PUT/DELETE/PUT-collection shapes, bearer header, timeouts, 4xx/5xx mapped, no secret in logs | WireMock | `RagIndexAdapterTest` | Wave 0 |
| ASST-01 | switch off: no assistant beans, no scheduler bean added, v1 context starts with empty key | context test | `AssistantSwitchOffIT` | Wave 0 |
| ASST-03 | each tool returns only structured fields, caps and `truncated`, null for no data, error results for bad args; no body text | unit (mocked services) | `*ToolTest` | Wave 0 |
| ASST-03 | definitions: at most 5 tools, at most 1,500 chars, descriptions at most 300, flat schema | unit | `ToolDefinitionsTest` | Wave 0 |
| ASST-03 | loop: tool_call then resend with the turn unchanged, several calls per round, guard at rounds+1, error mapping, oversize result replaced | unit with fake `RagChatPort` | `AssistantServiceTest` | Wave 0 |
| ASST-03 | wire: SSE parse, tools and toolTurns serialisation, 429/503 mapping | WireMock | `RagChatAdapterTest` (after 11-06) | Wave 0 |
| ASST-04 | rules active on the real tools, rejected on fixtures | ArchUnit | `ArchitectureBoundaryTest` | extend |
| ASST-01/03 | end-to-end on this machine with the switch on | live, human | `AssistantLiveCheck` (`@EnabledIfSystemProperty`) | Wave 0 |

### Sampling rate
- Per task: the quick command on touched classes. Per wave: the full backend suite. Phase gate: full suite green plus the human live check.
- Acceptance criteria assert properties (event after commit and not on rollback, v1 call returns with a blocking port, ArchUnit fails on a fixture), not grep counts (memory: plan grep criteria are brittle).

### Wave 0 Gaps
- [ ] `IndexAfterCommitIT` needs the Testcontainers Postgres base used by existing persistence ITs (reuse `PgVectorImage`/existing IT config).
- [ ] Archunit fixture classes under `src/test/java/.../archfixture`.
- [ ] `FakeRagIndexPort`, `FakeRagChatPort`, WireMock stubs for `/v1/collections/**` and an SSE body.
- [ ] A scripted fake chat service helper for the loop (tool_call then answer).

## Security Domain

### Applicable ASVS categories

| ASVS | Applies | Standard control |
|------|---------|------------------|
| V2 Authentication | yes (service-to-service) | rag-service bearer client key from env; Phase 13 puts the browser behind the existing cookie login |
| V4 Access control | yes | tools read-only by ArchUnit; answers shared as in v1, with no per-user claim |
| V5 Input validation | yes | tool arguments from the model validated at the tool boundary; rag-service responses parsed defensively |
| V6 Cryptography | partial | no new crypto; the tool-turn signature is rag-service's and is returned untouched |
| V7 Logging | yes | metadata only, no content, no key |
| V12/V13 Communication, API | yes | explicit timeouts, no redirects followed, base URL from config |

### Threats

| ID | Threat | STRIDE | Mitigation |
|----|--------|--------|------------|
| T-12-01 | Index push runs inside, or blocks, the user's transaction or request | Denial of service | adapter-level publish plus AFTER_COMMIT listener that only enqueues; blocking-port and rollback tests |
| T-12-02 | A tool mutates state or reaches Jira, ADO, credentials or the index writer | Elevation of privilege | method and class allowlist ArchUnit rules with a fixture proving they bite |
| T-12-03 | Prompt injection through ticket text, comments or document bodies | Tampering | rag-service fences passages; tools never return free text (D-12-06); read-only tools bound the damage to a wrong answer |
| T-12-04 | A Jira comment restricted in Jira becomes visible to every user through the assistant | Information disclosure | accepted per U4 (the v1 screens already show it); recorded; assistant never claims per-user permissions |
| T-12-05 | RAG api key leaks (logs, `toString`, responses, git) | Information disclosure | env only, `toString` redacted, no header logging, `.env` untracked, key written to `.env` without echo in the live plan |
| T-12-06 | Orphaned or stale documents answer questions about removed tickets or old revisions | Information disclosure | sweep deletes recorded-but-undesired ids, same-id replacement for documents; residual per U3 |
| T-12-07 | Tool-result flood or runaway loop exhausts context or time | Denial of service | result and list caps, app-side size check, rounds+1 guard, rag-service caps |
| T-12-08 | rag-service outage or slowness degrades v1 | Denial of service | short index timeouts, async off-thread pushes, failures dropped, switch default off, no `depends_on` |
| T-12-09 | Model-supplied tool arguments abuse a tool (huge `days`, odd release text) | Tampering | bounds on every integer and string, `ReleaseRef` exact-match resolution, error results |
| T-12-10 | Content or secrets in logs from the loop (question, answer, args, results) | Information disclosure | metadata-only log lines; canary test as in rag-service `LogHygieneIT` |
| T-12-11 | Half-configured switch (`enabled` with blank key) silently does nothing | Spoofing/Repudiation | fail fast at startup in `AssistantProperties` |

## Plan-shaping recommendations

Dependency facts: Phase 9 is done; Phase 11 is 3/9 and its remaining plans wait for the Phase 10 chat-model decision. Everything in Phase 12 that does not call `/v1/chat` with tools can be planned and executed now.

| Wave | Plan | Scope | Owns (no same-wave overlap) | Needs Phase 11 |
|------|------|-------|-----------------------------|----------------|
| 1 | 12-01 Config and ops | `AssistantProperties` (fail-fast), conditional registration, both `application.yml`, `compose.app.yaml`, `.env.example`, `docs/docker.md` section | `config/AssistantProperties.java`, `src/main/resources/application.yml`, `src/test/resources/application.yml`, `compose.app.yaml`, `.env.example`, `docs/docker.md` | no |
| 1 | 12-02 Tools | `AssistantTool`, `ToolRegistry`, `ReleaseRef`, five tools (or ten under U2), `ReleaseService.releasesContaining` with the repository, adapter and JPA read method, definitions-size test | `application/assistant/tools/**`, `ReleaseService.java`, `ports/ReleaseRepository.java`, `adapters/persistence/ReleaseRepositoryAdapter.java` (read method only), `ReleaseTicketItemJpaRepository.java`, their tests | no (limits are constants) |
| 1 | 12-03 Domain, ports, mapper | `domain/assistant/**` records, `RagIndexPort`, `RagChatPort`, `IndexDocumentMapper` and its test | `domain/assistant/**`, `ports/RagIndexPort.java`, `ports/RagChatPort.java`, `application/assistant/IndexDocumentMapper.java` | no |
| 2 | 12-04 Index adapter | `config/RagRestClientConfig` (index client now; chat client bean added in 12-08), `adapters/rag/RagIndexAdapter`, WireMock tests | `adapters/rag/RagIndexAdapter*.java`, `config/RagRestClientConfig.java` | no |
| 2 | 12-05 Post-commit indexer and sweep | event publishing in both persistence adapters, `IndexInvalidationListener`, `IndexSyncer`, `IndexSweep`, `AssistantSchedulingConfig`, after-commit IT, switch-off IT | `adapters/persistence/ReleaseRepositoryAdapter.java` (publish edits), `DocumentSnapshotRepositoryAdapter.java`, `application/assistant/Index*.java`, `config/AssistantSchedulingConfig.java`, ITs. **Edits `ReleaseRepositoryAdapter` after 12-02 (wave 1), so no same-wave overlap.** | no |
| 2 | 12-06 ArchUnit | the five rules, static helpers, real-tools non-vacuity case, fixtures, add `adapters.rag` to the existing application rule | `ArchitectureBoundaryTest.java`, `src/test/.../archfixture/**` | no (tools exist from 12-02) |
| 3 | 12-07 Orchestrator | `AssistantService` loop, `ToolRun`/`Source` recording, error mapping, size guard, tests with a fake `RagChatPort` | `application/assistant/AssistantService.java`, tests | no (turn is opaque) |
| 4 | 12-08 Chat adapter | `RagChatAdapter` (SSE parser, tools and toolTurns wire, 429/503 mapping), chat client bean, WireMock tests | `adapters/rag/RagChatAdapter*.java`, `config/RagRestClientConfig.java` | **yes: after 11-06 (published `openapi.json`)** |
| 5 | 12-09 Live check (`autonomous: false`) | register client, store key without printing, switch ON, recreate `app`, backfill, stop rag-service to prove v1 unaffected, ask exact-fact questions through an opt-in live test, confirm tool and source recording, removal and regeneration reflected in search | `.planning/phases/12-*` notes, `.env` (untracked), the live test class | **yes: Phase 11 through 11-06, Phase 10 decision** |

Notes for the planner:
- Executable before Phase 11 completes: 12-01 through 12-07. Hold 12-08 and 12-09. A fake `RagChatPort` in 12-07 means the loop logic is proven without the service.
- `config/RagRestClientConfig` is touched by 12-04 and again by 12-08 (different waves). `ReleaseRepositoryAdapter` is touched by 12-02 and 12-05 (different waves). Keep the order.
- Each plan's acceptance criteria should assert properties: a rollback publishes nothing handled, a blocking port does not slow `refresh`, the context has no assistant beans when the switch is off, ArchUnit fails on the fixture, the serialised definitions fit 1,500 characters, every tool result stays below the cap.
- After public API changes (new port methods, event record) run `mvn clean` once in the wave that adds them.
- The live plan must not print the api key, must not touch Ollama settings, and uses the compose network name `rag-service:8081` from the app container (and `localhost:8081` for host-side curl).

## Sources

### Primary (HIGH confidence, read in this session)
- `.planning/phases/12-releasemanagement-as-first-client/12-CONTEXT.md`, `.planning/REQUIREMENTS.md` (ASST-01..09), `.planning/ROADMAP.md` (Phase 12/13), `.planning/STATE.md`
- `.planning/phases/09-rag-service-foundation-index/09-CONTEXT.md` (D-09-01..17), `.planning/phases/11-tool-call-protocol/11-RESEARCH.md`, `11-AI-SPEC.md` (intake limits, budget)
- `rag-service/openapi.json`; rag-service `MetadataRules`, `ChunkSearchRepositoryAdapter`, `DocumentRepositoryAdapter`, `DocumentService`, `DocumentController`, `ContentHash`, `ToolProperties`, `application.yml`, `SchedulingConfig`, `GenerationConfig`
- Main app: `ReleaseService`, `DocumentGenerationService`, `PromotionService`, `EnvironmentLadderService`, `DashboardService`, `DocumentReadinessService`, `ActorResolutionService`, `ReleaseRepository`, `DocumentSnapshotRepository`, `ReleaseRepositoryAdapter`, `DocumentSnapshotRepositoryAdapter`, `JiraRestTicketAdapter`, domain records (`Release`, `ReleaseItem`, `TicketDetail`, `CodeLinks`, `StatusHistory`, `PromotionHistory`, dashboard records), `AdoRestClientConfig`, both `application.yml`, `compose.app.yaml`, `.env.example`, `ArchitectureBoundaryTest`, `pom.xml`
- Frontend: `TicketBrowser.tsx`, `ReleaseDetailRoute.tsx` (comments shown)

### Secondary (MEDIUM)
- Phase 11 wire shapes from `11-RESEARCH.md` (recommended, not yet published in `openapi.json`)

### Tertiary (LOW)
- JDK `HttpRequest.timeout` semantics for streamed bodies (A5), from training knowledge; verify in the live plan

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependency, all verified in `pom.xml`
- Architecture (events, sweep, tools): HIGH for the code mapping, MEDIUM for orphan handling (U3) and chat wire (A6)
- Pitfalls: HIGH for transaction and Phase 11 limit findings (read from code), MEDIUM for streaming timeouts

**Research date:** 2026-10-15
**Valid until:** Phase 11 plan 11-06 publishes `openapi.json`, or the Phase 10 model decision changes tool or prompt budgets
