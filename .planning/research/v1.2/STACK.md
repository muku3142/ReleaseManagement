# Technology Stack - v1.2 Portfolio & Planning (additions only)

**Project:** ReleaseManagement
**Researched:** 2026-10-20
**Scope:** only what the five new features need. v1.0/v1.1 stack is unchanged and not re-researched.
**Method:** inspected `frontend/package.json`, root `pom.xml`, `rag-service/pom.xml`, `ChatPort`, the existing `EncryptedTokenConverter` credential pattern and `RagTransformAdapter`/`RagChatAdapter`. No new library was evaluated against a registry, because the recommendation is to add none.

## Headline: zero new dependencies

Every v1.2 feature is buildable on the current stack. All five features are domain schema, REST endpoints, one new outbound LLM adapter, and plain React pages. A new dependency would be over-engineering by the project rules.

| Layer | New dependencies |
|-------|------------------|
| Backend (`pom.xml`) | none |
| rag-service (`rag-service/pom.xml`) | none |
| Frontend (`package.json`) | none |

## Per-feature decisions

### 1. Products and clients
| Concern | Decision | Why |
|---------|----------|-----|
| Storage | New Flyway migrations from V14: `product`, `client` (FK to product), release-to-product link, `client_installation` (client, release, environment, installed_at) | Pure relational domain; Spring Data JPA already fits. No library needed. |
| Dashboard views | Existing dashboard endpoint style plus new queries, using JPA projections or native queries for the by-product and by-client aggregates | Matches the existing "native query or projection for complex reads" guidance. Do not add jOOQ. |
| Naming | Call the domain concept `Client`/`ClientInstallation` in the main app only | rag-service already has `ClientApp`/`ClientService` meaning API consumers. They are separate deployables, so there is no code clash, but keep the glossary explicit to avoid confusion in plans and docs. |

### 2. RICE roadmap
| Concern | Decision | Why |
|---------|----------|-----|
| Score | `reach * impact * confidence / effort`, computed in Java with `BigDecimal` (scale 2, `HALF_UP`) in a domain class. Persist inputs only. Compute score on read, and sort in SQL with the same expression if paging is needed. | A formula this small needs no library or rules engine. Not persisting the score removes a stale-data class of bugs. |
| Jira items | Through the existing ticket-provider interface (`/search/jql`, cursor pagination) | The Jira-read-only constraint and provider seam already cover this. Store a snapshot (key, summary, status) plus the app-owned RICE inputs in a separate table keyed by ticket key. Never write back to Jira. |
| Manual items | Same table with a nullable ticket key | One ranked list, one code path. |
| UI | Plain table with TanStack Query and a sort by score | Rank is derived from the score, so no drag-and-drop reorder library is needed. |

### 3. Usage tracking (aggregate only, no identity)
| Concern | Decision | Why |
|---------|----------|-----|
| Capture | First-party. One `POST /api/usage/events` with a body like `{"events":["page:releases","feature:generate-doc"]}`. The frontend fires it from a router `onResolved` subscription (TanStack Router) and explicit `track(key)` calls, batched, using `fetch(..., {keepalive: true})`. | No SaaS (PostHog, Matomo, GA) can satisfy "no user identity, nothing leaves the network" without self-hosting a whole analytics stack. A counter endpoint is about 40 lines. |
| Why not `sendBeacon` | Prefer `fetch` with `keepalive`. | `sendBeacon` cannot set an `Authorization` header or a JSON content type without a Blob workaround. The existing auth model needs the header. CSRF rests on SameSite=Strict (locked decision). |
| Storage | One table `usage_counter(day date, event_key text, count bigint, primary key(day, event_key))`, written with `INSERT ... ON CONFLICT (day, event_key) DO UPDATE SET count = usage_counter.count + EXCLUDED.count` through a native `@Modifying` query (or `JdbcClient`, already in Boot). | Atomic, race-free, lock-light, and plain PostgreSQL. By construction the schema cannot hold identity. |
| Privacy guards | (a) The controller ignores the principal and never logs it for this route. (b) Event keys are validated against a server-side allowlist (regex or enum) so free text, IDs or emails cannot be smuggled into a key. (c) Batch size cap. (d) No IP, user agent or timestamp finer than the day. | "Aggregate only" needs structural enforcement, not convention. The allowlist is the key control. |
| Why not Micrometer | Do not use Micrometer or Actuator counters for this. | They are in-memory and reset on restart, and they are not dashboard-queryable business data. Keep Micrometer for ops metrics. |
| Matrix UI | An HTML `<table>` (feature rows by day or week columns), with cell shading by Tailwind opacity classes or inline `style` | The frontend has no chart library (grep found none). A matrix is a table. Do not add Recharts, Chart.js or visx for it. |

