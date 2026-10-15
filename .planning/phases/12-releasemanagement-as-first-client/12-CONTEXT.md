# Phase 12: ReleaseManagement as First Client - Context

**Gathered:** 2026-10-15
**Status:** Ready for planning. It depends on Phase 9 (done) and Phase 11. Planning may proceed now. Tasks that run the tool loop need Phase 11 executed, and Phase 11 waits for Phase 10.

<domain>
## Phase Boundary

ReleaseManagement, the v1 main app, becomes `rag-service`'s first caller in two ways:
1. **Index:** after each change commits, it pushes the text the screens show (ticket text, manual items, the latest generated documents, code-link text), plus a backfill sweep (ASST-01).
2. **Tools:** it defines about 10 read-only tools over its existing application services and runs the Phase 11 tool loop itself, so exact facts (stage, dates, counts, readiness, deployments, churn) come from the application rather than from text (ASST-03). Architecture tests prove the tools are read-only and isolated (ASST-04).

Not in this phase:
- the Assistant screen, "Ask about this release" and the "how I answered" panel (Phase 13);
- the full golden quality gate (Phase 13);
- AI document assist and the per-document prompts with implementation notes and commits (Phase 14);
- changes to `rag-service` itself, which is domain-free.

**Every v1 feature must behave exactly as before, whether rag-service is down, slow or switched off.**
</domain>

<decisions>
## Implementation Decisions

### Already locked (PROJECT.md, REQUIREMENTS.md, research/v1.1, ROADMAP scope notes, Phase 9-11 CONTEXT)
- The RAG client adapter sits behind a port in the main app's hexagon. rag-service is reached only through its published `/v1` API with ReleaseManagement's own client API key (Phase 9 D-09-01). The browser never holds that key (ASST-02, Phase 13).
- Index only what the screens show, and only the latest revision of each document type. Answers are shared as in v1, with no per-user Jira filtering. The assistant must never claim to honour per-user Jira permissions. Store `project_key` in metadata now, for possible later filtering.
- Tools call existing application services in-process, never controllers or repositories. They are read-only, with no Jira, Azure DevOps, credential or index-writer dependency, enforced by ArchUnit with a positive case so the rules are not vacuous (ROADMAP success criterion 5).
- The tool loop follows Phase 11's protocol: stateless resend with the signed tool-call turn, at most 4 rounds, tool failures returned as error results, 413 for oversized results (D-11-01..10).
- An `rm.assistant.enabled` switch (D-12-09).

### Collection layout
- **D-12-01: One collection per source type:** `rm-tickets`, `rm-documents`, `rm-manual-items`, `rm-code-links`. Every item carries `release_id`/`project_key`/status metadata. A question can search all four (Phase 10 multi-collection, D-10-05). Each source can be re-indexed or dropped on its own. Phase 13's "Ask about this release" becomes a `release_id` filter.
- **D-12-02: Stable natural document ids.** These are `ticket:{key}` (one per ticket, shared across releases), `document:{releaseId}:{type}` (the latest revision, replaced on regeneration), `manual:{itemId}` and `codelinks:{ticketKey}`. A re-push replaces in place, and deletes are exact (ROADMAP success criterion 3).
  - **Research must solve:** rag-service metadata is flat strings and numbers with no arrays (D-09-07), so a ticket in several releases cannot carry a list of release ids. Propose an encoding that keeps `release_id` filtering working without changing rag-service, if possible.

### Freshness and backfill
- **D-12-03: Seconds, best effort.** Push asynchronously right after the originating transaction commits (refresh, add or remove ticket, manual item edit, document generation). It must never run inside, or block, the user's transaction (ROADMAP research flag).
- **D-12-04: A failed push is dropped and the sweep heals it.** A push that fails or times out is logged as metadata only and forgotten. There is no outbox table, no retry machinery, and no Flyway migration for this.
- **D-12-05: The sweep runs at startup and every 30 minutes, configurable.** It compares and re-pushes anything missing or stale, and deletes what was removed. It is cheap, because unchanged content is a no-op in rag-service through its content hash. It runs only when the switch is on.

### Tool set
- **D-12-06: Tools return structured fields only:** stage, dates, counts, statuses, keys, titles, readiness flags and deployments. They never return free text such as descriptions, comments or document bodies. Free text reaches the model only through fenced retrieval. This removes the strongest injection channel and keeps results small within the 4096-token budget.
- **D-12-07: The full proposed set, about 10 coarse flat tools:** `list_releases`, `get_release`, `get_ticket`, `get_promotion_history`, `get_ladder`, `get_dashboard_summary`, `list_recent_deployments`, `get_ticket_churn`, `get_document_readiness`, `list_document_revisions`. Names and argument schemas may be refined in research, but not expanded beyond this scope.

### Scope of indexed data
- **D-12-08: All releases are indexed,** including frozen, shipped and rolled-back ones, with their status in metadata.
- **D-12-09: `rm.assistant.enabled` defaults to OFF.** Nothing is pushed and no tools are exposed until it is turned on, so v1 deployments without rag-service are unaffected. It is turned on on this machine for Phase 12's live check.
- **D-12-10: Ticket comments only as the screens show them.** Research checks whether restricted-visibility Jira comments are stored today, and excludes anything the screens don't show.

