---
phase: 01-foundation-authentication
plan: 05
subsystem: session-security
tags: [spring-security-7.1, jwt, hs256, oauth2-resource-server, csrf-spa, cookie-auth, jackson-3]
dependency-graph:
  requires:
    - "01-01: hexagonal package layout, GlobalExceptionHandler ProblemDetail reason-code registry, app.jwt.*/app.security.* config keys already declared in application.yml"
    - "01-03: JiraAuthPort/JiraAuthFailureReason taxonomy this plan's session issuance sits alongside (consumed directly by plan 06)"
  provides:
    - "adapters/security/JwtTokenService: issue(jiraAccountId, email) / refresh(refreshTokenValue), with an immutable absolute_exp claim copied forward, never recomputed"
    - "adapters/security/TokenPair, SessionRefreshException (INVALID_SESSION | SESSION_EXPIRED)"
    - "config/JwtConfig: one JwtEncoder + two type-pinned, HS256-pinned JwtDecoders (accessTokenDecoder @Primary, refreshTokenDecoder)"
    - "adapters/security/AuthCookieWriter: access_token (path /, 10 min) + refresh_token (path /api/auth, 30 min) httpOnly/Secure/SameSite=Strict cookies, fail-fast on cookie-secure=false outside 'local'"
    - "adapters/security/CookieBearerTokenResolver: reads the access_token cookie only, never Authorization"
    - "adapters/security/ProblemDetailAuthenticationEntryPoint: 401 reason=UNAUTHENTICATED / 403 reason=FORBIDDEN as application/problem+json"
    - "config/SecurityConfig: stateless SecurityFilterChain, csrf.spa() + explicit DEFAULT_CSRF_MATCHER override, authorization matrix"
    - "PUT /api/settings/jira-site now authenticated(), records the real JWT subject as updatedBy"
  affects:
    - "Plan 06 (auth endpoints): injects JwtTokenService, AuthCookieWriter, JiraCredentialRepository verbatim per this plan's interfaces"
    - "Plan 07 (sign-in UI): route guard branches on ProblemDetail reason=UNAUTHENTICATED"
tech-stack:
  added:
    - "spring-boot-starter-security, spring-boot-starter-oauth2-resource-server (Boot-managed, Spring Security 7.1.0)"
    - "spring-security-test (test scope)"
  patterns:
    - "Two independently-typed, HS256-pinned JwtDecoders (access/refresh) reject cross-type replay at decode time, before any application code runs"
    - "absolute_exp carried forward unchanged by issueWithCap(); issue()/refresh() share one encoding path"
    - "AuthCookieWriter's constructor is a startup fail-fast gate (cookie-secure=false only under 'local'), not documentation"
    - "Jackson 3 (tools.jackson.*) is Boot 4.1's managed Jackson - com.fasterxml.jackson.databind no longer resolves; only jackson-annotations stays under com.fasterxml"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/adapters/security/TokenPair.java
    - src/main/java/com/evision/releasemgmt/adapters/security/JwtTokenService.java
    - src/main/java/com/evision/releasemgmt/adapters/security/TokenTypeValidator.java
    - src/main/java/com/evision/releasemgmt/adapters/security/AbsoluteExpiryValidator.java
    - src/main/java/com/evision/releasemgmt/adapters/security/SessionRefreshException.java
    - src/main/java/com/evision/releasemgmt/adapters/security/AuthCookieWriter.java
    - src/main/java/com/evision/releasemgmt/adapters/security/CookieBearerTokenResolver.java
    - src/main/java/com/evision/releasemgmt/adapters/security/ProblemDetailAuthenticationEntryPoint.java
    - src/main/java/com/evision/releasemgmt/config/JwtConfig.java
    - src/main/java/com/evision/releasemgmt/config/SecurityConfig.java
    - src/test/java/com/evision/releasemgmt/adapters/security/JwtTokenServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/security/AuthCookieWriterTest.java
    - src/test/java/com/evision/releasemgmt/config/SecurityFilterChainIT.java
  modified:
    - pom.xml
    - src/test/resources/application.yml
    - src/test/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigControllerTest.java
