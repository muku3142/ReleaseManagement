---
phase: 01-foundation-authentication
plan: 03
subsystem: jira-credential-validation
tags: [spring-boot-4.1, restclient, wiremock, jira-cloud, problemdetail, hexagonal-architecture]
dependency-graph:
  requires:
    - "01-01: hexagonal package layout, GlobalExceptionHandler, ProblemDetail reason-code registry"
  provides:
    - "ports/JiraAuthPort: JiraIdentity validate(String siteBaseUrl, String email, String apiToken)"
    - "domain/auth/JiraAuthFailureReason (6 reasons): INVALID_CREDENTIALS, JIRA_FORBIDDEN, JIRA_RATE_LIMITED, JIRA_UNREACHABLE, JIRA_ERROR, SITE_NOT_CONFIGURED"
    - "GlobalExceptionHandler ProblemDetail mapping for JiraAuthenticationException (exhaustive switch, no default)"
    - "adapters/jira/JiraRestAuthAdapter (@Profile(\"!stub-jira\")) — live Jira Cloud /rest/api/3/myself validation"
    - "adapters/jira/StubJiraAuthAdapter (@Profile(\"stub-jira\")) — canned dev@example.com/valid-token pair"
    - "config/JiraRestClientConfig — RestClient bean with explicit connect/read timeouts, no base URL"
  affects:
    - "Plan 06 (session issuance) consumes JiraAuthPort.validate(...) verbatim"
    - "Plan 07 (frontend) branches on the six ProblemDetail 'reason' values"
    - "Phase 2's JIRA-06 branches on the same JiraAuthFailureReason taxonomy"
tech-stack:
  added:
    - "org.wiremock:wiremock-standalone:3.13.2 (replaces org.wiremock:wiremock — see deviations)"
  patterns:
    - "Anti-corruption layer: Jira wire field names (accountId/emailAddress) stay inside adapters/jira/, never escape to domain/auth or ports"
    - "Complementary @Profile(\"!stub-jira\")/@Profile(\"stub-jira\") pair — exactly one JiraAuthPort bean per profile combination, no selection machinery"
    - "Exhaustive switch over JiraAuthFailureReason with no default branch — a new reason without a mapping is a compile error, not a silent 500"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/auth/JiraIdentity.java
    - src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthFailureReason.java
    - src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthenticationException.java
    - src/main/java/com/evision/releasemgmt/ports/JiraAuthPort.java
    - src/main/java/com/evision/releasemgmt/config/JiraRestClientConfig.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraMyselfResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/StubJiraAuthAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/web/JiraAuthProblemDetailTest.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapterTest.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/StubJiraAuthAdapterTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - pom.xml
    - src/test/resources/application.yml
decisions:
  - "Used JdkClientHttpRequestFactory (spring-web) + java.net.http.HttpClient built directly, instead of Spring Boot's ClientHttpRequestFactorySettings/ClientHttpRequestFactoryBuilder — those classes now live in a spring-boot-http-client/spring-boot-restclient module that isn't on this project's classpath (spring-boot-starter-web in Boot 4.1's modularized layout doesn't pull it transitively), and adding it would mean touching pom.xml further into shared territory than needed for a same-outcome fix"
  - "Replaced org.wiremock:wiremock with org.wiremock:wiremock-standalone in pom.xml — the core wiremock artifact ships no HttpServerFactory SPI implementation on its own, and the natural companion (wiremock-jetty12) needs Jetty 12.0.30's org.eclipse.jetty:jetty-io API (WriteFlusher\$Listener), which Spring Boot's own dependency management force-upgrades to 12.1.10 project-wide, where that class no longer exists. wiremock-standalone shades its entire Jetty dependency under a wiremock/org/eclipse/jetty/... package, so it never collides with Boot-managed Jetty on the classpath"
metrics:
  duration_minutes: 55
  tasks_completed: 2
  files_created: 11
  completed_date: "2026-07-29"
---

# Phase 01 Plan 03: Jira Credential Validation Summary

`JiraAuthPort` with two `@Profile`-scoped adapters (live Jira Cloud `RestClient` call to `/rest/api/3/myself`, and a `stub-jira` canned pair) plus a six-reason `JiraAuthFailureReason` taxonomy mapped to distinct HTTP statuses via an exhaustive `GlobalExceptionHandler` switch, verified against WireMock for every upstream outcome.

## What Was Built

