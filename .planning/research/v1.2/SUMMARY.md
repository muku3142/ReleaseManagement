# Project Research Summary — v1.2 Portfolio & Planning

**Project:** ReleaseManagement
**Researched:** 2026-10-20
**Confidence:** MEDIUM overall (HIGH on integration points read from code; MEDIUM/LOW on external-tool behaviour, OpenAI streaming dialect, adblocker behaviour)

## Executive Summary

v1.2 needs **zero new dependencies** in the app, rag-service or frontend. It is five slices of ordinary domain
code — products/clients, RICE roadmap, usage counters, runbook Recommend (AI deployment notes) and a daily
planner — each copying a pattern already in the repo. Flyway V14-V18, one migration per slice.

- Client installs copy the `promotion` ledger (trigger-enforced append-only, `supersedes_id` corrections,
  composite FKs, current state derived on read) but live in their **own** `client_deployment` table.
- RICE score = reach x impact x (confidence/100) / effort, computed in Java, never stored.
- Usage = `usage_counter(day, event_key, count)` upsert with an allowlisted vocabulary and no identity column.
- External LLM key reuses `EncryptedTokenConverter`, write-only.
- All AI output is a human-reviewed draft saved as an AI-assisted revision.

Main risks: client installs conflated with promotions (corrupts the ladder and every dashboard figure);
hallucinated or injected output from the 4B dev model (each AI phase needs a golden-set gate *before* human
approval — the v1.1 lesson); data egress to the external provider; the usage "no identity" promise broken by
an audit or request-log filter on the ingest route.

## Key Findings

### Stack (STACK.md)
- No additions. Do not add Spring AI, LangChain4j, an OpenAI SDK, analytics SaaS, a chart library, Quartz/ShedLock, a RICE rules engine or a global state library.
- Frontend has no chart library; the usage matrix and portfolio views are shaded HTML tables.

### Features (FEATURES.md)
- **Products/clients** — table stakes: M:N release-product, client under product, append-only install record with back-dating, install only if the release covers the client's product; differentiators: "behind latest" badge, bulk install.
- **RICE** — Impact {0.25, 0.5, 1, 2, 3}, Confidence 0-100, Effort > 0; unscored items sort last with a deterministic tie-break; Jira import via the existing provider and `/search/jql`; local inputs never overwritten by refresh.
- **Usage** — day x event counters, route templates not ids, no visitors/sessions/cookies; dashboard labelled approximate.
- **AI deployment notes** — new doc-assist mode; four fixed sections (deploy steps, config/DB changes, risks, validation checks); each item cites a ticket key, PR id or commit sha; "insufficient data" when ADO evidence is thin.
- **Planner** — deterministic candidate set (releases in flight, pending promotions, docs not ready, top RICE); the model only orders and explains; unknown ids dropped; works with no model.

### Architecture (ARCHITECTURE.md)

```
 React SPA ──> app (Spring Boot) ──────────────> rag-service ──> Ollama   (chat, doc assist, Recommend)
                 │  PlanningModelPort
                 ├── RagPlanningModel (default) ──> rag-service ──> Ollama
                 └── OpenAiCompatibleAdapter ─────> external host (planner only, two-key gated)
```

- `client_deployment` is a separate ledger; `environment_stage`, `promotion`, `PromotionService` untouched.
- **Blocking for Recommend:** rag-service `rag.prompts.transform-system` says "Do not add facts / plain text". Needs a backward-compatible optional `profile` (`rewrite` default, `synthesize`) on `/v1/transform` with its own prompt.
- `RecommendEvidenceAssembler` reads stored `release_item_commit` / `release_item_pull_request` rows — no live ADO call.
- A product change must bump `items_changed_at` so documents go stale.

### Pitfalls (PITFALLS.md) — top items
1. Client deployment vs promotion conflation; double-counting multi-product releases on the dashboard.
2. RICE: rubric drift, Effort 0, Jira refresh overwriting local inputs, mirroring whole projects.
3. Usage: PII via URLs/ids, StrictMode double counts, adblockers on "analytics"/"track" paths.
4. Recommend: uncited or hallucinated steps, silent truncation, unset `num_ctx`, injection via commit text (delimiters only reduce it; real controls are no tools + closed output + citation check).
5. External egress: silent fallback, logging prompts, SSRF on the base URL, key leaking into logs/audit/actuator.

## Resolved conflict: where the external provider lives

**In the app behind `PlanningModelPort`, not in rag-service.** Putting it in rag-service breaks the
ArchUnit-enforced "Ollama is the only outbound call" guarantee for every client and feature, and puts a
third-party secret in request bodies. In the app, ArchUnit pins the adapter to `PlanningService`, the
per-feature policy (external only for the planner, never Recommend) is enforceable, and the 14-02 SSRF guard,
audit trail and credential pattern already exist. Cost accepted: two egress paths, mitigated by a single
consumer and a single provider selector.

Smaller resolutions: Recommend output is Markdown with four fixed headings plus server-verified citations;
the planner uses a closed candidate set (overrides ARCH's free-text idea); usage flush uses `fetch` keepalive
via `api/client.ts`; endpoint has a neutral name; the plan is built from local DB data only.

## Implications for Roadmap

1. **Products, clients, client-deployment ledger (V14)** — avoids Pitfalls 1-2.
2. **Portfolio dashboard views** — releases by product, client x product matrix, behind-latest badge; fixture with one release in two products asserts totals.
3. **RICE roadmap (V16)** — import, rubric validation, ranked table, change history.
4. **Usage tracking (V17)** — counters, allowlist, batching client, dashboard matrix.
5. **AI deployment notes / Recommend (V15)** — rag-service `synthesize` profile, evidence assembler, citation check, coverage report, golden set incl. injection. Start early; carries unmeasured model risk; parallel to 1-4.
6. **Planner + external provider (V18)** — Ollama first, external as a second plan; Settings, two-key egress, SSRF, banner, audit, ArchUnit, payload-shape test. Depends on 1, 3 and the `synthesize` profile.

### Research flags
- Phases 5 and 6: `/gsd-ai-integration-phase` (prompt quality at 4096 tokens, truncation, injection set; OpenAI-compatible streaming dialects, SSRF/DNS rebinding).
- Phase 4: short check of `SecurityConfig` CSRF with keepalive, request-log/audit filters on the route, manual adblock check.
- Phases 1-3: standard patterns.

## Open user questions
1. Can a client use several products? (default: yes, M:N)
2. Installable-stage rule: require FROZEN; require or only warn on reaching Production? (default: require FROZEN, warn)
3. Who may enable the external provider given one permission level? (default: any signed-in user + ops `allowed-hosts`, empty by default)
4. Persist daily plans? (default: no)
5. Chatbot coverage of products/clients? (default: defer)
6. RICE units: Reach and Effort units; Confidence free 0-100 or fixed {50, 80, 100}?

## Gaps
- Model quality for both AI features unmeasured on the dev GPU; production model needs its own measurement.
- Unknown whether ADO enrichment stores changed file paths.
- Unverified whether a request-log/audit filter attaches the principal to the usage route.
- Test V14 against a realistic v1.1 snapshot, not only an empty DB.
- Untracked `key.txt` and capture scripts in the working tree: stage by name; add ignore entries before phase 6.

## Out-of-scope candidates
Per-client ladders; auto-detected installs; timeline/Gantt/capacity/velocity; writes to Jira or ADO; AI-scored
RICE inputs; unique-visitor or per-user usage; auto-saved AI output; silent external fallback; planner write
actions.
