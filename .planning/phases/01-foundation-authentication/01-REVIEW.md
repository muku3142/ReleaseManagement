---
phase: 01-foundation-authentication
reviewed: 2026-07-30T12:00:00Z
depth: standard
files_reviewed: 83
files_reviewed_list:
  - README.md
  - compose.yaml
  - frontend/package.json
  - frontend/src/api/auth.test.ts
  - frontend/src/api/auth.ts
  - frontend/src/api/client.test.ts
  - frontend/src/api/client.ts
  - frontend/src/api/credentialStatus.ts
  - frontend/src/api/jiraSite.ts
  - frontend/src/api/queryClient.ts
  - frontend/src/components/AppShell.tsx
  - frontend/src/components/CredentialAlert.tsx
  - frontend/src/main.tsx
  - frontend/src/router.tsx
  - frontend/src/routes/DashboardRoute.tsx
  - frontend/src/routes/LoginRoute.tsx
  - frontend/src/routes/SettingsRoute.tsx
  - frontend/vite.config.ts
  - pom.xml
  - src/main/java/com/evision/releasemgmt/ReleaseManagementApplication.java
  - src/main/java/com/evision/releasemgmt/adapters/jira/JiraMyselfResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/jira/StubJiraAuthAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/JiraCredentialRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/JiraSiteConfigRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/JiraCredentialEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/JiraSiteConfigEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/JiraCredentialJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/JiraSiteConfigJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/security/AbsoluteExpiryValidator.java
  - src/main/java/com/evision/releasemgmt/adapters/security/AuthCookieWriter.java
  - src/main/java/com/evision/releasemgmt/adapters/security/CookieBearerTokenResolver.java
  - src/main/java/com/evision/releasemgmt/adapters/security/JwtTokenService.java
  - src/main/java/com/evision/releasemgmt/adapters/security/ProblemDetailAuthenticationEntryPoint.java
  - src/main/java/com/evision/releasemgmt/adapters/security/SecurityContextCurrentUserAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/security/SessionRefreshException.java
  - src/main/java/com/evision/releasemgmt/adapters/security/TokenPair.java
  - src/main/java/com/evision/releasemgmt/adapters/security/TokenTypeValidator.java
  - src/main/java/com/evision/releasemgmt/adapters/web/AuthController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
  - src/main/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/JiraSiteConfigResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/LoginRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReplaceJiraTokenRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/SessionResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/UpdateJiraSiteConfigRequest.java
  - src/main/java/com/evision/releasemgmt/application/AuthenticationService.java
  - src/main/java/com/evision/releasemgmt/application/JiraAccessContextService.java
  - src/main/java/com/evision/releasemgmt/application/JiraSiteConfigService.java
  - src/main/java/com/evision/releasemgmt/config/JiraRestClientConfig.java
  - src/main/java/com/evision/releasemgmt/config/JiraSiteConfigSeeder.java
  - src/main/java/com/evision/releasemgmt/config/JwtConfig.java
  - src/main/java/com/evision/releasemgmt/config/SecurityConfig.java
  - src/main/java/com/evision/releasemgmt/config/SpaForwardingController.java
  - src/main/java/com/evision/releasemgmt/domain/auth/JiraAccessContext.java
  - src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthFailureReason.java
  - src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthenticationException.java
  - src/main/java/com/evision/releasemgmt/domain/auth/JiraCredential.java
  - src/main/java/com/evision/releasemgmt/domain/auth/JiraIdentity.java
  - src/main/java/com/evision/releasemgmt/domain/jira/JiraSiteConfig.java
  - src/main/java/com/evision/releasemgmt/ports/CurrentUserPort.java
  - src/main/java/com/evision/releasemgmt/ports/JiraAuthPort.java
  - src/main/java/com/evision/releasemgmt/ports/JiraCredentialRepository.java
  - src/main/java/com/evision/releasemgmt/ports/JiraSiteConfigRepository.java
  - src/main/resources/application-local.yml
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V1__init_schema.sql
  - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
  - src/test/java/com/evision/releasemgmt/adapters/jira/JiraRestAuthAdapterTest.java
  - src/test/java/com/evision/releasemgmt/adapters/jira/StubJiraAuthAdapterTest.java
  - src/test/java/com/evision/releasemgmt/adapters/persistence/JiraCredentialPersistenceIT.java
  - src/test/java/com/evision/releasemgmt/adapters/persistence/JiraSiteConfigPersistenceIT.java
  - src/test/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverterTest.java
  - src/test/java/com/evision/releasemgmt/adapters/security/AuthCookieWriterTest.java
  - src/test/java/com/evision/releasemgmt/adapters/security/JwtTokenServiceTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/AuthControllerIT.java
  - src/test/java/com/evision/releasemgmt/adapters/web/JiraAuthProblemDetailTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigControllerTest.java
  - src/test/java/com/evision/releasemgmt/application/AuthenticationServiceTest.java
  - src/test/java/com/evision/releasemgmt/application/JiraAccessContextServiceTest.java
  - src/test/java/com/evision/releasemgmt/config/SecurityFilterChainIT.java
  - src/test/resources/application.yml
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-07-30T12:00:00Z
**Depth:** standard
**Files Reviewed:** 62
**Status:** issues_found

