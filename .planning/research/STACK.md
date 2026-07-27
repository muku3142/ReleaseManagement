# Stack Research

**Domain:** Release management web application — Java Spring Boot API + React SPA, Jira-sourced release documents with optional Azure DevOps enrichment
**Researched:** 2026-07-27
**Confidence:** HIGH for backend framework/build tooling and template-safety analysis; MEDIUM for Jira/ADO integration mechanics (some reliance on an internal, undocumented Atlassian endpoint — flagged explicitly below); MEDIUM for frontend toolchain (fast-moving ecosystem, versions verified against npm registry same day)

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java | 25 (LTS) | Language/runtime | Latest LTS (GA 2025-09-16, supported to ~2028); Spring Boot 4.1 only requires 17 as a floor, but a greenfield project should start on the current LTS rather than an aging one. If organisational JDK policy is pinned to 17 or 21, both remain fully supported by Spring Boot 4.1. |
| Spring Boot | 4.1.0 | Application framework | Current GA (released 2026-06-10), built on Spring Framework 7 / Jakarta EE 11. Non-negotiable per project constraint. |
| Spring Web (MVC) | (Boot-managed) | REST API | Standard for a synchronous CRUD+orchestration API like this one. No reactive requirement exists here (no streaming, no high-concurrency fan-out) — do not reach for WebFlux, it adds complexity (Mono/Flux, non-blocking JDBC) with no corresponding benefit for a single-release-manager internal tool. |
| PostgreSQL | 18.x | Primary datastore | Mature, free, first-class Flyway/Testcontainers/Spring Data support. JSONB columns are a good fit for storing raw Jira/ADO payload fragments (e.g., a ticket's custom fields, dev-status snapshot) alongside relational tables for releases/environments/audit trail. |
| Flyway | (Boot-managed, currently 11.x line) | Schema migration | Default recommendation over Liquibase: SQL-first, no XML/YAML ceremony, free tier covers everything this project needs (no need for Liquibase's rollback-changelog or multi-DB-abstraction features since the DB choice is fixed to Postgres). Use `spring-boot-starter-flyway`/`flyway-database-postgresql` per Boot's dependency management rather than pinning a version by hand. |
| Spring Data JPA + Hibernate | (Boot-managed) | Persistence/ORM | Standard default for a Spring Boot CRUD-plus-audit-trail domain (releases, tickets, environments, promotions). Team velocity and hiring-pool familiarity favour it over jOOQ. For the handful of genuinely complex read queries (e.g., cross-release audit reports), drop to a native query or a JPA projection/DTO rather than reaching for jOOQ project-wide — that's a targeted escape hatch, not a second persistence stack. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-web` | Boot-managed | REST controllers, embedded Tomcat | Always |
| `spring-boot-starter-validation` | Boot-managed | Bean Validation (Jakarta) | Validate inbound API payloads (template CRUD, release assembly requests) |
| `spring-boot-starter-data-jpa` | Boot-managed | JPA/Hibernate | Always |
| `spring-boot-starter-actuator` | Boot-managed | Health/metrics/observability | Always — needed for `/actuator/health` in any real deployment, and readiness checks that reflect Jira/ADO reachability |
| `spring-boot-starter-oauth2-client` | Boot-managed | OAuth 2.0 client support | Only if you choose OAuth 2.0 (3LO) for Jira Cloud instead of API tokens — see Jira auth section below. Not needed for a token/PAT-based integration. |
| `flyway-database-postgresql` | Boot-managed | Postgres-specific Flyway dialect | Required alongside `flyway-core` for Postgres targets from Flyway 10+ |
| Java `HttpClient` (JDK 11+) or Spring 7 `RestClient` | JDK-bundled / Boot-managed | Outbound HTTP to Jira & Azure DevOps REST APIs | Use `RestClient` (Spring Framework 6.1+/7, synchronous, fluent) as the standard outbound HTTP client — it is the modern replacement for `RestTemplate` (which is in maintenance mode) and avoids pulling in WebFlux for a non-reactive app |
| Jackson (`spring-boot-starter-json`, bundled) | Boot-managed | JSON (de)serialization | Parsing Jira/ADO REST responses; also parsing/flattening Atlassian Document Format (ADF) — see Jira section |
| `com.samskivert:jmustache` | 1.16 | Template placeholder resolution engine | Rendering user-authored templates against release/ticket/commit data — see Template Engine section for why Mustache specifically |
| `com.vladsch.flexmark:flexmark-all` | 0.64.8 | Markdown parsing/rendering, MD→HTML, MD→PDF/DOCX helper modules | Turning the Mustache-resolved Markdown/HTML template output into other export formats |
| `com.openhtmltopdf:openhtmltopdf-pdfbox` (+ `-svg-support`, `-slf4j`) | 1.0.10 | HTML → PDF rendering | PDF export path: render the resolved template to clean XHTML, then to PDF. Actively maintained fork of Flying Saucer, rebuilt on PDFBox 3. |
| `org.docx4j:docx4j-JAXB-ReferenceImpl` (or `docx4j-core`) | 17.0.2 | DOCX generation | DOCX export path — see Document Export section for the recommended template-driven approach vs. raw OOXML manipulation |
| `org.wiremock:wiremock` | 3.13.2 (stable line; do not adopt the 4.0.0-beta.* line yet) | HTTP stub server for tests | Testing the Jira/ADO HTTP clients against realistic canned responses, error codes, timeouts |
| `org.testcontainers:testcontainers` + `postgresql` module | 2.0.5 | Ephemeral Postgres/WireMock containers in tests | Integration tests that need a real Postgres (Flyway migrations, JPA mapping) or a containerized WireMock instance |
| `org.junit.jupiter:junit-jupiter` | 6.1.2 (Boot-managed — check parent BOM alignment) | Test framework | Always |
| `org.assertj:assertj-core` | 3.27.x (stable; avoid `4.0.0-M1`, still a milestone) | Fluent assertions | Always |
| `org.mockito:mockito-core` | 5.23.0 (Boot-managed) | Mocking | Unit tests around the provider abstraction (mock the Jira/ADO ports) |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Maven or Gradle | Build | Either is fine; Spring Initializr defaults and most enterprise Java shops still lean Maven — use whichever matches existing organisational tooling. |
| Vite | 8.x | Frontend build tool/dev server | Standard for a React SPA in 2026; replaces CRA entirely (CRA is dead — see below) |
| TypeScript | 7.x | Type safety | Non-negotiable for a data-shape-heavy app (release/ticket/commit DTOs, template placeholder schemas) |

## Installation

```bash
# Backend (Maven) — pom.xml parent
# <parent>
#   <groupId>org.springframework.boot</groupId>
#   <artifactId>spring-boot-starter-parent</artifactId>
#   <version>4.1.0</version>
# </parent>
#
# Starters: spring-boot-starter-web, spring-boot-starter-data-jpa,
#           spring-boot-starter-validation, spring-boot-starter-actuator
# Add explicitly (not Boot-managed): flyway-database-postgresql, postgresql driver,
#   com.samskivert:jmustache:1.16, com.vladsch.flexmark:flexmark-all:0.64.8,
#   com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10, org.docx4j:docx4j-JAXB-ReferenceImpl:17.0.2
# Test scope: org.wiremock:wiremock:3.13.2, org.testcontainers:testcontainers + postgresql:2.0.5

# Frontend
npm create vite@latest release-management-ui -- --template react-ts

# Core
npm install @tanstack/react-router @tanstack/react-query react-hook-form zustand

# Editor (template authoring)
npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-mention @tiptap/suggestion

# Styling / components (pick one lane — see Alternatives)
npm install tailwindcss @tailwindcss/vite
npx shadcn@latest init

# Dev dependencies
npm install -D typescript vitest @testing-library/react @testing-library/jest-dom msw playwright
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| Spring MVC (servlet, blocking) | Spring WebFlux | Only if you need high-concurrency streaming or non-blocking I/O at scale — not applicable to a single-user internal release tool |
| Spring Data JPA/Hibernate | jOOQ | If the team is SQL-first and the domain is reporting/query-heavy rather than CRUD-heavy; jOOQ gives compile-time-checked SQL but costs more boilerplate for simple entity persistence |
| Flyway | Liquibase | If you need first-class rollback scripts or must support multiple database engines from one changelog; neither applies here since the DB is fixed to Postgres |
| Plain REST + `RestClient` for Jira | A third-party Jira Java client (`lesstif/jira-rest-client`, `bobcarroll/jira-client`) | If the team wants a typed domain model over Jira's API and is comfortable depending on a community project with modest maintenance activity. Not recommended as the default — see Jira section. |
| PostgreSQL | Microsoft SQL Server | If organisational standard mandates it (common in Azure/Java-Spring shops) — Spring Boot support is equally good, only the JDBC driver and a couple of Flyway/Hibernate dialect settings change |
| `jmustache` for the resolution engine | `mustache.java` (spullara) | Functionally equivalent Mustache implementation; spullara's compiles templates to Java bytecode (marginally faster, adds a runtime compilation step), jmustache is a pure interpreter with zero dependencies. Prefer jmustache for a simpler deployment story; either is safe by design (see Template Engine section). |
| TanStack Router + TanStack Query | React Router v7 (data mode) + TanStack Query | React Router v7 is more battle-tested and has a larger ecosystem/tutorial base; choose it if the team already knows it or if you later add SSR (this project doesn't need SSR — it's a pure SPA behind a REST API, which is exactly TanStack Router's sweet spot) |
| Tailwind + shadcn/ui | Mantine | Mantine ships 100+ ready components out of the box with less setup friction and is a better fit if the team wants to move fast without owning component code; shadcn/ui is preferred here because the app has a few bespoke, interaction-heavy surfaces (template editor with placeholder insertion, environment ladder visualization) where owning and freely modifying component internals matters more than component-count breadth |
| TipTap for the template editor | CodeMirror 6 (plain-text/code-style editor) | If templates should be authored as raw Markdown/Mustache text with syntax highlighting rather than WYSIWYG rich text — simpler to reason about "what does the raw template look like" but worse UX for a non-technical release manager inserting placeholders |
| docx4j for DOCX export | Apache POI (XWPF) | POI is more actively used for reading/writing spreadsheets; for Word documents docx4j has better OOXML template/content-control support. Consider POI only if you don't need docx4j's data-binding and already use POI elsewhere (e.g., if a future requirement adds Excel export). |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| FreeMarker for user-authored templates | Turing-complete expression language with a documented history of sandbox bypasses (`ObjectWrapper`/`TemplateClassResolver` escapes below FreeMarker 2.3.30, and further bypasses reported since); safe use requires expert-level lockdown (`SIMPLE_WRAPPER`, `ALLOWS_NOTHING_RESOLVER`, disabling `?new`) that is easy to get subtly wrong | Mustache (jmustache) — logic-less by design, no arbitrary class resolution to sandbox in the first place |
| Pebble Templates for user-authored templates | Same class of risk as FreeMarker: a documented CVE-class vulnerability (GHSL-2020-050) showed sandbox bypass via exposed Spring/Servlet objects leading to command injection; Pebble's own maintainers respond that the engine's input model assumes trusted authors, i.e., it is explicitly *not* designed for hostile template authors | Mustache (jmustache) |
| JTE for user-authored templates | JTE templates are compiled to JVM bytecode at build time; using it for runtime, user-supplied templates means compiling arbitrary user input into executable Java classes on your server, which is a code-execution primitive, not a templating one. JTE is excellent for developer-authored, precompiled view templates — wrong tool for this project's core requirement. | Mustache (jmustache) |
| Handlebars.java (jknack) as the primary engine | It's a superset of Mustache that allows custom Java helpers to be registered — safe only if you strictly control the helper registry and never expose one that does reflection/arbitrary method invocation; also the project is explicitly marked "NOT MAINTAINED" in its own README, though point releases (last: Aug 2025) still land for CVE fixes | Mustache (jmustache), which has no helper-registration attack surface to worry about because it deliberately has none |
| Thymeleaf for user-authored templates | Thymeleaf's expression language (OGNL/SpringEL under the hood) has its own history of sandbox-bypass CVEs when attacker input reaches the expression evaluator; it's designed for developer-authored server-side views, not end-user content | Mustache (jmustache) |
| Create React App | Officially deprecated/unmaintained; the React team's own docs no longer recommend it | Vite |
| `RestTemplate` for new outbound HTTP calls | In maintenance mode since Spring 5; no new features land in it | Spring's `RestClient` (synchronous, fluent, modern) |
| Atlassian's `jira-rest-java-client` (JRJC) | Atlassian does not maintain a Cloud-compatible official Java client; JRJC targets the legacy Server API shape and community sources describe it as effectively unsupported for Cloud | Plain REST calls (`/rest/api/3/...` for Cloud, `/rest/api/2/...` for Data Center) via `RestClient` + Jackson DTOs |
| Treating the Jira `dev-status` endpoint as a stable, versioned public API | `/rest/dev-status/{version}/issue/detail` is Jira's own internal endpoint that powers the Development panel in the UI — it is explicitly undocumented and Atlassian states it "may change without notice." There is no public, documented *read* endpoint for development-panel data on Cloud; the public `devinfo` API (`/rest/devinfo/0.10/...`, OAuth-scoped `read:dev-info:jira`) is oriented at *writing* dev info as a Connect/Forge integration provider, not reading it back as a consumer. | Use `dev-status` deliberately but defensively: wrap it behind your own provider abstraction, treat schema drift as expected, write contract tests (WireMock) against captured real responses, and design the ADO-enrichment path to degrade gracefully (already a stated project constraint) if the endpoint's shape changes |
| Microsoft "azure-devops-java-api" as an official SDK | This repository does not exist under `microsoft/` on GitHub (confirmed 404) — there is no official Microsoft-maintained Java client for Azure DevOps | Either plain REST calls with a PAT (recommended, given the narrow read-only surface needed — commits and PR metadata only), or the community project `org.azd:azure-devops-java-sdk` (formerly `azure-devops-java-api`, actively pushed as recently as July 2026, ~8.0.0) if broader ADO API coverage is anticipated later |

## Stack Patterns by Variant

**Jira flavour: Cloud**
- Base path `https://your-domain.atlassian.net/rest/api/3/...` (REST API v3)
- Auth: API token + email as HTTP Basic (`Authorization: Basic base64(email:token)`) is the pragmatic default for a single-tenant internal tool talking to one known Jira Cloud site. Atlassian has deprecated *password*-based basic auth and cookie-based auth, but token-based Basic auth remains supported and is what "API tokens" are for. Note: tokens now expire (org-configurable, up to 365 days) — build in a rotation/renewal admin flow, this is not "set once and forget."
- If the org's security policy requires OAuth: use OAuth 2.0 (3LO) via `spring-boot-starter-oauth2-client`, which is Atlassian's recommended path for anything beyond personal scripts and gives you short-lived access tokens + rotating refresh tokens. This adds real complexity (consent flow, token storage/refresh) — only take it on if mandated.
- Rich text fields (issue description, comments) come back as Atlassian Document Format (ADF) — a nested JSON structure, not plain text. You need an ADF→plain-text/HTML flattening step before that content can be dropped into a Mustache placeholder or a generated document. Budget for this explicitly; it is not optional parsing work.
- Development panel data (commits/PRs) is not available through a documented Cloud read API — see the `dev-status` entry above.

**Jira flavour: Data Center (self-hosted)**
- Base path `https://your-jira-host/rest/api/2/...` — Data Center has no v3; you get plain-string description/comment fields (no ADF), which simplifies rendering compared to Cloud
- Auth: Personal Access Tokens (PATs), introduced in Jira 8.14+, sent as `Authorization: Bearer <token>` — the modern, recommended method, replacing basic-auth-with-password for scripts/integrations
- Same `dev-status` endpoint applies (`/rest/dev-status/1.0/issue/detail?...`), same "internal/undocumented" caveat — Atlassian's own Data Center KB article walks through using it, which is a stronger signal of real-world stability here than on Cloud, but it's still explicitly not a supported public contract
- If the org runs the "Azure DevOps for Jira" / "Azure Boards for Jira" Marketplace app, that app is what populates the Development panel in the first place (by writing to `devinfo` using work-item-key references — smart-commit-style — found in branch names, commit messages, or PR titles/descriptions). Your system is a *consumer* of that already-linked data, not a producer of the link.

**Azure DevOps enrichment**
- Reached only after resolving a Jira ticket's dev-status/detail response to an Azure Repos branch/commit/PR reference — there is no independent "search ADO for this ticket" step
- Auth: Personal Access Token (PAT) scoped to `Code (Read)` only, sent as HTTP Basic with an empty username (`Authorization: Basic base64(":"+PAT)`) — this is Azure DevOps's standard REST auth pattern and needs no client library
- Endpoints needed are narrow: Get Pull Request by ID, Get Pull Request Commits, Get Commit (`https://dev.azure.com/{org}/{project}/_apis/git/repositories/{repo}/commits/{commitId}?api-version=7.1`) — three or four calls, which argues for a thin hand-rolled client over a full SDK dependency
- Graceful degradation (a stated project constraint) is naturally satisfied by this design: if the PAT is missing/invalid/unauthorized or the referenced repo/PR/commit 404s, catch it at the provider-adapter boundary and simply omit code detail for that ticket

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 4.1.0 | Java 17 (floor) through Java 26 | Recommend Java 25 (current LTS) as the project baseline |
| Spring Boot 4.1.0 | Spring Framework 7, Jakarta EE 11, Servlet 6.1 | Jakarta namespace (`jakarta.*`) throughout — if copying code samples, verify they're not `javax.*`-era (Boot 2.x) |
| `flyway-core` (Boot-managed) | `flyway-database-postgresql` (same version) | Since Flyway 10, database-specific dialect support moved into separate modules — both must be added and kept in lockstep |
| Vite 8 | Node.js current LTS (20.19+/22.12+ per Vite's own floor) | Verify CI runner's Node version before adopting Vite 8 |
| TanStack Router 1.x | TanStack Query 5.x | Same maintainer ecosystem, designed to be used together, share devtools patterns |
| TipTap 3.x | ProseMirror (transitive) | TipTap 3 is a rewrite of TipTap 2 — check any tutorial/example code is v3, not v2 syntax |

## Sources

- https://spring.io/blog/2026/06/10/spring-boot-4/ — Spring Boot 4.1.0 release, Java version support
- https://docs.spring.io/spring-boot/system-requirements.html — Spring Boot Java baseline
- Maven Central metadata (`repo1.maven.org/maven2/.../maven-metadata.xml`) fetched 2026-07-27 — verified current `<release>` versions for spring-boot-starter-parent, flyway-core, testcontainers, wiremock, assertj-core, mockito-core, postgresql driver, flexmark-all, jmustache, mustache.java, pebble, openhtmltopdf-pdfbox, docx4j — HIGH confidence (authoritative registry, checked same day)
- npm registry (`registry.npmjs.org/<pkg>/latest`) fetched 2026-07-27 — verified current versions for vite, typescript, react, @tanstack/react-query, @tanstack/react-router, react-router-dom, zustand, @tiptap/react, tailwindcss, vitest, @testing-library/react, playwright, msw, react-hook-form — HIGH confidence
- https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/ — Jira Cloud REST API v3 intro, ADF note — HIGH confidence
- https://developer.atlassian.com/server/jira/platform/about-the-jira-server-rest-apis/ — Data Center API v2 confirmation — HIGH confidence
- https://developer.atlassian.com/cloud/jira/platform/deprecation-notice-basic-auth-and-cookie-based-auth/ — Cloud auth deprecation specifics — HIGH confidence
- https://developer.atlassian.com/server/jira/platform/personal-access-token/ and https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html — Data Center PAT auth — HIGH confidence
- https://support.atlassian.com/jira/kb/access-git-repo-commit-data-via-jira-rest-api-in-development-panel-data/ — explicit statement that `dev-status` is internal/undocumented, with Data Center example — HIGH confidence (official Atlassian KB, states its own caveat)
- https://developer.atlassian.com/cloud/jira/software/rest/api-group-development-information/ — public `devinfo` write-side API and OAuth scopes — HIGH confidence
- https://support.atlassian.com/jira-software-cloud/docs/reference-issues-in-your-development-work/ — smart-commit-style work-item-key linking convention (branch/commit/PR) — HIGH confidence
- GitHub API check (`api.github.com/repos/microsoft/azure-devops-java-api` → 404) performed 2026-07-27 — confirms no official Microsoft Java SDK repo exists — HIGH confidence
- GitHub API check (`api.github.com/repos/hkarthik7/azure-devops-java-sdk` → pushed 2026-07-10, not archived) — community `azd` SDK is actively maintained — MEDIUM confidence (single community maintainer, breaking v6 rewrite in flight)
- https://securitylab.github.com/advisories/GHSL-2020-050-pebble/ and https://research.securitum.com/server-side-template-injection-on-the-example-of-pebble/ — Pebble sandbox-bypass CVE-class finding — HIGH confidence
- https://medium.com/@armaanpathan/breaking-the-barrier-remote-code-execution-via-ssti-in-freemarker-template-engine-9797079752ac and FreeMarker's own `ObjectWrapper`/`TemplateClassResolver` docs — FreeMarker sandbox limitations — MEDIUM-HIGH confidence (vendor docs + independent writeups agree)
- https://jte.gg/pre-compiling/ and https://github.com/casid/jte — JTE compile-time model, confirming unsuitability for runtime user templates — HIGH confidence (official docs)
- https://github.com/jknack/handlebars.java (README "NOT MAINTAINED" banner, v4.5.0 Aug 2025 release) — MEDIUM confidence
- https://github.com/danfickle/openhtmltopdf — actively maintained Flying Saucer fork on PDFBox 3 — HIGH confidence
- https://www.postgresql.org/about/news/postgresql-18-released-3142/ — PostgreSQL 18 GA — HIGH confidence
- WebSearch aggregate on Flyway vs Liquibase 2026 community consensus — MEDIUM confidence (multiple independent blog sources agree, no single canonical source)
- WebSearch aggregate on React/TanStack 2026 ecosystem recommendations — MEDIUM confidence (community consensus, not a single vendor source)

---
*Stack research for: Release management web application (Java Spring Boot + React SPA, Jira/Azure DevOps integration, user-authored document templates)*
*Researched: 2026-07-27*