### 4. AI deployment notes (runbook Recommend mode)
| Concern | Decision | Why |
|---------|----------|-----|
| Path | Reuse the v1.1 AI doc-assist path: `RagTransformAdapter` in the main app, then rag-service transform endpoint, then the revision saved after review. Add a "recommend" mode and a prompt template. | Same revision semantics. No new transport. |
| Inputs | Release ADO commit messages, PR titles and PR status, already fetched by the ADO enrichment. Build the prompt in the main app. | The main app owns the data and the ADO graceful-degradation rule. With no ADO data, disable Recommend or say so. Do not call the LLM with an empty prompt. |
| Context budget | Cap commits and PRs and truncate per item before prompting. This is the real engineering risk on a 4 GB GPU with qwen3:4b-instruct (small context, weak long-context). | A deterministic cap in Java is safer than hoping the model copes. |
| Structured output | Ask for fixed headings (deploy steps, config/DB changes, risks, validation checks). Keep the output as Markdown so the existing revision, render and export path is unchanged. Do not introduce JSON-schema output. | Zero new parsing code, and the result stays human-reviewable. |
| Untrusted input | Commit messages and PR titles are untrusted text going into a prompt. Wrap them in delimited data blocks, and treat the output as a draft only (already saved as a revision after review). Nothing is executed. | Prompt injection is mitigated by human review and no tool access in this mode. |

### 5. Daily planning assistant and external OpenAI-compatible provider
| Concern | Decision | Why |
|---------|----------|-----|
| Client library | Hand-written `RestClient` adapter in rag-service implementing the existing `ChatPort`: `POST {baseUrl}/chat/completions` with `stream: true`, parsing `data:` SSE lines until `[DONE]`. **Do not add Spring AI.** | (1) rag-service already hand-rolls `OllamaChatAdapter` with its own cancellation, tool-call and refusal semantics on `ChatPort`; Spring AI would be a second, competing abstraction. (2) Large transitive footprint and fast-moving release train against a Boot 4.1 / Spring 7 baseline (compatibility not verified, so treat as risk). (3) The OpenAI chat-completions wire format is a stable, tiny surface: roughly 100 lines. (4) Project rule: no abstraction beyond need. |
| Single gateway | All LLM traffic, local or external, leaves through rag-service. The main app never calls an LLM provider directly. Selection happens inside rag-service behind `ChatPort` (a second bean, chosen per request). | Keeps one place for timeouts, the generation gate, log hygiene and the egress flag. `ChatPort.assertReady` and `assertToolsReady` need a provider-aware implementation. |
| Default | Ollama. The external provider is used only when an admin has enabled it. | Matches the egress requirement. |
| Secret storage | In the **main app** DB, reusing `EncryptedTokenConverter` and `key_version` (the AES pattern behind `AdoCredentialEntity`, keyed by `RM_CREDENTIAL_KEY`). Store `provider_config(enabled, base_url, model, encrypted_api_key, key_version, ...)`. The key is write-only: the API returns "set / not set" and never the value. A write must not decrypt (locked decision). | Reuses the one existing, tested crypto path. rag-service has no crypto today, and duplicating it there means a second key to manage and rotate. |
| Key transport | The main app passes the provider override (base URL, model, key) per request to rag-service over the existing service-to-service API-key channel. rag-service holds it in memory only for that call and must never log it. Extend the existing `LogHygieneIT` to cover it. | This is the one trade-off. The alternative is rag-service owning the secret, which means adding crypto and an admin write path there. Chosen: fewer moving parts. Revisit if rag-service ever serves other callers. |
| Egress controls | Admin-only settings write. Require `https` for the base URL. Reject userinfo in the URL, and consider blocking loopback and link-local hosts. Show a persistent "data leaves this network" banner on the planner and on AI doc-assist while enabled. Record enable and disable in the audit trail (existing). | The base URL is admin-supplied and the app makes server-side calls to it (SSRF surface). The flag requirement is explicit. |
| Planner data | Planner prompt input comes from existing release, promotion and (new) roadmap data. With an external provider, send only what the page needs and nothing that is not already on screen. | Limits egress. |
| Timeouts | Separate `RestClient` with its own connect and read timeouts, and no retry on a streamed completion | Retrying a partially streamed generation duplicates output. The generation gate already serialises the 4 GB GPU. Apply it only to Ollama; an external provider may allow concurrency. |

