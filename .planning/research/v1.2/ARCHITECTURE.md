# Architecture Patterns - v1.2 Portfolio & Planning

**Domain:** Release management tool, adding portfolio (products/clients), RICE roadmap, usage tracking and two AI aids
**Researched:** 2026-10-20
**Scope:** Integration of the five v1.2 features into the existing modular monolith + rag-service. Nothing here re-describes v1.0/v1.1.
**Overall confidence:** HIGH on integration points (read from the code); MEDIUM on the CSRF/beacon detail and the products<->clients cardinality (needs a user answer).

## Grounding (what the code actually looks like today)

| Fact | Where | Consequence for v1.2 |
|------|-------|----------------------|
| Hexagonal packages: `domain/`, `ports/`, `application/`, `adapters/{web,persistence,rag,ado,jira,...}` | `src/main/java/com/evision/releasemgmt` | New features follow the same split; every outbound dependency gets a port. |
| Flyway is at V13 (V12/V13 = AI provenance on `document_snapshot`) | `src/main/resources/db/migration` | v1.2 starts at V14. `ai_mode` has a CHECK constraint that must be widened again (V13 did exactly this for SUMMARIZE). |
| `promotion` is an append-only ledger (trigger blocks UPDATE/DELETE; corrections are rows with `supersedes_id`; composite FK keeps a correction on the same release); current stage is derived on read, never stored | V10, `PromotionRepository` (no save/update/delete), `PromotionHistory` | The pattern to copy for client deployments. The table itself must NOT be reused (see below). |
| Dashboard is a read model over this DB only (no Jira/ADO call), `DashboardReadRepository` is read-only by construction | `DashboardService`, `DashboardReadRepository` | Portfolio views extend the same read-model style. |
| ADO commits/PRs are already stored per ticket item (`release_item_commit`, `release_item_pull_request` with `title`, `status`) by enrichment | V9, `EnrichmentService` | Runbook Recommend reads stored rows. It makes no live ADO call, so ADO outages cannot break it. |
| Doc assist: `AssistantTransformService.prepare/run/saveAiRevision` -> `RagTransformPort` (`text` + `instruction`) -> rag-service `/v1/transform` (SSE) ; save goes through `AiRevisionService` -> `DocumentSnapshot.createAiRevision`; `AiMode` {CLEAN_UP, SUMMARIZE, CUSTOM} | `application/assistant`, `adapters/rag` | Recommend and the planning assistant ride this path. |
| rag-service transform system prompt says "Keep every ... unchanged ... Do not add facts. Output plain text." | `rag-service/src/main/resources/application.yml` line ~99 (`rag.prompts.transform-system`) | **Blocking finding:** the existing transform prompt forbids exactly what Recommend needs (suggest steps, risks, validation). Recommend needs a second prompt profile in rag-service. |
| rag-service is domain-free; Ollama is its only outbound call, enforced by an ArchUnit outbound rule (10-07, 11-07); holds no app credentials | v1.1 roadmap, `ChatPort` has one adapter `OllamaChatAdapter` | Decides where the external provider does NOT go. |
| Credentials at rest: AES-256-GCM `EncryptedTokenConverter` (key `app.credential.key`), used by `AdoCredentialEntity`/`JiraCredentialEntity` | `adapters/persistence/crypto` | Reuse for the external provider API key. |
| Assistant tools are read-only, ArchUnit-enforced; assistant is `@ConditionalOnProperty app.assistant.enabled` | `ArchitectureBoundaryTest`, `AssistantConfig` | New tools (if any) must satisfy the same rules; new services that use rag should honour the same switch. |
| Single permission level; no admin role exists | `SecurityConfig` | "Admin-configured" provider must be defined without a role system (see decision 2). |
| SPA hand-written fetch layer `frontend/src/api/*.ts`, routes in `frontend/src/routes`, feature folders `assistant/`, `documentAssist/` | `frontend/src` | New: `portfolio/`, `roadmap/`, `usage/`, `planning/` folders and route files. |

## Recommended Architecture

### Component map (NEW = n, MODIFIED = m)