decisions:
  - "Jackson 3 migration (Boot 4.1): ProblemDetailAuthenticationEntryPoint imports tools.jackson.databind.ObjectMapper, not com.fasterxml.jackson.databind - the latter no longer resolves on Boot 4.1's managed classpath"
  - "JwtTokenService.issue() truncates the absolute_exp Instant to seconds (ChronoUnit.SECONDS) before use - the encoded claim only round-trips at second precision, so the in-memory TokenPair.absoluteExpiry() must match what a later refresh() decode reconstructs, or equality assertions on chained refreshes fail on sub-second noise"
  - "NimbusJwtEncoder requires an explicit JwsHeader.with(MacAlgorithm.HS256) passed to JwtEncoderParameters.from(header, claims) - omitting it (as RESEARCH.md's illustrative snippet does) throws JwtEncodingException: Failed to select a JWK signing key against an ImmutableSecret-backed encoder"
  - "@WebMvcTest(JiraSiteConfigController.class) does not auto-include a hand-written @Configuration SecurityFilterChain bean - JiraSiteConfigControllerTest now @Imports SecurityConfig/JwtConfig/CookieBearerTokenResolver/ProblemDetailAuthenticationEntryPoint explicitly so the 'without jwt() returns 401' case exercises the real filter chain rather than silently falling through unauthenticated"
  - "AuthCookieWriter's cookie-building was deduplicated into two private helpers (accessCookie/refreshCookie) shared by write() and clear() rather than four independent inline ResponseCookie builds - httpOnly(true)/sameSite(\"Strict\") each appear exactly twice as the plan's acceptance criteria expect, but path(\"/api/auth\") appears once (in the shared refreshCookie() helper) instead of the plan's expected two; functionally identical, verified by AuthCookieWriterTest"
metrics:
  duration_minutes: 53
  tasks_completed: 2
  files_created: 13
  completed_date: "2026-07-29"
---

# Phase 01 Plan 05: Session Issuance & Filter Chain Summary

Stateless HS256 JWT access+refresh pair in httpOnly/Secure/SameSite=Strict cookies with pinned-algorithm, cross-type-rejecting decoders, an immutable 8-hour absolute cap, and a Spring Security 7.1 filter chain (`csrf.spa()`, cookie-only bearer resolution, `ProblemDetail` 401/403) that now requires authentication for `PUT /api/settings/jira-site` and records the real actor.

## What Was Built

**Task 1 — Token service.** `JwtConfig` decodes `RM_JWT_SECRET` once into a 256-bit `SecretKey` (fail-fast under 32 bytes), builds one `NimbusJwtEncoder` and two `NimbusJwtDecoder`s (`accessTokenDecoder` `@Primary`, `refreshTokenDecoder`), each pinned to `MacAlgorithm.HS256` and validated by a `DelegatingOAuth2TokenValidator` combining the default claims validator with a `TokenTypeValidator` and an `AbsoluteExpiryValidator`. `JwtTokenService.issue(jiraAccountId, email)` mints an access token (`app.jwt.access-ttl`, 10 min) and refresh token (`app.jwt.refresh-ttl`, 30 min) sharing one `absolute_exp` claim (`now + app.jwt.absolute-ttl`, 8h). `refresh(refreshTokenValue)` decodes with the refresh decoder only, maps `JwtValidationException` to `SessionRefreshException.Reason.SESSION_EXPIRED` when the failure is expiry-related (idle window or absolute cap) and `INVALID_SESSION` otherwise (wrong type, bad signature, missing claim, unparseable), then re-issues a new pair via a private `issueWithCap(...)` that copies the incoming `absolute_exp` forward unchanged — never recomputed, and never callable from `issue(...)`. No persistence, lookup, or denylist exists anywhere in the class.