### Claude's Discretion
- The RAG client adapter's shape: a port in `ports/`, a `RestClient` adapter with explicit timeouts, and configuration (`rm.assistant.*`: base URL, API key, collection names, sweep interval, timeouts) in both `application.yml` files, `compose.app.yaml` and `.env.example`.
- Where the tool loop lives in ReleaseManagement: an application-layer orchestrator that calls rag-service `/v1/chat`, executes tools, and resends. It must record which tools ran and which sources were used, for Phase 13's "how I answered" panel (ASST-06), without building any UI.
- Exact tool argument and return schemas, the text formatting of pushed items (title plus body; ADF already flattened), and the metadata keys.
- How the after-commit hook is wired (e.g. `@TransactionalEventListener(phase = AFTER_COMMIT)` with async execution), and how the sweep is scheduled. Do not let a new scheduler or executor bean replace Boot's defaults (lesson from Phase 10, 10-01).
- How `collections` are created on first use (`PUT /v1/collections/{c}` with `nomic-embed-text`) when the switch turns on.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone and requirements
- `.planning/ROADMAP.md`: "Phase 12: ReleaseManagement as First Client" (success criteria, research flags, scope notes)
- `.planning/REQUIREMENTS.md`: ASST-01, ASST-03, ASST-04, plus ASST-02/05/06 for what Phase 13 will need from this phase
- `.planning/PROJECT.md`: v1.1 milestone section and the Key Decisions row "Exception (2026-10-13): a standalone, domain-free RAG service"

### rag-service contract (what this phase calls)
- `rag-service/openapi.json`: the published API (collections, documents, search, chat, transform; the Phase 11 tool fields after 11-06)
- `.planning/phases/09-rag-service-foundation-index/09-CONTEXT.md`: D-09-01..17 (client keys, collections, flat metadata, 202 pushes, outage semantics)
- `.planning/phases/10-rag-chat-citations/10-CONTEXT.md`: D-10-01..12 (multi-collection chat, limits, 503/429 reasons)
- `.planning/phases/11-tool-call-protocol/11-CONTEXT.md` and `11-RESEARCH.md`: D-11-01..12 and the tool-call wire protocol (signed turns, resend, rounds, error results)

### Research (v1.1)
- `.planning/research/v1.1/SUMMARY.md`: per-user scoping decision, latest-revision-only, `IndexSweep`, the proposed tool list
- `.planning/research/v1.1/ARCHITECTURE.md`: indexer/diff design and ArchUnit guidance for the assistant package
- `.planning/research/v1.1/PITFALLS.md`: stale index, injection through ticket text
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Application services the tools wrap: `ReleaseService`, `ReleaseViewAssembler`, `PromotionService`, `EnvironmentLadderService`, `DashboardService`, `DocumentReadinessService`, `DocumentGenerationService` (revisions), `EnrichmentService` (code links), `TicketSearchService`.
- Ports and repositories exist for releases, promotions, documents and the dashboard read model. Tools must use services, not these repositories directly.
- `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java` (ArchUnit 1.4.2) is where the new read-only and isolation rules go, with a positive case.
- The Jira and ADO `RestClient` configs show the house style for an outbound client with explicit timeouts.

### Established Patterns
- Hexagonal main app: `domain` / `application` / `ports` / `adapters`; Flyway migrations up to V11 (the story-points quick task). This phase should need none (D-12-04).
- No `@TransactionalEventListener` exists yet, so the after-commit push is a new pattern; research must confirm it never runs inside refresh or generate transactions.
- Environment landmines: `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25`; `src/test/resources/application.yml` shadows main's, so new keys go in both; `mvn clean` after public API changes; never run the Vite dev server during Maven builds; the main-app backend suite is about 962 tests.
- A new scheduler or executor bean replaces Boot's auto-configured ones. Avoid that, or IndexWorker-style scheduling breaks (Phase 10 lesson).

### Integration Points
- Hook points: ticket refresh, add and remove (release assembly), manual item edits, document generation.
- Config: `rm.assistant.enabled` (default false) plus rag-service URL, key and timeouts in both yml files, `compose.app.yaml` (the app service talks to `rag-service:8081` on the compose network) and `.env.example`.
- rag-service must have a registered client for ReleaseManagement. The live plan registers it through the admin API and stores the key in `.env` without printing it.
</code_context>

<specifics>
## Specific Ideas
- The switch defaulting to off is the safety net. With it off, the app must not even construct a rag-service client call.
- Phase 13 needs, from this phase, the list of tools run and the sources used for each answer. Record them, but build no UI.
</specifics>

<deferred>
## Deferred Ideas
- A durable outbox for failed pushes (D-12-04), only if the sweep proves too slow to heal.
- Per-user Jira project filtering on `project_key`: deferred, the data is stored now.
- Tool results with free-text excerpts (D-12-06).
- The per-document AI prompts with implementation notes and commits: Phase 14.
</deferred>

---

*Phase: 12-releasemanagement-as-first-client*
*Context gathered: 2026-10-15*