## Summary

Reviewed the foundation/authentication phase: AES-256-GCM credential envelope, HS256 JWT
access/refresh pair with absolute-expiry cap, cookie-based session transport, Jira
login/credential-replace flow, the Jira-site SSRF-guard regex, the outbound Jira REST
client, and the frontend's single-flight refresh/retry client.

The security-critical primitives called out for special attention are sound: the AES-GCM
converter generates a fresh random IV per write (verified: two encryptions of the same
plaintext produce different ciphertext) and never logs key material; the JWT decoders pin
`MacAlgorithm.HS256` on the decoder itself (algorithm-confusion is not possible, confirmed
by an `alg: none` forgery test) and access/refresh tokens are rejected by the other type's
decoder via `TokenTypeValidator`; the absolute-expiry cap is carried forward unchanged
across `refresh()` calls, not recomputed, so it cannot be extended by repeated refreshes;
`AuthCookieWriter.clear()` writes to the exact same `path`/cookie-name pairs as `write()`;
and the SSRF-guarding `@Pattern` on `UpdateJiraSiteConfigRequest.baseUrl` was verified
empirically (via `String.matches()`) to correctly reject a trailing-newline bypass attempt,
so no defect was found there. `AuthController`/`AuthenticationService` were confirmed to
return no token/credential material in any response body (an explicit test asserts this),
and `ACCOUNT_MISMATCH` is scoped to the caller's own already-authenticated account, so it
is not a user-enumeration vector.

Five findings remain, none rising to Critical: an open-redirect gap in the frontend's
post-login redirect handling, dead/duplicated absolute-expiry validation logic in
`JwtTokenService.refresh()`, a fail-open default in the authorization matcher chain that
is not exploitable today but is a latent risk for future phases, and two low-impact
code-quality items (unreachable defensive code, and an inconsistent exception type for a
corrupted-ciphertext edge case).

## Warnings

### WR-01: Open redirect via unsanitized `redirect` query parameter after login