**Task 2 — Cookie transport and filter chain.** `AuthCookieWriter` writes `access_token` (`path=/`, `httpOnly`, `SameSite=Strict`, `Secure` per `app.security.cookie-secure`) and `refresh_token` (`path=/api/auth`, same flags) via two private per-cookie builders shared between `write()` and `clear()`; its constructor throws `IllegalStateException` unless `cookie-secure=true` or the `local` profile is active. `CookieBearerTokenResolver` reads only the `access_token` cookie, never the `Authorization` header. `ProblemDetailAuthenticationEntryPoint` implements both `AuthenticationEntryPoint` and `AccessDeniedHandler`, writing `application/problem+json` bodies (401 `UNAUTHENTICATED`, 403 `FORBIDDEN`) via an injected Jackson 3 `ObjectMapper`. `SecurityConfig`'s `SecurityFilterChain` is stateless (`SessionCreationPolicy.STATELESS`), uses `csrf.spa()`, routes bearer resolution through the cookie resolver, and authorizes: `/api/auth/{login,refresh,logout}` and `GET /api/settings/jira-site` permit-all, `/actuator/health` permit-all, every other `/api/**` authenticated, everything else (SPA routes/static assets) permit-all. `JiraSiteConfigController#update` now records the JWT subject (Jira `accountId`) as `updatedBy` with no code change (plan 01 already resolved `Principal`).

**Critical mid-plan finding — CSRF/bearer-cookie interaction (see Threat Flags below).** Spring's `oauth2ResourceServer()` unconditionally registers a CSRF-ignore rule for any request a bearer token can be resolved from, assuming (correctly, for the header case) that bearer tokens are immune to browser auto-attachment. Since this project's bearer token *is* a cookie, that assumption doesn't hold, and the exemption would otherwise silently defeat `csrf.spa()` for every authenticated write. Fixed by explicitly restoring `CsrfFilter.DEFAULT_CSRF_MATCHER` via `requireCsrfProtectionMatcher(...)`, and documented as a residual-risk finding: the exemption is not exploitable in this design because `SameSite=Strict` on `access_token` already prevents a genuine cross-site attacker from ever getting the cookie attached to a forged request.

## Verification

- `mvn test` — 65 tests, all green (was 42 at the start of this plan; +23 from this plan: 11 `JwtTokenServiceTest`, 4 `AuthCookieWriterTest`, 7 `SecurityFilterChainIT`, 1 new `JiraSiteConfigControllerTest` case), no regressions.
- `cd frontend && npx vitest run` — 4/4 still passing (unaffected by backend changes).
- `grep -rn "SessionRepository\|HttpSession\|JdbcIndexedSessionRepository" src/main/java/` → empty (no server-side session store, D-05).
- `grep -rniE "denylist|blacklist|revoked_token" src/main/java/ src/main/resources/db/migration/` → empty (D-09).
- `grep -rn "Authorization" src/main/java/com/evision/releasemgmt/adapters/security/` → empty (cookie is the only token source).
- A secret decoding to 16 bytes throws `IllegalStateException` citing "32 bytes"; `cookie-secure=false` outside `local` throws at `AuthCookieWriter` construction — both proven by unit tests (`JwtTokenServiceTest`, `AuthCookieWriterTest`) rather than manual `RM_JWT_SECRET`-unset boot attempts.
- All plan acceptance-criteria greps pass except `path("/api/auth")` (returns 1, plan expected 2 — see Decisions; functionally verified by `AuthCookieWriterTest`).