## Alternatives considered

| Category | Recommended | Alternative | Why not |
|----------|-------------|-------------|---------|
| External LLM client | `RestClient` adapter behind `ChatPort` | Spring AI (`spring-ai-starter-model-openai`) | Duplicates `ChatPort`, large footprint, Boot 4.1 / Spring 7 compatibility unverified, no benefit for one endpoint |
| External LLM client | `RestClient` adapter | `openai-java` or `langchain4j-open-ai` | Another SDK to track, own HTTP stack and logging that bypass our hygiene controls, no gain over 100 lines |
| Usage analytics | First-party counter table | PostHog, Matomo, Plausible (self-hosted) | A whole extra service to operate, with identity-by-default models. We need only counts. |
| Usage analytics | Counter table | Micrometer counters | Not persistent and not queryable for the dashboard |
| Matrix and charts | HTML table plus Tailwind | Recharts, Chart.js, visx, ECharts | No chart exists in the frontend today. A heatmap table needs none, and adding a charting library for one view fails the minimal-code rule. If trend lines are requested later, use hand-rolled inline SVG first. |
| RICE ordering | Sort by computed score | dnd-kit and manual ranking | RICE rank is derived, so manual reorder contradicts the model |
| Secret storage | Existing `EncryptedTokenConverter` | Vault or Spring Cloud Vault | Overkill for one secret, and the project already has an encrypted-at-rest pattern |

## Installation

```bash
# Nothing to install. No pom.xml or package.json changes.
# New work: Flyway migrations V14+ (main app), one rag-service adapter, frontend pages.
```

## What NOT to add (explicit)

- Spring AI, LangChain4j, any OpenAI SDK
- Any third-party analytics SaaS or self-hosted analytics platform; cookies, fingerprints or session IDs for usage tracking
- A chart library (Recharts, Chart.js, etc.) for the usage matrix or the roadmap
- Scheduling or distributed lock libraries (Quartz, ShedLock). The planner is user-triggered, and usage counters are written inline.
- A rules or scoring engine for RICE
- A second encryption implementation in rag-service
- A global state library. TanStack Query already covers server state.

## Integration points

| New piece | Touches | Notes |
|-----------|---------|-------|
| `usage_counter` table and `UsageController` | Security config | Authenticated, but the principal is ignored. Exclude the route from audit-actor logging. |
| Frontend `track()` and router subscription | TanStack Router in `frontend/src` | One module. Fail silently, and never block navigation. |
| `OpenAiCompatChatAdapter` in rag-service | `ChatPort`, `GenerationGate`, `OllamaRestClientConfig` pattern, `LogHygieneIT` | Per-request provider selection. Honour `Cancellation`. |
| `provider_config` entity | `EncryptedTokenConverter`, `RM_CREDENTIAL_KEY`, audit trail | Mirrors `AdoCredentialEntity`. |
| Recommend mode | `RagTransformAdapter`, ADO enrichment data, revision store | Prompt assembly and capping in the main app. |
| Roadmap | Jira ticket-provider interface | Read-only. Local table holds RICE inputs only. |

## Confidence

| Area | Level | Basis |
|------|-------|-------|
| No-new-dependencies conclusion | HIGH | Direct inspection of the poms, `package.json` and existing adapters |
| Counter-table and upsert approach | HIGH | Standard PostgreSQL `INSERT ... ON CONFLICT`, supported in Postgres 18 |
| `RestClient` over Spring AI | MEDIUM | Rests on the repo's existing `ChatPort` design and project rules. Spring AI's Boot 4.1 compatibility was not checked, so it is a risk, not a verified blocker. |
| OpenAI-compatible streaming format (`data:` lines, `[DONE]`) | MEDIUM | From training knowledge of the widely implemented chat-completions protocol. Not re-verified against current docs this session. Contract-test with WireMock against a captured response, as the repo already does for Ollama. |
| Per-request key transport vs rag-service-owned secret | MEDIUM | A design judgement. Flag for discuss-phase confirmation. |

## Sources

- Repo: `frontend/package.json`, `pom.xml`, `rag-service/pom.xml`, `rag-service/.../ports/ChatPort.java`, `AdoCredentialEntity.java`, `EncryptedTokenConverter` usage, `application.yml` (`RM_CREDENTIAL_KEY`)
- Project constraints in `CLAUDE.md` and the milestone brief (locked decisions: SameSite=Strict CSRF, write-only credentials that do not decrypt)
- OpenAI chat-completions streaming protocol (training knowledge, MEDIUM)