**File:** `frontend/src/routes/LoginRoute.tsx:20-24` (also `frontend/src/router.tsx:14-16`)
**Issue:** After a successful login, the app does:
```ts
const search = new URLSearchParams(window.location.search);
const redirectTo = search.get('redirect');
window.location.assign(redirectTo || '/');
```
`redirectTo` comes straight from the URL's query string with no check that it is a
same-origin, relative path. `router.tsx`'s `validateSearch` for `/login` only checks that
`search.redirect` is a string (`typeof search.redirect === 'string' ? search.redirect :
undefined`), it does not restrict it to a relative path either. An attacker can send a
victim a link such as `https://your-host/login?redirect=https://evil.example.com` (or a
protocol-relative `//evil.example.com`); after the victim signs in with their real Jira
credentials, the browser is navigated off-site to a page an attacker fully controls — a
classic post-authentication phishing/open-redirect primitive (OWASP "Unvalidated
Redirects and Forwards").
**Fix:** Only follow the redirect if it is a same-origin relative path:
```ts
function safeRedirectTarget(raw: string | null): string {
  if (raw && raw.startsWith('/') && !raw.startsWith('//')) return raw;
  return '/';
}
...
window.location.assign(safeRedirectTarget(redirectTo));
```
Apply the same restriction in `router.tsx`'s `validateSearch` for defense in depth.

### WR-02: Dead/duplicated absolute-expiry check in `JwtTokenService.refresh()`

**File:** `src/main/java/com/evision/releasemgmt/adapters/security/JwtTokenService.java:74-81`
**Issue:** `refreshTokenDecoder` (wired in `JwtConfig.decoder(...)`) already includes
`AbsoluteExpiryValidator` in its `DelegatingOAuth2TokenValidator`. If `absolute_exp` is
missing or already in the past, `refreshTokenDecoder.decode(refreshTokenValue)` on line 60
throws `JwtValidationException` and is handled by the `catch` block above (lines 61-69),
which already maps an expired absolute cap to `SESSION_EXPIRED`. This is exactly what
`JwtTokenServiceTest.refreshPastAbsoluteCapThrowsSessionExpired` exercises: the exception
is actually thrown from the validator inside `decode()`, not from the manual re-check.
Consequently, the code at lines 74-81 (`if (absoluteExpEpoch == null) ...` and
`if (Instant.now().isAfter(absoluteExpiry)) ...`) is unreachable in practice — by the time
`decode()` returns without throwing, the validator has already guaranteed the claim is
present and not expired. This duplicates the validator's job in two places that can
silently diverge (e.g. if the validator chain is ever changed without updating this
method, or vice versa), and runs contrary to the project's stated convention of not adding
handling for states that cannot occur.
**Fix:** Remove the redundant re-check and rely solely on the decoder's validator chain:
```java
Jwt jwt = ...; // as decoded above, guaranteed valid at this point
Instant absoluteExpiry = Instant.ofEpochSecond(jwt.getClaim(CLAIM_ABSOLUTE_EXP));
String email = jwt.getClaimAsString(CLAIM_EMAIL);
return issueWithCap(jwt.getSubject(), email, absoluteExpiry);
```
If the intent was defense-in-depth against a future decoder misconfiguration, say so in a
one-line comment instead of leaving it as unexplained, untestable dead code.

### WR-03: Authorization chain's final rule is fail-open (`permitAll()`), not fail-closed

**File:** `src/main/java/com/evision/releasemgmt/config/SecurityConfig.java:42-47`
**Issue:** The matcher chain ends with `.anyRequest().permitAll()`. Today this is safe in
practice because it only actually serves the SPA's static assets/`index.html` (via
`SpaForwardingController`) and `/actuator/health` (the only actuator endpoint exposed per
`application.yml`'s `management.endpoints.web.exposure.include: health`) — every current
API path is either explicitly `permitAll()`-listed or captured by `/api/**.authenticated()`
first. However, this means any new server-rendered endpoint added in a later phase that is
*not* prefixed with `/api/` and not otherwise matched will be public by default unless a
developer remembers to add an explicit rule — a fail-open default rather than fail-closed.
**Fix:** Scope the trailing rule to only the concrete public surfaces instead of a bare
`anyRequest()`, e.g. match the SPA-forwarding pattern and static resource paths explicitly,
then end the chain with `.anyRequest().denyAll()` (or `.authenticated()`) so any
unanticipated future endpoint is rejected by default rather than served.

## Info

### IN-01: Unreachable `null`-principal fallback in `JiraSiteConfigController.update`

**File:** `src/main/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigController.java:32-34`
**Issue:** `String actor = principal != null ? principal.getName() : "anonymous";` — but
`PUT /api/settings/jira-site` is not in `SecurityConfig`'s `permitAll()` list, so it always
falls under `/api/**.authenticated()`; Spring Security guarantees `principal` is non-null
by the time the handler method runs. The `"anonymous"` branch can never execute and adds
defensive handling for a state that cannot occur here, which the project convention (see
`CLAUDE.md`) calls out to avoid.
**Fix:** Simplify to `String actor = principal.getName();`, or if the guard is meant to
protect against a future authorization-config regression, note that explicitly in a short
comment.

### IN-02: `EncryptedTokenConverter.convertToEntityAttribute` doesn't uniformly wrap malformed-envelope failures

**File:** `src/main/java/com/evision/releasemgmt/adapters/persistence/crypto/EncryptedTokenConverter.java:73-91`
**Issue:** The `catch (GeneralSecurityException e)` block wraps GCM-tag/auth failures into
a clean `IllegalStateException`, but a stored value that is too short to contain a key
version byte plus a 12-byte IV (e.g. a corrupted or truncated column) causes
`ByteBuffer.get()`/`get(iv)` to throw an unchecked `BufferUnderflowException`, which is not
caught here and propagates as a different, undocumented exception type than the one used
for every other decrypt failure path. Practically low-impact — it still ends up as a
generic 500 via `GlobalExceptionHandler`'s catch-all, with no key or plaintext leaked — but
it is an inconsistency in the converter's own error contract.
**Fix:** Broaden the catch to also normalize this case, e.g.
`catch (GeneralSecurityException | java.nio.BufferUnderflowException e)`, or explicitly
validate `raw.length >= 1 + IV_LENGTH` up front and throw the same `IllegalStateException`
message style.

---

_Reviewed: 2026-07-30T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