```
                                  Browser (React SPA)
  +------------------------------------------------------------------------------+
  | Releases(m)  Dashboard(m)  Portfolio(n)  Roadmap(n)  Planner(n)  Settings(m)  |
  |   |             |             |             |            |            |        |
  |   |      usage/track()(n) -- batches {key,count} ---------+            |        |
  +---|-------------|-------------|-------------|------------|------------|--------+
      v             v             v             v            v            v
  +------------------------------------------------------------------------------+
  | adapters/web                                                                  |
  |  ReleaseController(m: products)  DashboardController(m)  ProductController(n) |
  |  ClientController(n)  ClientDeploymentController(n)  RoadmapController(n)     |
  |  UsageController(n)  AssistantTransformController(m: RECOMMEND)               |
  |  PlanningController(n, SSE)  PlanningProviderController(n)                    |
  +------------------------------------------------------------------------------+
      v
  +------------------------------------------------------------------------------+
  | application                                                                   |
  |  ProductService(n)  ClientDeploymentService(n)  PortfolioDashboardService(n)  |
  |  RoadmapService(n)  RiceScorer(n, pure)  RoadmapJiraImporter(n)               |
  |  UsageService(n)    UsageEventRegistry(n)                                     |
  |  RecommendEvidenceAssembler(n)  AssistantTransformService(m: +RECOMMEND)      |
  |  PlanningService(n)  PlanningContextAssembler(n)  PlanningProviderService(n)  |
  |  ReleaseService(m: set products while OPEN)  RenderContextMapper(m: products) |
  +------------------------------------------------------------------------------+
      v (ports)
  +------------------------------------------------------------------------------+
  | ports (n): ProductRepository ClientRepository ClientDeploymentRepository      |
  |            RoadmapRepository UsageRepository PlanningProviderConfigRepository|
  |            PlanningModelPort                                                  |
  | ports (m): DashboardReadRepository (+portfolio reads)  RagTransformPort(+profile)|
  +------------------------------------------------------------------------------+
      v                                         v                          v
  adapters/persistence (n+m)            adapters/rag (m)           adapters/planning (n)
  Postgres V14..V18                     RagTransformAdapter        OpenAiCompatibleAdapter
                                              |                    (RestClient -> admin URL)
                                              v
                                     rag-service (m: +synthesize profile)
                                              |
                                              v
                                          Ollama  (only outbound call, unchanged)
```

### Component boundaries

| Component | Responsibility | Talks to |
|-----------|----------------|----------|
| `ProductService` / `ClientService` (n) | CRUD for products, clients, product<->client membership; set a release's products while OPEN | `ProductRepository`, `ReleaseRepository` |
| `ClientDeploymentService` (n) | Append a client deployment (+ correction) under the same row-lock discipline as `PromotionService`; validates release contains the product, client belongs to product | `ClientDeploymentRepository`, `ReleaseRepository` |
| `PortfolioDashboardService` (n) | Releases by product; "what each client runs"; "behind latest" flag. Read-only. Reuses `DashboardService` stage/shipped derivation rather than re-implementing it | `DashboardReadRepository` (extended), `DashboardService` |
| `RoadmapService` + `RiceScorer` (n) | CRUD of manual items, import of Jira items, ranking. `RiceScorer` is a pure domain function | `RoadmapRepository`, `JiraTicketPort` via existing access-context service |
| `UsageService` (n) | Validate event keys against an allowlist, upsert counts per (day, key); read the matrix | `UsageRepository` only |
| `RecommendEvidenceAssembler` (n) | Build the bounded evidence text from stored commits and PR titles/status for one release; reports what was omitted | `ReleaseRepository` (stored code links), `TransformSizeEstimator` |
| `AssistantTransformService` (m) | Gains RECOMMEND: evidence text instead of stored document text; everything else (limits, SSE, save as AI revision) unchanged | `RagTransformPort`, `AiRevisionService` |
| `PlanningService` (n) | Assemble context, pick model via port, stream, never persist the plan | `PlanningContextAssembler`, `PlanningModelPort` |
| `PlanningModelPort` (n) | One method: stream a plan for (context, instruction) to a listener; result carries provider identity (LOCAL/EXTERNAL, host, model) | two adapters below |
| `RagPlanningModel` (n) | Default adapter: delegates to `RagTransformPort` with the synthesize profile | rag-service |
| `OpenAiCompatibleAdapter` (n) | Optional adapter: `POST {base}/chat/completions` with `stream:true`; parse SSE with the extracted `SseStreamReader` | admin-configured external host only |
| `PlanningProviderService` (n) | Read/write the singleton provider config, validate host against the ops allow-list, choose adapter per request | `PlanningProviderConfigRepository` |
| rag-service `TransformService` (m) | Add a `profile` (`rewrite` default, `synthesize`) selecting the system prompt; no other change | Ollama |

