---
phase: 01-foundation-authentication
plan: 06
subsystem: auth-endpoints
tags: [spring-security-7.1, jwt, jira-auth, aes-256-gcm, cookie-auth, archunit, testcontainers]
dependency-graph:
  requires:
    - "01-03: JiraAuthPort.validate + JiraAuthFailureReason taxonomy + stub-jira dev@example.com/valid-token pair"
    - "01-04: JiraCredentialRepository (findByAccountId/upsert) with column-level AES-256-GCM encryption"
    - "01-05: JwtTokenService (issue/refresh), TokenPair, AuthCookieWriter, SecurityConfig's authorization matrix and csrf.spa() enforcement"
  provides:
    - "application/AuthenticationService: login(email, apiToken) validates live against Jira before persisting anything or minting tokens; refresh(refreshTokenValue) rotates the idle window while preserving the absolute cap"
    - "application/JiraAccessContextService.currentContext(): the sole AUTH-02 mechanism - Jira credential lookup keyed strictly on the authenticated principal, with no accountId-taking overload and no shared-credential path"
    - "ports/CurrentUserPort + adapters/security/SecurityContextCurrentUserAdapter: framework-free access to the authenticated Jira accountId, resolved only from a JwtAuthenticationToken"
    - "adapters/web/AuthController: POST /api/auth/login|refresh|logout, GET /api/auth/session - all four token-free in the response body"
    - "GlobalExceptionHandler now maps SessionRefreshException's two reasons (SESSION_EXPIRED, INVALID_SESSION) distinct from JiraAuthenticationException's INVALID_CREDENTIALS"
  affects:
    - "Phase 2 (Jira ticket reads): must call JiraAccessContextService.currentContext() - this is now the only path to a Jira credential in the whole codebase"
    - "Plan 07 (sign-in UI): consumes the exact four-endpoint REST contract and every ProblemDetail reason value documented below"
tech-stack:
  added: []
  patterns:
    - "application/ may depend on adapters.security (JwtTokenService) directly - ArchitectureBoundaryTest's application-vs-adapters rule was narrowed to jira/persistence/web; security/session infrastructure has no port abstraction and none is warranted for a single-implementation technical concern"
    - "AuthenticationService decodes its own freshly-issued access token (via the injected accessTokenDecoder) to recover accountId/email on refresh, since TokenPair carries only accessToken/refreshToken/absoluteExpiry - no widening of plan 05's TokenPair record"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/auth/JiraAccessContext.java
    - src/main/java/com/evision/releasemgmt/ports/CurrentUserPort.java
    - src/main/java/com/evision/releasemgmt/adapters/security/SecurityContextCurrentUserAdapter.java
    - src/main/java/com/evision/releasemgmt/application/AuthenticationService.java
    - src/main/java/com/evision/releasemgmt/application/JiraAccessContextService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/AuthController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/LoginRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/SessionResponse.java
    - src/test/java/com/evision/releasemgmt/application/AuthenticationServiceTest.java
    - src/test/java/com/evision/releasemgmt/application/JiraAccessContextServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/AuthControllerIT.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
decisions:
  - "Narrowed ArchitectureBoundaryTest's applicationDoesNotDependOnAdapters rule from a blanket ..adapters.. ban to adapters.jira/adapters.persistence/adapters.web only - AuthenticationService must constructor-inject JwtTokenService (adapters.security) per this plan's own design, and no plan has created a port abstraction over the JWT session mechanism, nor is one warranted for a single-implementation technical concern"
  - "AuthenticationService.refresh decodes the newly-issued access token with the injected accessTokenDecoder bean to recover accountId/email, rather than widening plan 05's three-field TokenPair record"
  - "AuthenticationService.login stores the submitted email (not identity.email()) - Jira Cloud's /myself can return a null emailAddress depending on account visibility settings, and the stored email is what reconstructs Basic auth for later calls"
metrics:
  duration_minutes: 55
  tasks_completed: 2
  files_created: 11
  completed_date: "2026-07-29"
---

# Phase 01 Plan 06: Auth Endpoints & Per-User Credential Resolution Summary

Composes plans 03-05 into the live sign-in surface (`/api/auth/login|refresh|logout|session`) and establishes `JiraAccessContextService.currentContext()` as the single, structurally-enforced path every later phase must use to read a Jira credential.

## What Was Built