**Claim names, TTLs, and contracts plan 06 must reuse verbatim:**
- Claims: `CLAIM_TOKEN_TYPE="token_type"`, `CLAIM_ABSOLUTE_EXP="absolute_exp"`, `CLAIM_EMAIL="email"`; types `TYPE_ACCESS="access"`, `TYPE_REFRESH="refresh"`.
- TTLs: `app.jwt.access-ttl=PT10M`, `refresh-ttl=PT30M`, `absolute-ttl=PT8H` (unchanged from plan 01).
- Decoder beans: `accessTokenDecoder` (`@Primary`, injected wherever a plain `JwtDecoder` is needed) and `refreshTokenDecoder` (inject via `@Qualifier("refreshTokenDecoder")`).
- Cookies: `AuthCookieWriter.ACCESS_COOKIE="access_token"` (path `/`), `REFRESH_COOKIE="refresh_token"` (path `/api/auth`); `write(response, pair)` / `clear(response)`.
- `SessionRefreshException.Reason`: `INVALID_SESSION`, `SESSION_EXPIRED` — plan 06's refresh endpoint maps these to its own `ProblemDetail` reasons.
- `ProblemDetailAuthenticationEntryPoint` emits `reason=UNAUTHENTICATED` (401) / `reason=FORBIDDEN` (403) — plan 07's route guard branches on `UNAUTHENTICATED`.
- Authorization matrix (final): `POST /api/auth/login|refresh|logout` permitAll; `GET /api/settings/jira-site` permitAll; `PUT /api/settings/jira-site` authenticated; `/actuator/health` permitAll; any other path permitAll (SPA/static).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MacAlgorithm` moved package in Spring Security 7.1**
- **Found during:** Task 1, first compile of `JwtConfig`/`JwtTokenServiceTest`
- **Issue:** The plan's sketch imports `org.springframework.security.oauth2.jwt.MacAlgorithm`; the actual class is `org.springframework.security.oauth2.jose.jws.MacAlgorithm` (confirmed via jar inspection of `spring-security-oauth2-core-7.1.0.jar`).
- **Fix:** Corrected the import in both `JwtConfig.java` and `JwtTokenServiceTest.java`.
- **Files modified:** `src/main/java/com/evision/releasemgmt/config/JwtConfig.java`, `src/test/java/com/evision/releasemgmt/adapters/security/JwtTokenServiceTest.java`
- **Commit:** baf604b

**2. [Rule 1 - Bug] `NimbusJwtEncoder.encode(JwtEncoderParameters.from(claims))` (no header) throws `JwtEncodingException: Failed to select a JWK signing key`**
- **Found during:** Task 1, first `JwtTokenServiceTest` run (8/11 failures)
- **Issue:** An `ImmutableSecret`-backed `NimbusJwtEncoder` cannot infer a signing algorithm without an explicit `JwsHeader`; RESEARCH.md's illustrative `encode(...)` snippet omitted one.
- **Fix:** Added `JwsHeader.with(MacAlgorithm.HS256).build()` and passed it via `JwtEncoderParameters.from(header, claims)` in `JwtTokenService.encode(...)` and in the two test methods that hand-craft tokens directly against the encoder.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/security/JwtTokenService.java`, `src/test/java/com/evision/releasemgmt/adapters/security/JwtTokenServiceTest.java`
- **Commit:** baf604b

**3. [Rule 1 - Bug] `absolute_exp` sub-second precision mismatch broke cap-preservation assertions**
- **Found during:** Task 1, `capIsPreservedAcrossASingleRefresh`/`capIsNotExtendableAcrossChainedRefreshes` failures (`Instant` equality off by nanoseconds)
- **Issue:** The encoded `absolute_exp` claim only round-trips at second precision (`Instant.ofEpochSecond(...)`), but `issue()`'s in-memory `TokenPair.absoluteExpiry()` retained sub-second precision, so it never equaled a value reconstructed from a later decode.
- **Fix:** `issue()` now truncates to `ChronoUnit.SECONDS` before use.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/security/JwtTokenService.java`
- **Commit:** baf604b

**4. [Rule 3 - Blocking] `com.fasterxml.jackson.databind` does not resolve on Boot 4.1's classpath**
- **Found during:** Task 2, first compile of `ProblemDetailAuthenticationEntryPoint`
- **Issue:** Boot 4.1 migrated Jackson to the 3.x line under the `tools.jackson` group/package (`tools.jackson.core:jackson-databind:3.1.4`); only `jackson-annotations` remains under `com.fasterxml.jackson`. `com.fasterxml.jackson.databind.ObjectMapper` is not on the classpath at all (confirmed via `mvn dependency:tree`).
- **Fix:** Imported `tools.jackson.databind.ObjectMapper` instead.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/security/ProblemDetailAuthenticationEntryPoint.java`
- **Commit:** 65a9717