## Decision 1: Client deployments vs existing promotions

**Recommendation: a separate append-only ledger `client_deployment`; do not write client installs into `promotion`.**

Why (all from the code):

1. `promotion` means "release R reached ladder stage S". The ladder is shared, a release has one position on it, and `DashboardService` derives stage, shipped time, failed stage, median frozen-to-production and releases-per-stage from effective promotions. Rows meaning "client C installed R" would inflate or corrupt every one of those figures (a release "deployed to PROD" 40 times).
2. `promotion` has no client or product column; adding nullable `client_id` to an append-only audited table whose invariants (`promotion_stage_deployed_idx`, effective = not superseded and not VOIDED) are relied on by Phase 8 forces every existing query to add `WHERE client_id IS NULL`. One missed query is a silent wrong number.
3. Keeping the ladder "unchanged" (milestone constraint) is satisfied literally: `environment_stage`, `promotion` and `PromotionService` are not touched.

How the two relate (a rule, not a foreign key):

- A client deployment optionally records the `stage_id` it was installed through? **No.** Per-client ladders are deferred (PROV-02). A client deployment is the fact "client C runs release R (of product P) since T". It carries no stage.
- Soft guard recommended: a client deployment requires the release to be FROZEN (a draft release cannot be shipped to a client). Do NOT require a PROD promotion: a client may install from UAT. Surface "release has not reached the Production stage" as a warning on the form, derived from `PromotionHistory`. This is an open product question (flagged below).
- "What each client runs" is derived on read, like current stage (D-07-03): the latest effective, non-VOIDED row per (client, product) by `installed_at`. Rollback is just a later row (an older release, or outcome ROLLED_BACK).

### Data model sketch (Flyway V14)

```
product                          client
  id UUID PK                       id UUID PK
  name VARCHAR(100) NOT NULL       name VARCHAR(100) NOT NULL
  retired_at TIMESTAMPTZ           retired_at TIMESTAMPTZ
  UNIQUE (lower(name))             UNIQUE (lower(name))
        |                                |
        |   product_client  (M:N join)   |
        +--- product_id FK, client_id FK, PK (product_id, client_id)

release_product                  client_deployment  (append-only, trigger like promotion)
  release_id FK release            id UUID PK
     ON DELETE CASCADE             release_id FK release  ON DELETE RESTRICT
  product_id FK product            product_id FK product  ON DELETE RESTRICT
     ON DELETE RESTRICT            client_id  FK client   ON DELETE RESTRICT
  PK (release_id, product_id)      outcome VARCHAR(16)  SUCCESS|FAILED|ROLLED_BACK|VOIDED
                                   installed_at TIMESTAMPTZ NOT NULL   -- "when" (user-entered, default now)
                                   recorded_at  TIMESTAMPTZ DEFAULT now()
                                   recorded_by  VARCHAR(255) NOT NULL
                                   note VARCHAR(2000)
                                   supersedes_id UUID, correction_reason VARCHAR(2000)
                                   FK (release_id, product_id) -> release_product   -- client can only get a release that covers the product
                                   FK (product_id, client_id)  -> product_client    -- client must belong to the product
                                   composite FK (supersedes_id, release_id) like promotion
                                   same CHECKs: correction has reason, VOIDED only by correction
                                   UNIQUE INDEX on supersedes_id WHERE NOT NULL
                                   INDEX (client_id, product_id, installed_at)
                                   TRIGGER BEFORE UPDATE OR DELETE -> raise (copy promotion_append_only)
```

Notes:
- The two composite FKs encode the business rules in the schema (IDOR/consistency backstop, same technique as `promotion_supersedes_same_release`), so `ClientDeploymentService` validation is a friendlier error, not the only guard.
- `product_client` is M:N because "product has many clients" does not say a client uses one product. If the user confirms strictly 1:N, make it `client.product_id` and drop the join; M:N costs one table and avoids a later migration. Open question #1.
- Products/clients are retired, never deleted (same convention as `environment_stage.retired_at`).
- `release_product` mutations go through `ReleaseService` and respect `Release.requireOpen()`. They must also bump `releases.items_changed_at` (as ticket changes do) or `DocumentReadinessService` will not mark documents stale when the products shown in a document change.
- A "what each client runs" SQL view `effective_client_deployment` (not superseded, not VOIDED) keeps the effective rule in one place; `DashboardService` today expresses it in Java over `PromotionHistory`, and a second Java copy for a second ledger is the drift risk.