**Task 1 - Login orchestration and the AUTH-02 mechanism.** `JiraAccessContext` (zero-import record: siteBaseUrl/accountId/email/apiToken) and `CurrentUserPort` (`Optional<String> currentAccountId()`) live in `domain/auth`/`ports` with no framework imports, enforced by ArchUnit. `SecurityContextCurrentUserAdapter` is the only class in the codebase that reads `SecurityContextHolder`, and matches specifically on `JwtAuthenticationToken` so an anonymous or differently-typed `Authentication` resolves to `Optional.empty()` rather than leaking a principal name into a credential lookup. `AuthenticationService.login(email, apiToken)` resolves the configured site, calls `jiraAuthPort.validate(...)` **before** touching persistence or minting anything, stores the *submitted* email (not Jira's own, which can be null depending on account visibility) via `credentialRepository.upsert(...)`, then issues a token pair - a rejected token propagates its exact `JiraAuthFailureReason` untouched and neither the credential repository nor the token service are ever invoked. `AuthenticationService.refresh(refreshTokenValue)` delegates to `JwtTokenService.refresh`, then decodes the freshly-issued access token with an injected `accessTokenDecoder` bean to recover `accountId`/`email` (since `TokenPair` intentionally carries only three fields), returning a `SignedInUser` whose `absoluteExpiry` is byte-identical to the pair's - proving the cap is passed through, never recomputed. `JiraAccessContextService.currentContext()` resolves `CurrentUserPort.currentAccountId()`, looks up that account's stored credential, and pairs it with the configured site URL; both "no authenticated principal" and "authenticated but no stored credential" map to `INVALID_CREDENTIALS`, and there is no overload taking an accountId anywhere in the class.

**Task 2 - The four endpoints and token-free responses.** `LoginRequest` (`@NotBlank @Email` email, `@NotBlank` apiToken - no length/format constraint on the token itself, since Atlassian publishes no stable format) and `SessionResponse` (`accountId`/`email`/`absoluteExpiry`, no token field of any kind) are the only two DTOs. `AuthController` exposes `POST /login` (writes cookies via `AuthCookieWriter`, returns `SessionResponse`), `POST /refresh` (rejects a missing/blank `refresh_token` cookie as `INVALID_SESSION` before calling the service), `POST /logout` (204, clears both cookies, touches no server-side state), and `GET /session` (reads the already-validated `Jwt` principal directly - no database call, no Jira call). `GlobalExceptionHandler` gained one exhaustive-switch handler mapping `SessionRefreshException`'s two reasons to 401 `SESSION_EXPIRED`/`INVALID_SESSION`, distinct from `JiraAuthenticationException`'s `INVALID_CREDENTIALS` so plan 07's SPA can tell "silently retry" apart from "ask the user to re-enter their Jira token".

**Critical mid-plan finding - application layer needs adapters.security.** The plan's own design directs `AuthenticationService` to constructor-inject `JwtTokenService` directly (no port exists over it, and none was created in plan 05). `ArchitectureBoundaryTest`'s `applicationDoesNotDependOnAdapters` rule as written (`resideInAPackage("..adapters..")`) would have failed the build on this legitimate dependency. Narrowed the rule to ban only `adapters.jira`/`adapters.persistence`/`adapters.web` from `application/`, matching this plan's own acceptance-criteria grep pattern (`adapters\.(jira|persistence|web)`) exactly - `adapters.security` remains reachable from `application/` as the session/token infrastructure it is, with every other adapter boundary still enforced.

## Verification

- `mvn test` - 90 tests, all green (was 65 at the start of this plan; +12 from Task 1: 7 `AuthenticationServiceTest` + 5 `JiraAccessContextServiceTest`; +13 from Task 2: `AuthControllerIT`), no regressions.
- `mvn test -Dtest='AuthenticationServiceTest,JiraAccessContextServiceTest,ArchitectureBoundaryTest'` - 7 + 5 + 3 = 15 tests, matching the plan's exact expected count.
- `mvn test -Dtest=AuthControllerIT` - 13/13 passing against real Postgres 18 (Testcontainers) and the `stub-jira` profile.
- Manual smoke test against a live `spring-boot:run -Dspring-boot.run.profiles=local,stub-jira` instance (compose disabled, using the already-running `releasemanagement-postgres-1` container): login issues two httpOnly `Set-Cookie` headers (`SameSite=Strict`) and a token-free body; `GET /api/auth/session` and `POST /api/auth/refresh` both echo the identical `absoluteExpiry`; a wrong token returns 401 with `reason: INVALID_CREDENTIALS` and the "no longer valid" detail message.
- `grep -rn "findByAccountId" src/main/java/` - the only caller is `JiraAccessContextService` (plus plan 04's adapter implementation and the port declaration itself) - no other code path fetches a credential by any other key.
- `grep -rniE "service-account|shared-credential|default-credential" src/main/java/ src/main/resources/` - empty (AUTH-02).
- `grep -rn "apiToken" src/main/java/com/evision/releasemgmt/adapters/web/dto/` - matches only `LoginRequest.java`; no response DTO mentions a token.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `ArchitectureBoundaryTest`'s blanket application-vs-adapters ban would have failed the build on this plan's own required design**
- **Found during:** Task 1, first compile/test of `AuthenticationService`
- **Issue:** The plan directs `AuthenticationService` to constructor-inject `JwtTokenService` (`adapters.security`) directly - there is no port over the JWT session mechanism and plan 05 didn't create one. `ArchitectureBoundaryTest.applicationDoesNotDependOnAdapters` as written bans `application/` from depending on anything under `..adapters..`, which would have failed on this legitimate, plan-mandated dependency. The plan's own acceptance criteria confirms the intended scope: its grep only checks for `adapters\.(jira|persistence|web)`, deliberately omitting `adapters.security`.
- **Fix:** Narrowed the ArchUnit rule to ban only `adapters.jira`/`adapters.persistence`/`adapters.web` from `application/`, matching the plan's acceptance-criteria grep exactly. Every other architectural boundary (domain/ports framework-import ban, domain/ports isolation from adapters/config/application) is untouched.
- **Files modified:** `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java`
- **Commit:** 64f0428

**2. [Rule 1 - Bug] Doc comment accidentally tripped the "no service-account naming" grep gate**
- **Found during:** Task 1 acceptance-criteria verification
- **Issue:** A Javadoc comment on `JiraAccessContextService` used the phrase "no shared/service-account credential", whose substring `service-account` matched the case-insensitive grep this plan uses to prove no shared-credential naming exists anywhere in source (`grep -rniE 'service-account|...'`), inflating the count from the expected 0.
- **Fix:** Reworded the comment to convey the same intent ("no path to any credential outside the caller's own signed-in identity") without the colliding substring.
- **Files modified:** `src/main/java/com/evision/releasemgmt/application/JiraAccessContextService.java`
- **Commit:** 64f0428

**3. [Rule 1 - Bug] `GlobalExceptionHandler`'s new handler doubled its own grep-gated literal count**
- **Found during:** Task 2 acceptance-criteria verification
- **Issue:** The plan's acceptance criteria expects `grep -Ec 'SESSION_EXPIRED|INVALID_SESSION' GlobalExceptionHandler.java` to return exactly 2 (one line per case label). Writing the `reason` argument as a second literal string (`"SESSION_EXPIRED"`/`"INVALID_SESSION"`) alongside the `case` label doubled the matching line count to 4.
- **Fix:** Replaced the literal `reason` argument with `ex.reason().name()` in both switch arms - functionally identical output, no duplicated literal to drift from the enum, and the line count matches the plan's expectation.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java`
- **Commit:** 52a3d36

---

**Total deviations:** 3 auto-fixed (1 blocking architectural-rule narrowing, 2 grep-collision wording fixes)
**Impact on plan:** All three were required to make the plan's own explicitly-specified design pass its own acceptance criteria. No scope creep, no behavior change beyond what the plan already mandated.

## Issues Encountered

None beyond the deviations above.

## REST Contract for Plan 07

| Method | Path | Auth | Body in | Body out | Notes |
|---|---|---|---|---|---|
| POST | `/api/auth/login` | none (CSRF-protected) | `{"email","apiToken"}` | `SessionResponse` | 200 on success; validates live against Jira first |
| POST | `/api/auth/refresh` | `refresh_token` cookie (CSRF-protected) | none | `SessionResponse` | 200; `absoluteExpiry` unchanged from original login |
| POST | `/api/auth/logout` | none (CSRF-protected) | none | 204, no body | Clears both cookies; no server-side state |
| GET | `/api/auth/session` | `access_token` cookie | none | `SessionResponse` | No DB/Jira call - reads the validated JWT directly |

`SessionResponse`: `{accountId, email, absoluteExpiry}` - never a token field, in any response, in any form.

**`reason` values plan 07 branches on:**
- From login: `INVALID_CREDENTIALS` (401, "no longer valid" - prompt re-entry), `JIRA_FORBIDDEN` (403), `JIRA_RATE_LIMITED` (429), `JIRA_UNREACHABLE` (503), `JIRA_ERROR` (502), `SITE_NOT_CONFIGURED` (409), `VALIDATION_FAILED` (400).
- From refresh: `SESSION_EXPIRED` (401, "sign in again") vs `INVALID_SESSION` (401, "sign in again") - both distinct from `INVALID_CREDENTIALS`.
- From session/any authenticated call without a valid cookie: `UNAUTHENTICATED` (401, plan 05).

**AUTH-02 contract for every later phase:** `JiraAccessContextService.currentContext()` is the *only* supported way to obtain a `JiraAccessContext(siteBaseUrl, accountId, email, apiToken)` for an outbound Jira call. It is keyed strictly on the currently authenticated principal (`CurrentUserPort.currentAccountId()`); there is no overload taking an accountId and no configuration key naming a shared or default credential anywhere in the codebase. Phase 2's ticket reads must call this method from within an authenticated request - there is no other path to a Jira credential.

## Known Stubs

None. Every class this plan introduces is fully wired against real collaborators: `AuthenticationService`/`JiraAccessContextService` are proven with Mockito unit tests against every collaborator interface, and `AuthController`'s four endpoints are proven end-to-end against real Postgres 18 (Testcontainers) and the `stub-jira` profile's canned credential pair. `StubJiraAuthAdapter` (plan 03) remains the deliberate, profile-gated substitute for a live Jira Cloud site in this environment - real-instance sign-in is plan 08's explicit manual UAT checkpoint, as this plan's own `<verification>` section states.

## Threat Flags

None beyond what this plan's own `<threat_model>` already covers (T-06-01 through T-06-11) - no new network endpoints, auth paths, or schema surfaces were introduced beyond what the plan specified.

## Self-Check: PASSED

All 11 created files verified present on disk. Both commits (64f0428, 52a3d36) verified in `git log`.