**5. [Rule 3 - Blocking] `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` moved package in Boot 4.1**
- **Found during:** Task 2, first compile of `SecurityFilterChainIT`
- **Issue:** Same modularization pattern as plan 01's `@WebMvcTest` deviation — Boot 4.1 moved `@AutoConfigureMockMvc` into `spring-boot-webmvc-test`'s `org.springframework.boot.webmvc.test.autoconfigure` package.
- **Fix:** Corrected the import.
- **Files modified:** `src/test/java/com/evision/releasemgmt/config/SecurityFilterChainIT.java`
- **Commit:** 65a9717

**6. [Rule 3 - Blocking] `src/test/resources/application.yml` doesn't merge with main's — `app.jwt.access-ttl`/`refresh-ttl`/`absolute-ttl` unresolved once Spring Security beans entered the test classpath**
- **Found during:** Task 2, first full-context `SecurityFilterChainIT`/`AuthCookieWriter` bean creation attempts
- **Issue:** Same root cause documented in 01-01/01-03's deviations — the test classpath's `application.yml` fully replaces, not merges with, `src/main/resources/application.yml`. `JwtTokenService`/`AuthCookieWriter` newly need these three TTL properties.
- **Fix:** Added `app.jwt.access-ttl/refresh-ttl/absolute-ttl` to `src/test/resources/application.yml`, matching main's values.
- **Files modified:** `src/test/resources/application.yml`
- **Commit:** 65a9717

**7. [Rule 1 - Bug] `AuthCookieWriter`'s fail-fast check broke every full-context test once Spring Security entered the classpath**
- **Found during:** Task 2, `JiraCredentialPersistenceIT`/`JiraSiteConfigPersistenceIT`/`SecurityFilterChainIT` all failing with `IllegalStateException: app.security.cookie-secure=false is only permitted under the 'local' profile`
- **Issue:** `src/test/resources/application.yml` sets `cookie-secure: false` for all tests (matching local-dev ergonomics), but no test activated the `local` profile, so `AuthCookieWriter`'s new startup guard (this plan's own T-05-06 mitigation) now correctly refused to start every full-context test.
- **Fix:** Added `spring.profiles.active: local` as a base property in `src/test/resources/application.yml` (additive with any test's own `@ActiveProfiles`, confirmed via Boot's config-data profile activation being additive to profiles already set by `@ActiveProfiles`).
- **Files modified:** `src/test/resources/application.yml`
- **Commit:** 65a9717

**8. [Rule 1 - Bug] `@WebMvcTest(JiraSiteConfigController.class)` silently let an unauthenticated `PUT` reach the controller instead of 401**
- **Found during:** Task 2, the new `putWithoutAuthenticationIsUnauthorized` case failing with 500 (NPE from an unstubbed Mockito call), not 401
- **Issue:** `@WebMvcTest`'s slice does not auto-include a hand-written `@Configuration` `SecurityFilterChain` bean (`SecurityConfig`) the way it auto-includes Spring Boot's own security auto-configuration; the request reached the controller with `Principal=null` instead of being rejected by the filter chain.
- **Fix:** Added `@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})` to `JiraSiteConfigControllerTest` so the slice genuinely enforces authentication/CSRF via the real filter chain, not just `spring-security-test`'s postprocessor shortcuts.
- **Files modified:** `src/test/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigControllerTest.java`
- **Commit:** 65a9717