### Modified existing code (Feature 1)

| Component | Change |
|-----------|--------|
| `Release` / `ReleaseService` / `ReleaseEntity` | Products set (ids) while OPEN; create-release request accepts product ids. Release stays valid with zero products (existing releases) |
| `ReleaseView` (template view-model) | Add `products` list (name only). Additive; existing templates keep working. Bump nothing in the placeholder allow-list except the new field |
| `DashboardReadRepository` + `DashboardController` | Extend or add a sibling read port: releases per product, effective client runs. Read-only rule stays |
| `IndexDocumentMapper` / assistant tools | Optional in v1.2: add products to the release document pushed to rag, and one read-only `clients` tool. Defer unless the user wants chatbot answers about clients; if added, it must pass the existing ArchUnit read-only rules |
| `ArchitectureBoundaryTest` | Add `ClientDeploymentRepository` to the "append-only: no update/delete/save" rule that already covers `PromotionRepository` |

### Data flow (Feature 1)

```
Record client install:  UI -> POST /api/releases/{r}/clients/{c}/deployments
   -> ClientDeploymentService: load release (must be FROZEN), product in release_product,
      client in product_client -> appendUnderLock (row lock on release) -> INSERT
Dashboard by client:    GET /api/dashboard/clients
   -> PortfolioDashboardService -> effective_client_deployment (latest per client,product)
      JOIN release (name/version) ; "behind" = a newer FROZEN release of that product exists
```

## Decision 2: Where the external-provider option lives

**Recommendation: in the app (ReleaseManagement), behind a `PlanningModelPort`, not in rag-service.**

| Criterion | In rag-service | In the app (recommended) |
|-----------|----------------|--------------------------|
| v1.1 invariant "Ollama is the only outbound call" (ArchUnit-enforced) | Broken for the whole service, for every client app, including release Q&A and doc assist that were promised no-egress | Intact. rag-service is unchanged on this axis |
| Who holds the third-party key | rag-service must hold it (config-only: restart to change, not "admin-configurable"), or receive it on every request (key in transit and request bodies, log-hygiene risk) | App already has an encrypted credential store (`EncryptedTokenConverter`) and a Settings screen pattern (`AdoCredentialController`) |
| "Admin enables it at runtime, says so clearly" | Needs per-request routing + runtime config API on a domain-free service | Singleton DB config row + UI banner; trivial |
| Blast radius of misconfiguration | Any client's data could silently go external | Only the planning feature's payload; Recommend/doc assist/chat keep using `RagTransformPort`/`RagChatPort` which can only reach Ollama |
| Reuse by other apps | Possible | Not needed now; if another app wants it, promote later (YAGNI) |
| Cost | New adapter + gate semantics + openapi change + re-run of the v1.1 outbound-rule tests | One adapter (~150 lines) reusing `SseStreamReader` (extracted in 14-05) and `RestClient` |

The planning feature does not need retrieval or tools: the app assembles a bounded context itself. So it needs only "text + instruction -> streamed text", which both Ollama (via the transform endpoint) and any OpenAI-compatible server provide.

Design:

```
PlanningService --> PlanningModelPort.stream(context, instruction, listener) -> ProviderInfo
                      |-- RagPlanningModel        (default)  -> RagTransformPort(profile=synthesize) -> rag-service -> Ollama
                      '-- OpenAiCompatibleAdapter (opt-in)   -> POST {base-url}/chat/completions stream:true
Selection: PlanningProviderService.current():
   external used only if  app.planning.external.allowed-hosts contains host   (ops-controlled, default empty)
                      AND planning_provider_config.enabled = true             (Settings, user-controlled)
```

