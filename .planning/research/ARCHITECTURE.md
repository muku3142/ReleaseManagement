# Architecture Research

**Domain:** Internal release-management tool (Spring Boot + React) — ticket aggregation, template-driven document generation, deployment tracking
**Researched:** 2026-07-27
**Confidence:** MEDIUM-HIGH (established patterns — hexagonal architecture, Resilience4j, anti-corruption layer, Spring provider selection — are well-documented and cross-verified; Jira's dev-status/ADO-link mechanism is confirmed to exist but is an unofficial/undocumented API and needs verification against the real Jira instance, as already flagged in PROJECT.md)

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  React SPA (Vite build, served as static assets by Spring Boot)      │
│  Release list/detail · Ticket picker · Template editor+preview       │
│  Document viewer/export · Environment ladder view                    │
└───────────────────────────────┬────────────────────────────────────┘
                                 │ REST/JSON (same origin in prod)
┌────────────────────────────────▼────────────────────────────────────┐
│                      Web Layer (Spring MVC Controllers)               │
│   ReleaseController · TicketSearchController · TemplateController     │
│   DocumentController · PromotionController                            │
│   (Controller DTOs — never JPA entities, never domain objects)        │
├────────────────────────────────┬──────────────────────────────────────┤
│                   Application Layer (use-case services)               │
│  ReleaseAssemblyService · TicketEnrichmentService                     │
│  TemplateRenderingService · DocumentGenerationService                 │
│  PromotionService                                                      │
├──────────────┬──────────────────────────────┬─────────────────────────┤
│  Domain Model │        Ports (interfaces)     │   (no framework deps)  │
│  Release      │  TicketProvider                │                       │
│  Ticket       │  CodeReferenceEnrichmentPort   │                       │
│  Environment  │  TemplateRenderer               │                       │
│  Promotion    │  ReleaseRepository / ...        │                       │
│  Template     │                                 │                       │
├──────────────┴──────────────────────────────┴─────────────────────────┤
│                          Adapters (outbound)                           │
│  jira/  JiraClient → JiraTicketProvider (implements TicketProvider)    │
│  ado/   AdoClient → AdoEnrichmentAdapter (implements enrichment port)  │
│  render/ PebbleTemplateRenderer (implements TemplateRenderer)          │
│  persistence/ JPA entities + Spring Data repositories                 │
├─────────────────────────────────────────────────────────────────────┤
│  Resilience wrapper (Resilience4j: Retry, CircuitBreaker, TimeLimiter,│
│  RateLimiter) around JiraClient and AdoClient calls only               │
├─────────────────────────────────────────────────────────────────────┤
│                    PostgreSQL/relational DB (single instance)         │
│  releases · release_tickets · promotions · templates · documents      │
└─────────────────────────────────────────────────────────────────────┘
```

This is a modular monolith, not a distributed system. One Spring Boot process, one database. The internal boundaries above (domain / ports / adapters) exist to isolate Jira- and ADO-specific concepts and to make the template contract stable — not to prepare for splitting into services. See Anti-Patterns for an explicit rejection of microservices/CQRS/messaging for this project.

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|-------------------------|
| Domain model | Provider-neutral `Ticket`, `Release`, `Environment`, `Promotion`, `Template` types; business rules (can a release promote? is a ticket enrichable?) | Plain Java records/classes, zero Spring/JPA/Jira imports |
| Ports | Interfaces the domain/application layer depends on: `TicketProvider`, `CodeReferenceEnrichmentPort`, `TemplateRenderer`, repository interfaces | Plain Java interfaces in a `ports` package |
| Jira adapter | Talks to Jira REST API, maps Jira JSON → domain `Ticket`; the only place that knows about issue types, custom fields, fix versions, JQL | `JiraClient` (HTTP) + `JiraTicketProvider` (implements `TicketProvider`) + `JiraTicketMapper` |
| ADO adapter | Given a link discovered on a Jira ticket, fetches commits/PRs; the only place that knows ADO's repo/commit/PR API shapes | `AdoClient` (HTTP) + `AdoEnrichmentAdapter` (implements `CodeReferenceEnrichmentPort`) |
| Application services | Orchestrate use cases across ports: assemble a release, enrich tickets, render a template, generate/store a document, promote a release | `@Service` classes, transactional boundaries, no HTTP/Jira/ADO knowledge |
| Template rendering | Resolve placeholders against a fixed, documented view-model — not raw domain/JPA objects | Pebble (or FreeMarker) engine fed a `TemplateContext` of plain view DTOs |
| Persistence | Store releases, ticket snapshots, promotions, templates, generated documents | Spring Data JPA + a relational DB (PostgreSQL recommended) |
| Web layer | REST endpoints, request/response DTOs, auth filter | Spring MVC `@RestController`, MapStruct or hand-written mappers |
| Frontend | Browse/select tickets, author/preview templates, view documents, view/promote environment ladder | React SPA (Vite), built and copied into Spring Boot's static resources for a single deployable artifact |

## Recommended Project Structure

```
src/main/java/com/<org>/releasemgmt/
├── domain/                        # Pure Java. No Spring/JPA/HTTP imports.
│   ├── ticket/                    # Ticket, TicketType, TicketStatus, CodeReference, EnrichmentStatus
│   ├── release/                   # Release, ReleaseTicket, Environment, Promotion
│   └── template/                  # Template, PlaceholderCatalog (metadata, not rendering)
├── ports/
│   ├── TicketProvider.java              # inbound-facing outbound port (fetch/search tickets)
│   ├── CodeReferenceEnrichmentPort.java # optional enrichment
│   ├── TemplateRenderer.java
│   ├── ReleaseRepository.java
│   └── TemplateRepository.java
├── application/
│   ├── ReleaseAssemblyService.java
│   ├── TicketEnrichmentService.java     # orchestrates the "conditional enrichment" fan-out
│   ├── TemplateRenderingService.java
│   ├── DocumentGenerationService.java
│   └── PromotionService.java
├── adapters/
│   ├── jira/
│   │   ├── JiraClient.java              # thin HTTP client (Spring RestClient), Jira DTOs only
│   │   ├── JiraTicketProvider.java      # implements TicketProvider; the anti-corruption layer
│   │   └── JiraTicketMapper.java
│   ├── ado/
│   │   ├── AdoClient.java
│   │   └── AdoEnrichmentAdapter.java    # implements CodeReferenceEnrichmentPort
│   ├── render/
│   │   ├── PebbleTemplateRenderer.java  # implements TemplateRenderer
│   │   └── view/                        # ReleaseView, TicketView, CommitView, PullRequestView
│   ├── persistence/
│   │   ├── entity/                      # JPA entities — separate from domain classes
│   │   ├── repository/                  # Spring Data JpaRepository interfaces
│   │   └── ReleaseRepositoryAdapter.java # implements ports.ReleaseRepository
│   └── web/
│       ├── ReleaseController.java
│       ├── TicketSearchController.java
│       ├── TemplateController.java
│       ├── DocumentController.java
│       ├── PromotionController.java
│       └── dto/                         # request/response records
├── config/
│   ├── ResilienceConfig.java             # Resilience4j registries for jira/ado
│   ├── SpaForwardingConfig.java          # forwards unknown paths to index.html
│   └── SecurityConfig.java
└── ReleaseManagementApplication.java

src/main/resources/static/            # React production build output lands here
frontend/                             # React app source (Vite), built by frontend build plugin
```

### Structure Rationale

- **`domain/` has zero framework imports.** This is the enforcement mechanism for the anti-corruption layer: if `domain.ticket.Ticket` cannot import `com.atlassian.*` or `jakarta.persistence.*`, Jira/JPA concepts physically cannot leak in. Enforce with a simple ArchUnit test, not just convention.
- **`ports/` is small and stable.** These interfaces are what `application/` depends on. Adding a second ticket provider later means adding an adapter that implements `TicketProvider` — zero changes to `application/` or `domain/`.
- **`adapters/render/view/` is a separate, deliberately duplicated model from `domain/`.** This is the template contract (see Pattern 2 below) — it must be allowed to lag behind or diverge slightly from the domain model so templates don't break every time the domain evolves.
- **JPA entities live under `adapters/persistence/entity/`, not in `domain/`.** Keeps `@Entity`/lazy-loading concerns out of business logic and out of the template view-model.
- **Package-by-feature inside each layer** (ticket/, release/, template/) rather than package-by-technical-role — keeps related classes discoverable as the codebase grows, without needing a module system for a project this size.

## Architectural Patterns

### Pattern 1: Provider port with a single adapter today, dispatch-ready tomorrow

**What:** Define `TicketProvider` as a plain interface expressed entirely in domain terms — no Jira vocabulary. Ship exactly one Spring bean implementing it (`JiraTicketProvider`). Do not build a plugin registry, `@ConditionalOnProperty` switch, or `ServiceLoader` SPI for this — there is nothing to switch between yet, and it can't be tested until a second provider actually exists.

**When to use:** Whenever a requirement says "add an abstraction now, implementations later" but only one implementation is being built now. This is the single highest-leverage anti-over-engineering call in the project.

**Trade-offs:** Slightly less "flexible" on day one, but avoids the two real failure modes: (a) building selection machinery around a single option, which is pure speculative cost, and (b) *not* abstracting at all and having Jira concepts (fix versions, custom fields, JQL) bleed into `Release`/`Ticket`, which is expensive to unwind later. The interface shape is the actual deliverable; the selection mechanism is deferred.

**Example:**
```java
// ports/TicketProvider.java — provider-neutral, no Jira vocabulary
public interface TicketProvider {
    List<Ticket> search(TicketSearchCriteria criteria);
    Ticket fetch(TicketReference ref);
}

// domain/ticket/Ticket.java — the neutral domain model
public record Ticket(
    TicketReference reference,   // opaque key + source system tag, e.g. ("JIRA", "PROJ-123")
    String title,
    String description,
    TicketType type,             // domain enum: STORY, BUG, TASK — mapped from provider's issue type
    TicketStatus status,         // domain enum: OPEN, IN_PROGRESS, DONE — mapped from provider's workflow status
    String url,
    Optional<CodeReference> codeReference   // present only when ADO enrichment succeeded
) {}

// adapters/jira/JiraTicketProvider.java — the only class that knows Jira's shape
@Component
public class JiraTicketProvider implements TicketProvider {
    private final JiraClient client;
    private final JiraTicketMapper mapper;

    public List<Ticket> search(TicketSearchCriteria criteria) {
        var jiraIssues = client.searchIssues(mapper.toJql(criteria)); // JQL lives ONLY here
        return jiraIssues.stream().map(mapper::toDomain).toList();
    }
}
```

**Spring mechanics for when a second provider ships:** don't reach for `@ConditionalOnProperty` (that's for choosing one active bean per *deployment*, e.g. "this instance always talks to Jira"). If a single running instance ever needs to route to different providers by data (e.g. a `Release.providerType` field), inject `Map<String, TicketProvider> providersByBeanName` — Spring auto-populates this for you from all beans implementing the interface — or add a `boolean supports(ProviderType)` method to the port and inject `List<TicketProvider>`, then pick the one that supports the release's provider type. Both are standard, well-documented Spring idioms for "N implementations, runtime dispatch." Keep this out of v1 entirely; document the decision point instead.

### Pattern 2: Anti-corruption layer between Jira and the domain

**What:** `JiraTicketMapper` is the single translation point. It converts Jira's JSON (custom field IDs like `customfield_10021`, `fixVersions`, `issuetype.name`, `status.name`, dev-status panel data) into the neutral `Ticket`/`TicketType`/`TicketStatus` shapes. Nothing outside `adapters/jira/` ever sees a Jira field name, a JQL string, or a fix-version object.

**When to use:** Any time an external system's model differs from — or is expected to be replaced/extended alongside — the domain's model. This is exactly the DDD Anti-Corruption Layer pattern (Evans; also documented as an architecture pattern by Microsoft's Azure Architecture Center). The concrete test for whether the ACL is doing its job: grep the codebase outside `adapters/jira/` for `customfield`, `fixVersion`, or `JQL` — zero hits means the layer is holding.
**Trade-offs:** Requires an explicit mapping step (some boilerplate) instead of just deserializing Jira JSON straight into whatever shape is convenient. That boilerplate is the cost of being able to add Azure DevOps Boards or GitHub Issues later without touching `application/` or `domain/`.

### Pattern 3: A dedicated, versioned view-model as the template contract

**What:** Templates never render against domain entities or JPA entities directly. They render against a small, explicit set of "view" DTOs (`ReleaseView`, `TicketView`, `CommitView`, `PullRequestView`) built specifically to be shown to templates — flat-ish, documented, and stable by design even when the domain underneath changes shape.

**When to use:** Any time end users author logic/placeholders against server data (mail-merge, report generators, notification templates). This is the standard shape used by document-generation platforms (e.g., Apryse's "data model" concept for generate-via-template, Flowable's document-generation "context object") — expose a purpose-built, flattened context object, not the internal object graph.

**Trade-offs:** Two models to maintain (domain vs. view) instead of one, but this is what lets the domain evolve (e.g., add a `Ticket.epicLink` field) without silently breaking every template a release manager has already written. It's also what makes "document available placeholders" tractable — you document the view model, not the whole domain.

**Example:**
```java
// adapters/render/view/TicketView.java — the actual template-facing contract
public record TicketView(
    String key,            // "PROJ-123"
    String title,
    String type,           // "Bug" / "Story" (display string, not the domain enum)
    String status,
    String url,
    boolean hasCodeDetail, // drives {% if ticket.hasCodeDetail %} in templates
    List<CommitView> commits,
    List<PullRequestView> pullRequests
) {}
```

Detailed treatment of validation, missing-data handling and placeholder documentation is in the Anti-Patterns and Integration Points sections below, since these are the highest-risk decisions in the project.

## Data Flow

### Release assembly + enrichment flow

```
Release manager searches Jira
    ↓
TicketSearchController → ReleaseAssemblyService.search(criteria)
    ↓
TicketProvider (JiraTicketProvider) → JiraClient → Jira REST API
    ↓ (maps Jira JSON → domain Ticket, each Ticket carries an Optional link-to-ADO reference if Jira exposes one)
Release manager selects tickets → ReleaseAssemblyService.assemble(release, ticketRefs)
    ↓
persist Release + ReleaseTicket rows (snapshot of ticket data at assembly time)
    ↓
TicketEnrichmentService.enrichAll(release.tickets)
    ├── partitions tickets into { hasAdoLink } vs { noAdoLink }  → no-link tickets short-circuit, marked NOT_LINKED
    └── for hasAdoLink tickets: fan out in parallel (bounded concurrency) to CodeReferenceEnrichmentPort
          → AdoEnrichmentAdapter → AdoClient → Azure DevOps REST API (commits, PRs)
          → each result tagged ENRICHED or FAILED (with reason), never silently dropped
    ↓
Release now holds, per ticket: EnrichmentStatus ∈ {NOT_LINKED, ENRICHED, FAILED} + optional CodeReference
```

### Document generation flow

```
Release manager picks a template + release → DocumentController.generate(releaseId, templateId)
    ↓
DocumentGenerationService loads Release (with tickets + enrichment results) and Template
    ↓
builds TemplateContext: ReleaseView { ticketViews: [TicketView...] }   ← the versioned, documented contract
    ↓
TemplateRenderer (Pebble) renders template against TemplateContext
    ↓
rendered output is persisted as an immutable GeneratedDocument row
    (templateId + templateVersion + rendered content + generatedAt + generatedBy)
    ↓
Release manager previews/exports; re-generation creates a NEW GeneratedDocument revision, never mutates the old one
```

### Promotion flow

```
Release manager triggers promotion → PromotionController.promote(releaseId, targetEnvironment)
    ↓
PromotionService validates targetEnvironment is the next rung on the fixed ladder
    ↓
appends an immutable Promotion row (release, fromEnv, toEnv, timestamp, promotedBy)
    ↓
Release.currentEnvironment is derived from the latest Promotion row (or updated transactionally alongside the insert)
```

### Key Data Flows

1. **Jira is always the entry point; ADO is only ever reached from a Jira ticket's link data.** The system never independently queries ADO for a release — this is a one-directional dependency (Jira → ADO), never the reverse, and it's what keeps ADO genuinely optional.
2. **Domain objects flow one way through layers:** adapters map external JSON → domain objects → application services persist/orchestrate → view DTOs are built *from* domain objects for templates and *from* domain objects for REST responses. Nothing upstream of `adapters/` ever depends on a downstream shape.
3. **Ticket data is snapshotted at two points, for two different reasons:** once into `release_tickets` at assembly time (so a release's ticket set is stable even if Jira tickets are edited later), and again implicitly into each `GeneratedDocument` (so a previously issued document is reproducible verbatim even if the release's ticket set or the template is edited afterward).

## Scaling Considerations

This is a single-role internal tool: one Spring Boot instance, one release manager (or a small handful), releases measured in dozens per year, tickets per release in the dozens. There is no "scale to millions of users" trajectory here, and none should be designed for.

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current expected load (single team, dozens of releases/year, tens of tickets/release) | Single Spring Boot instance + single PostgreSQL instance is sufficient indefinitely. No caching layer beyond an in-process Caffeine cache for ADO enrichment results is needed. |
| Multiple applications/products sharing one instance (per PROJECT.md open question) | Add a tenant/application dimension to `Release` and scope queries by it — this is a data-modeling change, not an architecture change. Still one instance, one DB. |
| Heavier ADO enrichment (large releases, 100+ tickets, many with ADO links) | The thing that actually breaks first is ADO API rate limits / call volume, not the database or the JVM. Fix with batching + bounded-concurrency fan-out + caching (see Integration Points), not with infrastructure scaling. |

### Scaling Priorities

1. **First (and likely only) real bottleneck: ADO enrichment fan-out on large releases.** Naive per-ticket sequential calls are the risk, not compute or DB scale. Fix with parallel bounded fan-out, deduplication by repo/commit, and short-TTL caching (see Integration Points).
2. **Second, much smaller: Jira search pagination for very large ticket sets.** Standard cursor/pagination on the Jira client is enough; no architectural response needed.

## Anti-Patterns

### Anti-Pattern 1: Letting Jira concepts leak past the adapter boundary

**What people do:** Deserialize Jira's JSON straight into the domain model (or straight into JPA entities), because it's less code up front. `Ticket.fixVersions`, `Ticket.customFields.get("customfield_10021")`, or JQL fragments end up referenced in application services, in the template view-model, or in the database schema.
**Why it's wrong:** This is exactly the failure mode the provider abstraction requirement exists to prevent — adding Azure DevOps Boards or GitHub Issues later means reworking every place that assumed Jira's shape, which is the "core rework" the abstraction was supposed to avoid.
**Do this instead:** Force all Jira-shaped data through `JiraTicketMapper` in `adapters/jira/`. Enforce with an ArchUnit rule banning Jira/Atlassian imports outside that package.

### Anti-Pattern 2: Rendering templates against domain or JPA entities directly

**What people do:** Pass the `Release` JPA entity (or the domain `Release`) straight into the template engine's context because it's already there and has all the data.
**Why it's wrong:** Two distinct failures. (1) Security: template engines given live objects can invoke arbitrary getters/methods via reflection — a user-authored template is untrusted input by definition here, and this is precisely the Server-Side Template Injection risk class. (2) Stability: every time the domain model changes shape (rename a field, restructure an association), every previously authored template referencing that field silently breaks or silently renders blank.
**Do this instead:** Build a dedicated, flattened, explicitly-versioned view-model (`ReleaseView`/`TicketView`/`CommitView`/`PullRequestView`) that is the only thing templates ever see, and configure the template engine to disallow arbitrary reflection outside that whitelisted object graph (FreeMarker: `ALLOWS_NOTHING` `TemplateClassResolver`/restricted `ObjectWrapper`; Pebble: don't register extensions that expose arbitrary Java classes, keep the context to the DTOs above).

### Anti-Pattern 3: Sequential per-ticket external calls when assembling a release

**What people do:** Loop over the release's tickets and call the ADO API once per ticket, synchronously, one at a time.
**Why it's wrong:** This is the classic N+1 pattern applied to an external API instead of a database — a 50-ticket release becomes 50 sequential round trips, each paying full network latency, and it multiplies rate-limit exposure.
**Do this instead:** Collect all tickets needing enrichment first, fan out in parallel with bounded concurrency (a fixed-size executor or a semaphore — not unbounded, to respect ADO rate limits), and cache repo/commit/PR lookups so re-generating a document for the same release doesn't re-fetch everything.

### Anti-Pattern 4: Reaching for microservices, event sourcing, CQRS, or a message broker

**What people do:** Treat "pluggable provider," "audit trail," and "async enrichment" as signals that this needs a distributed, event-driven architecture.
**Why it's wrong:** This is a single-role internal tool with modest data volume and no independent scaling or deployment requirements between its parts. Event sourcing/CQRS would add a second read model, eventual-consistency handling, and replay infrastructure to solve a problem (audit trail, partial enrichment) that a `promotions` append-only table and an `EnrichmentStatus` enum solve directly. A message broker would add operational surface area (a new service to run, monitor, and secure) to fan out ADO calls that `CompletableFuture`/virtual threads handle in-process.
**Do this instead:** One Spring Boot process, one relational database, in-process async fan-out (Java virtual threads, `spring.threads.virtual.enabled=true` on Spring Boot 3.2+, are a good fit here — blocking-style code, real concurrency for the ADO fan-out, no reactive-stack rewrite needed) and plain append-only tables for audit history.

### Anti-Pattern 5: Building provider-selection infrastructure before there's a second provider

**What people do:** Because the requirement says "add providers later," build a `ProviderRegistry`, a `ServiceLoader` SPI, or `@ConditionalOnProperty`-driven bean selection on day one, for a system that only has Jira.
**Why it's wrong:** This infrastructure is untestable and unvalidated until a second provider actually exists — it's speculative generality, the opposite of the "define the interface, defer the mechanism" principle this project should follow.
**Do this instead:** Ship one interface, one adapter, one Spring bean, directly autowired. Revisit selection mechanics (see Pattern 1) only when a second provider is actually being implemented.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Jira REST API | `JiraClient` using Spring's `RestClient` (Spring Boot 3.2+) with typed request/response DTOs, wrapped with Resilience4j (`@CircuitBreaker` + `@Retry` + `@TimeLimiter`) | Do not add the `jira-rest-java-client` dependency — Atlassian no longer actively supports it, and Jira Cloud in particular has no well-maintained official Java client; a thin typed HTTP client gives full control and no dead-dependency risk. Read-only usage only (per constraint). |
| Jira dev-status / development-information API (for ADO link discovery) | `JiraClient.getDevelopmentInfo(issueId)` calling `/rest/dev-status/1.0/issue/detail` | This endpoint is **not an official, versioned API** — it can change without notice between Jira versions and is the mechanism PROJECT.md already flags as needing verification. Isolate it behind its own method in `JiraClient` so if it changes (or the real link mechanism turns out to be smart-commits or a custom field instead), only this one adapter method needs to change — the domain/port contract (`Optional<CodeReference>` on `Ticket`) does not. |
| Azure DevOps REST API (commits, PRs) | `AdoClient` using Spring's `RestClient`, wrapped with its own Resilience4j instance (separate config from Jira's — different rate limits, different failure profile) | Fetch only repo-scoped commit/PR data, never work items (out of scope). Cache responses (Caffeine, keyed by repo+commit/PR id) since merged commit/PR data is immutable once fetched. |
| React build tooling | Vite build output copied into `src/main/resources/static` via a frontend build plugin (`frontend-maven-plugin` for Maven, or the Node/Gradle plugin for Gradle) as part of `mvn package` / `./gradlew build` | Produces one deployable jar. Dev workflow: run Vite's dev server separately with a proxy to the Spring Boot backend for hot reload; only bundle into the jar for packaging. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `application/` ↔ `ports/` | Direct method calls against interfaces, Spring-injected | `application/` never imports anything from `adapters/*` packages directly — only the interfaces in `ports/`. |
| `adapters/jira/` ↔ `adapters/ado/` | None — no direct coupling | The only connection between them is data: a `Ticket`'s optional ADO link, discovered by the Jira adapter and consumed by `TicketEnrichmentService`, which then calls the ADO port. Neither adapter package references the other. |
| `application/` ↔ `adapters/render/` | Through `TemplateRenderer` port, given a pre-built `TemplateContext` (view DTOs) | The rendering adapter never reaches back into `domain/` or JPA — it only receives what `DocumentGenerationService` hands it. |
| `adapters/web/` ↔ `application/` | Controllers call application services and map results to response DTOs | Controller DTOs are distinct from both domain objects and view DTOs — three separate models (domain, template-view, REST-DTO) that are allowed to diverge on purpose. |
| React SPA ↔ REST API | Same-origin HTTP/JSON in production (single jar); proxied dev server locally | Resource-oriented endpoints: `/api/releases`, `/api/releases/{id}/tickets`, `/api/releases/{id}/documents`, `/api/templates`, `/api/tickets/search`, `/api/releases/{id}/promotions`. A handful of action endpoints beyond CRUD: `POST /api/templates/{id}/validate`, `POST /api/templates/{id}/preview`, `POST /api/releases/{id}/documents/{type}/generate`, `POST /api/releases/{id}/promotions`. |

### Template validation & placeholder documentation (specific mechanism)

Because templates are user-authored and must reference only real fields, validate at **save time**, not just at render time:

1. Maintain the view-model classes (`ReleaseView`, `TicketView`, `CommitView`, `PullRequestView`) as the single source of truth for what's placeholder-addressable.
2. Build a **placeholder catalog** by reflecting over those record/class fields (with a small annotation like `@Placeholder("Ticket key, e.g. PROJ-123")` for human-readable docs) and render it as a reference page/panel in the template editor UI — this is what answers "how to document available placeholders to users."
3. On template save, run a **validation render**: render the template against a fully-populated synthetic sample context (a fake `ReleaseView` with fake tickets, commits, PRs, including at least one ticket with `hasCodeDetail = false`) in **strict mode** (Pebble's `strictVariables` or FreeMarker with no fallback default) so any reference to a non-existent field throws immediately, with the exact field path, instead of silently rendering blank at real-generation time. Store the validation result; block save (or show a warning) on failure.
4. This also directly answers "how to handle missing/optional data": the sample context used for validation must include a not-linked-to-ADO ticket, so any template that unconditionally accesses `ticket.commits[0]` fails validation with a clear message rather than throwing at generation time for a real release with an unlucky ticket.

## Sources

- [Hexagonal Architecture with Spring Boot — Baeldung-style overview](https://www.arhohuttunen.com/hexagonal-architecture-spring-boot/) — ports/adapters structure, MEDIUM confidence (widely corroborated pattern, not project-specific)
- [Hexagonal Architecture in Spring Boot: A Practical Guide](https://dev.to/jhonifaber/hexagonal-architecture-or-port-adapters-23ed)
- [Guide to Resilience4j With Spring Boot — Baeldung](https://www.baeldung.com/spring-boot-resilience4j) — Retry/CircuitBreaker/TimeLimiter/RateLimiter configuration and decorator ordering, HIGH confidence
- [The Anti-Corruption Layer: Protecting Your Domain from Legacy Systems](https://hosseinnejati.medium.com/the-anti-corruption-layer-protecting-your-domain-from-legacy-systems-6da58fc5f462) and [Anti-Corruption Layer Pattern — Azure Architecture Center (Microsoft Learn)](https://learn.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer) — HIGH confidence, canonical pattern documentation
- [Server-Side Template Injection: RCE for the modern webapp (Kettle, Black Hat)](https://blackhat.com/docs/us-15/materials/us-15-Kettle-Server-Side-Template-Injection-RCE-For-The-Modern-Web-App-wp.pdf) and [SSTI Prevention 2026](https://www.graphnodesoftware.com/blog/server-side-template-injection-ssti) — MEDIUM-HIGH confidence, security-critical, corroborates the "never expose live domain objects to a user-authored template" rule
- [Template Engines for Spring — Baeldung](https://www.baeldung.com/spring-template-engines) — Pebble/FreeMarker/Thymeleaf comparison, MEDIUM confidence
- [Document Generation Data Model — Apryse docs](https://docs.apryse.com/web/guides/generate-via-template/data-model) and [Flowable "Generate Document"](https://documentation.flowable.com/latest/reactmodel/bpmn/reference/generate-document) — MEDIUM confidence, cross-industry pattern for a dedicated "context object" as the template contract
- [The Jira Software Cloud REST API — development-information group](https://developer.atlassian.com/cloud/jira/software/rest/api-group-development-information/) and [REST API endpoint provides only a part of Development info (Atlassian bug tracker)](https://jira.atlassian.com/browse/JSWCLOUD-16901) — confirms dev-status/development-info API exists but is unofficial/inconsistent; LOW-MEDIUM confidence, flagged as needing project-specific verification (already an open question in PROJECT.md)
- [atlassian/jira-rest-java-client — Bitbucket](https://bitbucket.org/atlassian/jira-rest-java-client) and community discussion confirming reduced official support — MEDIUM confidence, informs "use a typed HTTP client, not this library" recommendation
- [Serving React apps from Spring Boot](https://blog.indrek.io/articles/serving-react-apps-from-spring-boot/) and [Run React Frontend and SpringBoot Backend on the same port... single artifact](https://medium.com/codex/run-react-frontend-and-springboot-backend-on-the-same-port-and-package-them-as-a-single-artifact-a790c9e10ac1) — MEDIUM confidence, standard well-corroborated single-jar deployment approach
- [Snapshot Pattern: Storing Periodic States for Backup or Audit](https://www.momentslog.com/development/design-pattern/snapshot-pattern-storing-periodic-states-for-backup-or-audit) — MEDIUM confidence, generic pattern applied here to "store rendered documents as immutable snapshots"
- [Harnessing Parallel Calls in Spring Boot Using CompletableFuture](https://medium.com/@shrivastavamohit628/harnessing-the-power-of-parallel-calls-in-java-spring-boot-using-completablefuture-a678ef22edde) — MEDIUM confidence, standard fan-out pattern for the enrichment step

---
*Architecture research for: Spring Boot + React release management tool with pluggable ticket providers, user-authored templates, and deployment tracking*
*Researched: 2026-07-27*