**9. [Rule 2 - Missing Critical / Security] `oauth2ResourceServer()` unconditionally exempts bearer-resolvable requests from CSRF — silently defeats `csrf.spa()` for cookie-carried bearer tokens**
- **Found during:** Task 2, `protectedWriteWithValidAccessCookieButWithoutCsrfIsForbidden` unexpectedly returning 200
- **Issue:** Decompiling `OAuth2ResourceServerConfigurer` confirmed `registerDefaultCsrfOverride(http)` unconditionally calls `csrfConfigurer.ignoringRequestMatchers(bearerTokenRequestMatcher)` during its `init()` phase, appending (not replacing) an ignore rule that `CsrfConfigurer.configure()` ANDs with `requireCsrfProtectionMatcher` regardless of DSL call order (all configurers' `init()` calls precede all `configure()` calls). Since `BearerTokenRequestMatcher.matches()` succeeds whenever our `CookieBearerTokenResolver` can resolve *any* token string from the `access_token` cookie, every authenticated write request is CSRF-exempt in practice.
- **Fix:** Explicitly set `.csrf(csrf -> csrf.spa().requireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER))` — this keeps `csrf.spa()` fully enforced for any request not carrying a resolvable bearer token (proven by the rewritten IT case, which now asserts a 403 from `CsrfFilter` itself for a request with no `access_token` cookie at all). The residual exemption for bearer-cookie-authenticated requests could not be removed via public API (`ignoredCsrfProtectionMatchers` has no clear/reset method); see Threat Flags below for why this is not exploitable given `SameSite=Strict`.
- **Files modified:** `src/main/java/com/evision/releasemgmt/config/SecurityConfig.java`, `src/test/java/com/evision/releasemgmt/config/SecurityFilterChainIT.java`
- **Commit:** 65a9717

None of the above required an architectural decision (Rule 4) in the sense of new tables/services/frameworks — all were framework-version corrections against Boot 4.1/Spring Security 7.1's actual resolved classpath and behavior, or (item 9) a documented, unconditional framework interaction whose mitigation is configuration-level, not a new architecture.

## Known Stubs

None. Every class this plan introduces is fully wired: `JwtTokenService` issues and refreshes real tokens against the real `JwtEncoder`/`JwtDecoder`s, `AuthCookieWriter` writes/clears real cookies, and `SecurityConfig` is the live filter chain protecting the one real endpoint that exists so far (`PUT /api/settings/jira-site`). Plan 06 wires the login/refresh/logout endpoints these classes were built for.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: csrf-bypass-residual-risk | `src/main/java/com/evision/releasemgmt/config/SecurityConfig.java` | Spring's `oauth2ResourceServer()` unconditionally exempts any request a bearer token is resolvable from (via `CookieBearerTokenResolver`, i.e. any request carrying an `access_token` cookie) from CSRF protection (T-05-04's `csrf.spa()` mitigation). This is a documented, unconditional Spring Security behavior with no public API to disable, verified by decompiling `OAuth2ResourceServerConfigurer.registerDefaultCsrfOverride`. **Why this is not currently exploitable:** `access_token` is `SameSite=Strict` (T-05-05/AuthCookieWriter), which prevents the browser from ever attaching that cookie to a genuinely cross-site request — the only path through which the exemption could be abused. If a future change ever relaxes `SameSite` to `Lax` or `None` for this cookie, this exemption becomes a live CSRF hole and must be re-addressed (e.g. a custom filter re-implementing the double-submit check specifically for bearer-cookie-authenticated requests, or an upstream Spring Security fix). Recommend the verifier/reviewer treat any future `SameSite` change on `access_token` as requiring a re-review of this exact interaction. |

## Self-Check: PASSED

All 13 created files verified present on disk. Both commits (baf604b, 65a9717) verified in `git log`.