Controls (make egress explicit and auditable):
1. **Two keys.** Env/property allow-list of permitted hosts (default empty = feature physically off) AND a Settings toggle. This substitutes for an admin role the app does not have, and doubles as SSRF control: the base URL is validated against the allow-list at save time and again at call time; HTTPS required except loopback.
2. `planning_provider_config` singleton row (Flyway V18): `enabled`, `base_url`, `model`, `api_key` (via `EncryptedTokenConverter`), `egress_acknowledged_by`, `egress_acknowledged_at`, `updated_by`, `updated_at`. The key is write-only over the API (return only "set / not set"), same as the ADO credential: a credential write must not decrypt (locked decision).
3. **Minimised payload.** Context for the external path contains only roadmap titles/scores, release names/versions/stage, client run summaries. Never commit messages, ticket descriptions, comments, document text. Enforce with a unit test that asserts the exact set of fields the external context builder can emit.
4. **Visible provider.** The stream's first event carries `{kind: LOCAL|EXTERNAL, host, model}`; the Planner page always renders it, and shows a persistent "data is sent to <host>" banner when EXTERNAL. Settings shows the exact payload field list.
5. Logs: metadata only (provider kind, host, sizes, outcome), never prompt, plan, or key (SAFE-03 style). Add the key and plan text to the log-hygiene canary tests.
6. ArchUnit: `OpenAiCompatibleAdapter` and `PlanningModelPort` may be referenced only from `PlanningService`/`PlanningProviderService`; `AssistantService`, `AssistantTransformService`, `RecommendEvidenceAssembler`, `IndexSyncer` must not depend on them. This keeps "Recommend uses commit text and therefore can never leave the machine" a build-time fact.
7. The plan is not saved server-side (no new PII/retention surface). Revisit only if the user asks.

Also decided: **Recommend (feature 4) is Ollama-only and never uses the external provider**, because it ships ADO commit text.

## Decision 3: AI deployment notes (Recommend)

Reuse the v1.1 doc-assist path; add one rag-service prompt profile.

```
UI (runbook viewer, AiAssistPanel: new "Recommend" action)
  -> GET  /api/assistant/transform/limits          (existing, add recommend max-chars)
  -> POST /api/assistant/transform  {mode: RECOMMEND, releaseId, sourceRevision}   (existing SSE endpoint, new mode)
       AssistantTransformService.prepare:
         latest runbook revision must equal sourceRevision (else RevisionSupersededException, as today)
         RecommendEvidenceAssembler.build(releaseId)   <- stored commits + PR title/status, NO ADO call
         instruction = configured recommend text (app.assistant.document-assist.instructions)
         size check via TransformSizeEstimator (same preflight/413 contract)
       run -> RagTransformPort.transform(evidence, instruction, profile=SYNTHESIZE, listener)
  -> UI shows generated suggestions beside the runbook; editor buffer = original runbook
     + "## Suggested deployment notes" + generated text; user edits
  -> POST save {mode: RECOMMEND, markdown}  -> AiRevisionService.save(... AiMode.RECOMMEND ...)
```

Required changes:

| Where | Change |
|-------|--------|
| rag-service `TransformRequest` / `TransformService` / `PromptRules` / `application.yml` | Optional `profile` field (`rewrite` default, `synthesize`) selecting `rag.prompts.synthesize-system`: evidence is data not instructions; may propose steps, config and DB changes, risks, validation checks; must cite ticket keys/commit ids from the evidence; must say "not in the evidence" instead of inventing; plain text/Markdown. Regenerate `openapi.json`; bump prompt hash. Backward compatible, so the app can deploy before or after |
| `RagTransformPort`/`RagTransformAdapter` | Pass the profile |
| `TransformMode` + `AiMode` | Add RECOMMEND |
| Flyway V15 | Widen `document_snapshot_ai_mode_check` to include 'RECOMMEND' (copy V13 pattern: DROP IF EXISTS + ADD) |
| `AssistantTransformService.prepare` | RECOMMEND branch: input text is the evidence pack, not `latest.markdown()`; only valid for the RUNBOOK document type |
| `AiRevisionService.save` | Unchanged. Because Recommend's saved markdown is "original + suggestions section", the save contract (client sends final edited markdown, as today) needs no new path |
| Frontend `documentAssist/` | RECOMMEND mode in `AiAssistPanel`, mandatory "verify before use" warning (already exists for runbook/approval), history badge "AI recommended" |

Evidence pack rules (this is the part most likely to go wrong):
- Group by ticket key; per ticket: PR titles with status, then commit messages. Cap commits per ticket and in total below the existing `app.template.render.max-collection-size` margin and the `num-ctx` budget (4096 tokens on the dev GPU is small: expect to prefer PR titles, truncate commit messages to the first line, and report "N commits omitted" in the UI before the call).
- Tickets with no code links are listed as "no code detail" so the model does not guess; if the release has no linked commits or PRs at all, disable Recommend with an explanation rather than asking the model to invent steps.
- Commit messages and PR titles are untrusted (prompt injection). They go in the fenced TEXT block; output is reviewed by a human before save; rendering already blocks remote images (`MarkdownBody`).
- Staleness: the revision records `basis_generated_at` as today. Show the evidence's last enrichment time in the UI so the reviewer knows how fresh the commits are.