**Task 1 — Identity contract, failure taxonomy, ProblemDetail mapping.** `JiraIdentity` (record: accountId/email/displayName), `JiraAuthFailureReason` (6 values: `INVALID_CREDENTIALS`, `JIRA_FORBIDDEN`, `JIRA_RATE_LIMITED`, `JIRA_UNREACHABLE`, `JIRA_ERROR`, `SITE_NOT_CONFIGURED`), `JiraAuthenticationException`, and `JiraAuthPort` — all zero-framework-import, ArchUnit-enforced. `GlobalExceptionHandler` gained one new `@ExceptionHandler(JiraAuthenticationException.class)` alongside plan 01's existing three handlers, mapping each reason to a distinct `HttpStatus` and a fixed, leak-free message via an exhaustive `switch` with no `default` (a future unmapped reason fails compilation, not silently 500s). `JiraAuthProblemDetailTest` is parameterized over `JiraAuthFailureReason.values()`, asserting status, `reason` property, non-blank detail, and absence of `Basic `/`token=`/`Authorization` in every response.

**Task 2 — Live adapter, stub adapter, WireMock coverage.** `JiraRestClientConfig` builds one `RestClient` bean with explicit connect/read timeouts and no base URL (the site URL is passed per-call, per D-15/D-16). `JiraRestAuthAdapter` (`@Profile("!stub-jira")`) calls `GET {siteBaseUrl}/rest/api/3/myself` with `Authorization: Basic base64(email:token)`, catching `HttpClientErrorException.{Unauthorized,Forbidden,TooManyRequests}`, `HttpServerErrorException`, `ResourceAccessException`, and generic `RestClientException` to map to the six reasons (a null `accountId` in an otherwise-200 body also maps to `JIRA_ERROR`; a null `emailAddress` is tolerated, matching Jira Cloud's email-visibility setting). `JiraMyselfResponse` is `@JsonIgnoreProperties(ignoreUnknown = true)` and package-private, so Jira wire vocabulary never crosses into `domain/auth`. `StubJiraAuthAdapter` (`@Profile("stub-jira")`) accepts exactly `dev@example.com`/`valid-token`. `JiraRestAuthAdapterTest` covers all 9 required WireMock cases (200 success + Basic-header assertion, missing email, unknown fields, 401, 403, 429, 500, 3s-delay-vs-1s-timeout, empty-body/missing-accountId) and `StubJiraAuthAdapterTest` covers the canned pair plus rejection.

**RESEARCH.md Assumption A1 status:** confirmed. The WireMock-captured `/myself` shape (`accountId`, `emailAddress`, `displayName`, `active`) matched the assumed field names exactly — no DTO field-name correction was needed.

**Final `JiraAuthPort` signature:** `JiraIdentity validate(String siteBaseUrl, String email, String apiToken)` — three-argument form per the plan's deliberate deviation from RESEARCH.md's two-argument sketch (site URL is mutable runtime state, not baked into a startup `RestClient`).

**Six `JiraAuthFailureReason` values → HTTP status → `reason` string (plan 07 branches on these):**
| Reason | HTTP Status | `reason` property |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 Unauthorized | `INVALID_CREDENTIALS` |
| `JIRA_FORBIDDEN` | 403 Forbidden | `JIRA_FORBIDDEN` |
| `JIRA_RATE_LIMITED` | 429 Too Many Requests | `JIRA_RATE_LIMITED` |
| `JIRA_UNREACHABLE` | 503 Service Unavailable | `JIRA_UNREACHABLE` |
| `JIRA_ERROR` | 502 Bad Gateway | `JIRA_ERROR` |
| `SITE_NOT_CONFIGURED` | 409 Conflict | `SITE_NOT_CONFIGURED` |

**Stub credential pair:** email `dev@example.com`, API token `valid-token` → `JiraIdentity("dev-account-id", "dev@example.com", "Dev User")`; any other pair throws `INVALID_CREDENTIALS`.

## Verification

- `mvn test` — 29 tests, all green (7 `JiraAuthProblemDetailTest` + 9 `JiraRestAuthAdapterTest` + 2 `StubJiraAuthAdapterTest` + 3 `ArchitectureBoundaryTest` + 3 `JiraSiteConfigPersistenceIT` + 5 `JiraSiteConfigControllerTest`), no regressions from plan 01.
- `grep -rn "Basic " src/main/java/ | grep -v JiraRestAuthAdapter` → empty (Basic-auth construction exists in exactly one place).
- `grep -rn "accountId\|emailAddress" src/main/java/ --include=*.java | grep -v "adapters/jira\|domain/auth"` → empty (Jira wire vocabulary contained to the adapter package).
- `grep -rn "implements JiraAuthPort" src/main/java/` → exactly `JiraRestAuthAdapter` and `StubJiraAuthAdapter`, with complementary `@Profile("!stub-jira")`/`@Profile("stub-jira")` — exactly one bean active in any profile combination.
- All plan acceptance-criteria greps pass except the literal `withConnectTimeout|withReadTimeout` string match (see Deviations — the underlying requirement, explicit connect/read timeouts, is met via `connectTimeout(...)`/`setReadTimeout(...)`, verified functionally by the 3-second-delay-vs-1-second-timeout WireMock case mapping to `JIRA_UNREACHABLE`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `ClientHttpRequestFactorySettings`/`ClientHttpRequestFactoryBuilder` (org.springframework.boot.http.client) are not on this project's classpath in Spring Boot 4.1**
- **Found during:** Task 2, writing `JiraRestClientConfig` per the plan's literal code sketch
- **Issue:** Spring Boot 4.1's modularization moved RestClient auto-configuration and the `ClientHttpRequestFactorySettings`/`Builder` helper classes into separate `spring-boot-http-client`/`spring-boot-restclient` artifacts (confirmed present in the Boot 4.1.0 BOM, e.g. `spring-boot-starter-restclient`), which `spring-boot-starter-web` no longer pulls in transitively. Neither class exists anywhere on the compiled classpath (checked every jar).
- **Fix:** Built the `RestClient` using `org.springframework.http.client.JdkClientHttpRequestFactory` (already in `spring-web`) wrapping a `java.net.http.HttpClient` built with `.connectTimeout(...)`, and `requestFactory.setReadTimeout(...)` for the read timeout — both explicit and equally enforced, without adding a new pom.xml dependency into the parallel-plan-shared file.
- **Files modified:** `src/main/java/com/evision/releasemgmt/config/JiraRestClientConfig.java`
- **Commit:** 7c4ba25

**2. [Rule 3 - Blocking] `org.wiremock:wiremock` 3.13.2 alone cannot start a WireMock server — no bundled `HttpServerFactory`**
- **Found during:** Task 2, first `JiraRestAuthAdapterTest` run
- **Issue:** `FatalStartupException: Jetty 11 is not present and no suitable HttpServerFactory extension was found.` The core `wiremock` artifact ships no `META-INF/services` `HttpServerFactory` registration; it needs a companion extension. Adding `org.wiremock:wiremock-jetty12:3.13.2` resolved the SPI lookup but then failed with `NoClassDefFoundError: org/eclipse/jetty/io/WriteFlusher$Listener` — Spring Boot's own dependency management force-upgrades every `org.eclipse.jetty:*` artifact project-wide to `12.1.10`, while `wiremock-jetty12` 3.13.2 was built against Jetty `12.0.30`, whose `WriteFlusher$Listener` inner type was removed in the `12.1.x` line.
- **Fix:** Replaced the `org.wiremock:wiremock` + `wiremock-jetty12` pair with the single `org.wiremock:wiremock-standalone:3.13.2` artifact, which shades its entire Jetty dependency tree under `wiremock/org/eclipse/jetty/...` — completely isolated from Boot's managed `org.eclipse.jetty:jetty-io:12.1.10` on the classpath, so there is no version collision. The standalone jar still exposes the normal `com.github.tomakehurst.wiremock.junit5.WireMockExtension`/`@WireMockTest` API at the expected package path, so no test code changed.
- **Files modified:** `pom.xml`
- **Commit:** 7c4ba25

**3. [Rule 3 - Blocking] `src/test/resources/application.yml` shadows `src/main/resources/application.yml`, so `app.jira.connect-timeout`/`read-timeout` were unresolved when the full Spring context loaded `JiraRestClientConfig`**
- **Found during:** Task 2, `JiraSiteConfigPersistenceIT` (full-context Testcontainers test) failing with `PlaceholderResolutionException`
- **Issue:** Same root cause plan 01 already hit and documented (deviation #5 in `01-01-SUMMARY.md`) — the test classpath's `application.yml` fully replaces, rather than merges with, the main one. `JiraRestClientConfig`'s `@Value` bindings for the two new Jira timeout keys had no source once a full `@SpringBootTest` context assembled.
- **Fix:** Added `app.jira.connect-timeout: PT5S` / `app.jira.read-timeout: PT10S` to `src/test/resources/application.yml`, alongside the existing `default-base-url` test override, keeping the test fixture self-contained per the established pattern.
- **Files modified:** `src/test/resources/application.yml`
- **Commit:** 7c4ba25

None of the above required an architectural decision (Rule 4) — all were dependency/version-compatibility issues in Spring Boot 4.1's newly modularized artifact layout or in WireMock 3.13.2's own Jetty version pinning, corrected against the actual resolved classpath rather than the plan's illustrative code, with no change to the port's contract, the failure taxonomy, or the adapter's externally observable behavior.

## Known Stubs

None. Both adapters are fully wired: the live adapter makes a real Jira Cloud call with real Basic-auth headers and real error mapping; the stub adapter is a deliberate, documented, profile-gated fixture for local dev/CI, not a placeholder standing in for unfinished production code.

## Threat Flags

None beyond the plan's own `<threat_model>` (T-03-01 through T-03-07) — no new network endpoints, auth paths, or schema surfaces were introduced beyond what the plan specified. The `wiremock-standalone` substitution is a test-scope-only dependency change with no production runtime effect.

## Self-Check: PASSED