## Decision 4: RICE roadmap

Roadmap items are not release items. Keep them out of `release_ticket_item` and out of enrichment/ADO paths.

```
roadmap_item  (Flyway V16)
  id UUID PK
  source VARCHAR(8) CHECK (JIRA|MANUAL)
  jira_key VARCHAR(32) NULL            -- UNIQUE WHERE NOT NULL
  title VARCHAR(500) NOT NULL          -- manual: user text; Jira: snapshot of summary
  description TEXT                      -- manual only; Jira snapshot of summary only, not description
  jira_status VARCHAR(100), jira_synced_at TIMESTAMPTZ, jira_site_url?        -- snapshot, display only
  product_id UUID NULL FK product       -- optional link, makes "roadmap by product" possible
  reach NUMERIC  CHECK (>= 0)           -- people/events per period
  impact NUMERIC CHECK (IN (0.25,0.5,1,2,3))
  confidence NUMERIC CHECK (BETWEEN 0 AND 100)    -- percent
  effort NUMERIC CHECK (> 0)            -- person-months
  scored_by/at, created_by/at, archived_at
```

- Score = `reach * impact * (confidence/100) / effort`. Implement once as a pure function (`RiceScorer`, domain) and rank in memory in the service. Item counts are in the tens to low hundreds, so no stored score, no generated column, no SQL duplicate of the formula (avoid "formula lives in two places"). Unscored items sort last and are flagged.
- Jira import uses the existing per-user path (`JiraAccessContextService` + `JiraTicketPort.searchAll`, the same machinery as the ticket picker): import selected tickets as `source=JIRA` rows; RICE inputs live only here. A Jira snapshot refresh is an explicit user action (needs the user's Jira credentials; there is no background Jira job and none should be added). Jira stays read-only: scores are never written back.
- Integrates with: dashboard? No. With planner: top-N ranked items feed `PlanningContextAssembler`. With products: optional `product_id`.
- Frontend: `roadmap/` folder, a `RoadmapRoute` with a sortable table (existing table patterns), an inline score editor, "Import from Jira" dialog reusing the ticket-search components.

## Decision 5: Usage tracking

Aggregate-only by construction: the table has no column that could hold identity.

```
usage_counter  (Flyway V17)
  day DATE NOT NULL
  event_key VARCHAR(80) NOT NULL        -- e.g. 'page.dashboard', 'feature.doc.export.pdf'
  count BIGINT NOT NULL
  PRIMARY KEY (day, event_key)
UPSERT: INSERT ... ON CONFLICT (day, event_key) DO UPDATE SET count = usage_counter.count + EXCLUDED.count
```

- Frontend: one `track(key)` function plus a router-level hook emitting `page.<route>` automatically (so new v1.2 pages are covered with no extra work). Events buffer in memory and flush as `POST /api/usage {events:[{key,count}]}` every ~30 s and on `visibilitychange`.
- Server: `UsageController` must not read the `Principal` at all, and the key must be validated against an allowlist (`UsageEventRegistry`, a code enum/list mirrored in the frontend). Unknown keys are rejected: this prevents cardinality blow-up and free-text/PII smuggling. Counts per request are capped.
- Privacy: no user id, no IP, no per-event timestamp finer than a day (day granularity also blocks re-identification in a single-digit-user tool). Check that any request-logging filter does not log the usage request body or principal for this route.
- Matrix: rows = event key (grouped page/feature), columns = week (or last 30 days), cell = count. One query, `GROUP BY event_key, date_trunc('week', day)`. Dashboard gets a tab, not a rewrite.
- Flush detail to verify in the phase: `navigator.sendBeacon` cannot set the CSRF header. Project memory says CSRF rests on SameSite=Strict while `csrf.spa()` still applies via a matcher; prefer `fetch(..., {keepalive: true})` through the existing `api/client.ts` so the normal header path is used. Confidence MEDIUM; verify against `SecurityConfig`.
- Excluded from the assistant index and tools (no value, avoids exposing counts as "facts").

## Decision 6: Daily planning assistant

```
GET  /api/planning/status          -> {provider:{kind,host,model}, ollamaReady, externalEnabled}
POST /api/planning/plan (SSE)      -> events: provider, token, done, error   (same BrowserStreamListener pattern)
GET/PUT /api/settings/planning-provider  -> admin config (key write-only)
```

`PlanningContextAssembler` builds a bounded text snapshot, server-side, never from client-sent text (same rule as doc assist):
- Top N ranked roadmap items (title, key, score, product)
- Open/frozen releases with derived current stage and failed stages (from `DashboardService`)
- Client runs that lag the latest frozen release (from `PortfolioDashboardService`)
- Stale document count (from `DocumentReadinessService`)
- Optional user free-text focus (length capped, NUL-checked, as `resolveInstruction` does)

For the Ollama path the budget is the same `num-ctx` pre-flight (`TransformSizeEstimator`); the larger context of an external model is NOT used to widen the payload (keeps behaviour and egress identical across providers).

Gate and availability: when `app.assistant.enabled=false` (rag integration off), the Ollama path is unavailable; the planner page shows the same offline state as the assistant. The external path works independently of rag-service and of Ollama, which also makes it the fallback some users will want; that is acceptable only because the toggle is explicit.

## Data Flow Changes Summary

| Flow | Before | After |
|------|--------|-------|
| Release creation | name/version/project/tickets | + product ids (optional) |
| Dashboard | releases, stages, deployments, churn, story points | + by-product, + client runs, + usage matrix tab |
| Runbook AI | transform stored markdown | + Recommend: evidence from stored commits/PRs via synthesize profile |
| Outbound network from app | Jira, ADO, rag-service | + one optional external LLM host, planning only, allow-listed |
| Outbound network from rag-service | Ollama only | Ollama only (unchanged) |
| DB writes at runtime | releases, items, docs, promotions | + client_deployment (append-only), roadmap_item, usage_counter, planning_provider_config |

## Patterns to Follow

### Pattern 1: Append-only ledger with derived "current"
**What:** Copy `promotion` for `client_deployment`: trigger blocks UPDATE/DELETE, corrections supersede, repository port has only `load` and `appendUnderLock`, current state derived on read.
**When:** Any audited fact that can be corrected.

### Pattern 2: Schema as backstop for cross-entity rules
**What:** Composite FKs `(release_id, product_id) -> release_product` and `(product_id, client_id) -> product_client` so an invalid install cannot exist even through a bug.
**Example:**
```sql
CONSTRAINT client_deployment_release_covers_product
    FOREIGN KEY (release_id, product_id) REFERENCES release_product (release_id, product_id),
CONSTRAINT client_deployment_client_has_product
    FOREIGN KEY (product_id, client_id) REFERENCES product_client (product_id, client_id)
```

### Pattern 3: Server assembles model input; client sends only a request
**What:** Recommend and planner inputs are built from stored data on the server, the client sends mode/ids. Matches the existing rule that nothing the client sends as document text reaches the model.

### Pattern 4: Port per outbound dependency, switch in config
**What:** `PlanningModelPort` with two adapters; selection logged as metadata; ArchUnit pins who may depend on the external one.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Client installs as promotions
**Why bad:** Corrupts shipped/median/per-stage dashboard figures and the audited ledger's invariants.
**Instead:** Separate ledger (Decision 1).

### Anti-Pattern 2: External provider inside rag-service
**Why bad:** Breaks the service's single-outbound guarantee for all clients, makes it hold third-party secrets, needs per-request routing.
**Instead:** Port + adapter in the app (Decision 2).

### Anti-Pattern 3: Reusing the rewrite prompt for Recommend
**Why bad:** The transform system prompt forbids adding facts and outputs plain text; the model will refuse to suggest steps or produce empty "unchanged" output, wasted live-test cycles on a 4 GB GPU model.
**Instead:** Separate `synthesize` profile with evidence-grounding rules.

### Anti-Pattern 4: Free-form usage event names or user-scoped counts
**Why bad:** Cardinality explosion and a back door for identity.
**Instead:** Allowlisted keys, day granularity, no principal read.

### Anti-Pattern 5: Stored RICE score
**Why bad:** Stale when an input changes; formula duplicated in SQL and Java.
**Instead:** Compute in `RiceScorer`, rank in memory.

## Suggested Build Order

Flyway numbers are pre-assigned so parallel work does not collide: V14 portfolio, V15 ai_mode RECOMMEND, V16 roadmap, V17 usage, V18 planning provider.

```
Phase A  Products & clients (V14)  ----+-----------------------------+
Phase B  Recommend (V15 + rag-service  |                             |
         synthesize profile)  [independent of A, can run parallel]   |
Phase C  RICE roadmap (V16)  [soft dep on A: product_id]             |
Phase D  Usage tracking (V17)  [independent; before E so the         |
         new pages are measured from day one]                        |
Phase E  Planning assistant + external provider (V18)  <-- needs A, C, and B's synthesize profile
```

1. **Products & clients.** Everything else reads from it (planner context, roadmap grouping). Contains the only schema-heavy design work (two composite FKs, append-only trigger) and the ReleaseView template field. Deliver: V14, product/client CRUD, release<->product, client deployment ledger, dashboard by product/client.
2. **Recommend (in parallel with 1 if capacity).** Touches rag-service prompt profile + doc-assist only. Needs a live model run, so start early: it carries the same unmeasured-quality risk as v1.1 doc assist.
3. **RICE roadmap.** Needs products (optional link) and the Jira picker components; no AI.
4. **Usage tracking.** Small; placing it after roadmap means the router hook covers all new pages; placing it earlier collects more history. Either order is safe since the router hook is generic. Matrix tab on dashboard last.
5. **Planning assistant + external provider.** Last: its context assembler consumes ranked roadmap, client runs and releases, and it reuses the synthesize profile from step 2. The external adapter, Settings screen, allow-list and ArchUnit rules are the bulk; Ollama-only planner can ship first, external provider as a second plan in the same phase.

## Scalability Considerations

| Concern | Current scale (single team) | If it grows |
|---------|-----------------------------|-------------|
| Client run view | derived by query over `client_deployment` with `(client, product, installed_at)` index | add a materialised current-run table only if a measured slow query appears |
| RICE ranking | in-memory over hundreds | SQL sort/generated column if > ~5k items |
| Usage counters | one row per (day, key): a few hundred rows/day at most | partition/roll up by month; not needed |
| Planner context size | bounded by `num-ctx` pre-flight | unchanged; deliberately not widened for external models |
| Generation concurrency | rag-service `GenerationGate` serialises Ollama use; planner and Recommend compete with assistant chat on one GPU | external path bypasses the gate by design; expect busy states, reuse existing 429/busy UI |

## Open Questions (need a user answer before the phase plan)

1. Product<->client cardinality: can one client use several products? (Sketch assumes yes via `product_client`.)
2. Must a release be FROZEN, and must it have reached Production, before a client install can be recorded? (Sketch: FROZEN required, Production only warned.)
3. Is the external provider's "admin" simply any signed-in user plus an ops allow-list, or is a real admin concept wanted? (Sketch assumes the former; the app has one permission level.)
4. Should the generated daily plan be persisted/historised? (Sketch: no.)
5. Should the chatbot know about products/clients (index mapping + tool)? (Sketch: defer.)

## Research flags for phases

| Phase | Flag |
|-------|------|
| A Products & clients | Standard patterns; decide Q1/Q2 first. Check `DocumentReadinessService` staleness bump on product change. |
| B Recommend | Needs deeper research and live measurement: synthesize prompt quality on `qwen3:4b-instruct` with a 4096-token budget, evidence truncation policy, injection via commit text. Use `/gsd-ai-integration-phase`. |
| C RICE | Standard. Confirm scale choices (impact values, effort unit) with the TPMs. |
| D Usage | Small; verify CSRF/keepalive flush path against `SecurityConfig`, and request-log hygiene. |
| E Planner | Needs research: OpenAI-compatible streaming dialect differences across providers (usage chunk, `[DONE]`, error shapes), SSRF handling for the configured URL, plan prompt quality. Use `/gsd-ai-integration-phase`. |

## Sources

- Codebase (HIGH): `V10__environment_ladder_promotion.sql`, `V9__ado_enrichment.sql`, `V12/V13` migrations, `PromotionRepository`, `DashboardReadRepository`, `DashboardService`, `AssistantTransformService`, `AiRevisionService`, `RagTransformPort`, `AiMode`, `AiAssist`, `EncryptedTokenConverter`, `SecurityConfig`, `ArchitectureBoundaryTest`, `rag-service` `ChatPort`/`TransformService`/`TransformRequest`/`application.yml` (`rag.prompts.transform-system`).
- `.planning/PROJECT.md` and `.planning/milestones/v1.1-ROADMAP.md` (HIGH): decisions on standalone rag-service, no-egress, immutable snapshots.
- Not externally researched: OpenAI-compatible streaming API details (training knowledge, LOW; verify in phase E). RICE formula and impact scale are the standard Intercom definition (training knowledge, MEDIUM).
