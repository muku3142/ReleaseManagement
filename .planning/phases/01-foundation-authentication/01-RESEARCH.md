# Phase 1: Foundation & Authentication - Research

**Researched:** 2026-07-28
**Domain:** Spring Boot 4.1 / Java 25 hexagonal skeleton, self-issued cookie-borne JWT auth, column-level encryption, Jira Cloud credential validation, Vite/React single-jar packaging
**Confidence:** MEDIUM-HIGH (backend/frontend build mechanics and Jira Cloud auth are HIGH; the ArchUnit/JUnit 6 compatibility gap and the JWT-library choice are flagged LOW/ASSUMED and need a quick spike-level confirmation before Wave 0)

## Summary

Phase 1 is a walking-skeleton phase: nothing here is exploratory, most of it is "wire the standard thing correctly." Three things, however, are genuinely load-bearing and get the deepest treatment below: (1) cookie-borne stateless JWT auth with an access+refresh pair under Spring Security 7's new `csrf.spa()` DSL, (2) column-level AES-GCM encryption of the Jira API token via a Spring-aware `AttributeConverter`, and (3) a structured `ProblemDetail`-based error shape that Phase 2's JIRA-06 will extend. A fourth item is a genuine, currently-open risk rather than a design decision: **ArchUnit's `archunit-junit5` module does not yet support JUnit 6** (Spring Boot 4.1's managed JUnit version), confirmed via an open upstream issue with no released fix as of this research. This changes how the architecture-boundary tests must be wired in Wave 0.

**Primary recommendation:** Single Maven module, package-by-layer (`domain/`, `ports/`, `adapters/{jira,persistence,web}/`, `config/`), Spring Boot 4.1 + Spring Security 7's `csrf.spa()` for CSRF, a symmetric-key `NimbusJwtEncoder`/`NimbusJwtDecoder` pair (via `spring-boot-starter-oauth2-resource-server`, not `oauth2-client`) issuing two independently-typed self-contained JWTs (access + refresh, no server-side token table — anything else would silently reintroduce the server-side session state D-05/D-09 explicitly reject), a self-contained AES-256-GCM envelope in a single `AttributeConverter`-managed column for the Jira token, `ProblemDetail` (RFC 9457) with a `reason` extension property for every failure path, and `frontend-maven-plugin` 2.0.1 to build Vite's output into `src/main/resources/static` for a single jar. Use ArchUnit's core library directly with plain `@Test` methods (bypassing `archunit-junit5`'s test engine) to sidestep the JUnit 6 gap without waiting on upstream.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Jira site configuration (store/view/set) | API / Backend | Database / Storage | One row, seeded from app config, edited via a settings endpoint; UI only displays/edits what the API returns |
| Sign-in (validate email + token against Jira) | API / Backend | — | The server must hold and send the Jira credential itself (Basic auth to `/rest/api/3/myself`) — the browser never talks to Jira directly, so this cannot live client-side |
| Session issuance (access + refresh JWT) | API / Backend | Browser / Client | Backend signs and sets cookies; browser only stores (httpOnly, opaque) and forwards them automatically — no token handling in JS |
| CSRF protection | API / Backend | Browser / Client | Spring Security issues/validates the token; the SPA's fetch/axios layer must read the plain cookie value and echo it in a header on state-changing requests |
| Jira credential encryption at rest | Database / Storage | API / Backend | Ciphertext is computed in the application layer (JPA converter) before the row is written; the database never sees or needs to know about plaintext |
| App shell, navigation, empty state, settings screen | Browser / Client | API / Backend | Pure client-side rendering/routing; all state (site config, sign-in, token replace) comes from REST calls |
| Package/architecture boundary enforcement (`domain` has no framework imports) | API / Backend (build-time) | — | Enforced by a test that runs in the Maven build, not a runtime concern |

## Project Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | Sign in with Jira email + API token, validated live against Jira | Jira Cloud `/rest/api/3/myself` + Basic auth section; error-shape section for rejecting invalid/expired/revoked tokens |
| AUTH-02 | All Jira reads use the signed-in user's own credential | Satisfied structurally by storing the credential per signed-in account and having every future Jira call read it from that account's row — no shared/service credential introduced in this phase |
| AUTH-03 | Stored credentials encrypted at rest, never returned to client | Column-level AES-GCM `AttributeConverter` section; DTO design rule (never map the token field to any response DTO) |
| AUTH-04 | Replace an expired/revoked token without losing releases/templates/history | Schema design keeps the credential row keyed by Jira account identity, independent of any future release/template rows — replacing it is an `UPDATE`, not a delete-and-recreate |
| JIRA-01 | Configure which Jira Cloud site the system reads from | Single-row `jira_site_config` table, seeded from `application.yml`, exposed via a settings GET/PUT endpoint |

## Project Constraints (from CLAUDE.md)

- Backend: Java 25 (LTS) on Spring Boot 4.1.0 — Spring MVC (servlet, blocking), not WebFlux.
- PostgreSQL 18 + Flyway (`flyway-database-postgresql` required alongside `flyway-core` from Flyway 10+).
- Spring Data JPA/Hibernate for persistence; outbound HTTP via Spring's `RestClient`, never `RestTemplate`.
- Frontend: Vite 8.x + TypeScript 7.x, React SPA, TanStack Router/Query, Tailwind + shadcn/ui.
- No FreeMarker/Pebble/Thymeleaf/Handlebars.java anywhere (project-wide constraint, not triggered in this phase but binds any future template work).
- Hexagonal package layout (`domain/` → `ports/` → `adapters/`) is mandatory from Phase 1, enforced by ArchUnit, with zero framework imports in `domain/`.
- No over-engineering / no speculative abstraction: do **not** build `TicketProvider` or any provider-selection machinery in this phase.
- Testing: JUnit Jupiter (Boot-managed, currently the 6.1.x line), AssertJ, Mockito, WireMock 3.13.2 (not the 4.0.0-beta line), Testcontainers 2.0.x.
- Security: no SQL injection/XSS/command injection; sanitize all external-boundary input; never commit secrets.
- Git: no `--no-verify`, no force-push to main, no `Co-Authored-By` trailers, stage files by name.
- GSD workflow enforcement is active — all file changes must go through a GSD entry point (already satisfied by this research→plan→execute chain).

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.1.0 | Application framework | Non-negotiable project constraint; current GA (2026-06-10) [CITED: spring.io/blog/2026/06/10/spring-boot-4] |
| Java | 25 (LTS) recommended, 17 floor | Runtime | Local dev machine currently has JDK 17 installed only — see Environment Availability |
| PostgreSQL | 18.x | Datastore | [CITED: postgresql.org/about/news/postgresql-18-released-3142] |
| Flyway | Boot-managed (11.x line) + `flyway-database-postgresql` | Schema migration | Dialect module split from `flyway-core` since Flyway 10 [VERIFIED: STACK.md, same-day Maven Central check] |
| Spring Data JPA + Hibernate 7 | Boot-managed | Persistence | Standard default; `AttributeConverter` (JPA 2.1+ standard) used for the encrypted column, not a Hibernate-only annotation |

### Supporting (Phase 1-specific)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-web` | Boot-managed | REST controllers | Always |
| `spring-boot-starter-security` | Boot-managed | Security filter chain, `csrf.spa()` | Always |
| `spring-boot-starter-oauth2-resource-server` | Boot-managed | `JwtDecoder`/`JwtEncoder` (Nimbus, transitively via `spring-security-oauth2-jose`) | Self-issued JWT validation **and** issuance — see JWT section for why this starter, not `oauth2-client` |
| `spring-boot-starter-validation` | Boot-managed | Bean Validation on sign-in/site-config DTOs | Always |
| `spring-boot-starter-actuator` | Boot-managed | `/actuator/health` | Always |
| `spring-boot-starter-data-jpa` | Boot-managed | JPA/Hibernate | Always |
| `flyway-database-postgresql` | Boot-managed | Postgres Flyway dialect | Always, alongside `flyway-core` |
| `com.github.eirslett:frontend-maven-plugin` | 2.0.1 | Downloads Node, runs `npm ci && npm run build`, copies Vite output into the jar | [VERIFIED: Maven Central metadata, fetched this session — `<release>2.0.1</release>`] |
| `org.wiremock:wiremock` | 3.13.2 | Stub Jira `/myself` for integration tests | Per project stack constraint (not the 4.0.0-beta line) |
| `org.testcontainers:testcontainers` + `postgresql` module | 2.0.5 | Ephemeral Postgres for Flyway/JPA integration tests | Combine with `@ServiceConnection` |
| ArchUnit core (`com.tngtech.archunit:archunit`) | 1.4.2 | Architecture-boundary assertions | Use the **core** module with plain `@Test` methods — see Pitfall below on `archunit-junit5` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `AttributeConverter` for the encrypted token column | Hibernate `@ColumnTransformer` | `@ColumnTransformer` pushes crypto into raw SQL functions (harder to unit test, ties you to Postgres-side crypto functions); `AttributeConverter` is portable, standard JPA, and testable with a plain JUnit test with no Spring/DB context |
| Symmetric (HS256) self-issued JWT | Asymmetric (RS256/ES256) via `NimbusJwtEncoder.withPublicKey`/`withPrivateKey` | Asymmetric only earns its complexity when a *different* service validates tokens than the one that issues them (e.g. a separate resource server). Here, one Spring Boot process both issues and validates — a shared secret from env (mirroring D-11's key-from-env pattern already established for credential encryption) is simpler and equally safe for this topology |
| `frontend-maven-plugin` | Gradle Node plugin | Only relevant if the project chooses Gradle; this dev machine has Maven 3.9.11 installed and no Gradle — Maven is the pragmatic default here (see Environment Availability) |
| ArchUnit core + plain `@Test` | `archunit-junit5`'s `@AnalyzeClasses`/engine-based discovery | `archunit-junit5` targets JUnit Platform 1.x and, as of this research, does not support JUnit 6 (Boot 4.1's managed version) — see Common Pitfalls |

**Installation (Maven, additions beyond Boot starters):**
```xml
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit</artifactId>
  <version>1.4.2</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock</artifactId>
  <version>3.13.2</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <version>2.0.5</version>
  <scope>test</scope>
</dependency>
```

```xml
<!-- frontend-maven-plugin, bound to generate-resources so the build output
     lands on the classpath before the jar is packaged -->
<plugin>
  <groupId>com.github.eirslett</groupId>
  <artifactId>frontend-maven-plugin</artifactId>
  <version>2.0.1</version>
  <configuration>
    <workingDirectory>frontend</workingDirectory>
    <installDirectory>${project.build.directory}</installDirectory>
  </configuration>
  <executions>
    <execution><id>install node and npm</id><goals><goal>install-node-and-npm</goal></goals></execution>
    <execution><id>npm ci</id><goals><goal>npm</goal></goals><configuration><arguments>ci</arguments></configuration></execution>
    <execution>
      <id>npm run build</id>
      <goals><goal>npm</goal></goals>
      <phase>generate-resources</phase>
      <configuration><arguments>run build</arguments></configuration>
    </execution>
  </executions>
</plugin>
<!-- copy frontend/dist -> target/classes/static via maven-resources-plugin,
     bound to process-resources (after Vite build, before the jar is assembled) -->
```

**Version verification performed this session:**
- `frontend-maven-plugin` 2.0.1 — [VERIFIED: Maven Central metadata]
- Vite 8.1.5, TypeScript 7.0.2, React 19.2.8, `@tanstack/react-router` 1.170.18, `@tanstack/react-query` 5.101.4 — [VERIFIED: `npm view <pkg> version`, this session]
- ArchUnit 1.4.2 (latest, April 2026) — [VERIFIED: archunit.org/news + GitHub releases, this session]
- Local toolchain actually installed: JDK 17.0.16 (Microsoft build), Maven 3.9.11, Node 20.19.0, Docker 29.1.3, no Gradle, no `psql` client — [VERIFIED: direct shell probe, this session]

## Architecture Patterns

### System Architecture Diagram

```
Browser (React SPA, Vite build)
  │  fetch("/api/...", { credentials: "same-origin" })
  │  reads XSRF-TOKEN cookie -> sends X-XSRF-TOKEN header on POST/PUT/DELETE
  ▼
Spring Security filter chain (same origin, one jar)
  ├─ CsrfFilter (csrf.spa(): CookieCsrfTokenRepository + XorCsrfTokenRequestAttributeHandler)
  ├─ BearerTokenAuthenticationFilter (custom BearerTokenResolver reads "access_token" cookie,
  │     NOT the Authorization header)
  └─ JwtAuthenticationProvider (NimbusJwtDecoder, HS256, shared secret from env)
  ▼
adapters/web  (Controllers: AuthController, JiraSiteConfigController)
  │  DTOs only — never entities, never the JWT, never the raw Jira token
  ▼
application/  (AuthenticationService, JiraSiteConfigService)
  │
  ├──▶ ports/JiraAuthPort  ──▶ adapters/jira/JiraRestAuthAdapter ──▶ RestClient ──▶ Jira Cloud
  │                              (Basic auth: base64(email:token))    GET /rest/api/3/myself
  │                          ──▶ adapters/jira/StubJiraAuthAdapter (Spring profile "stub-jira",
  │                              no live Jira call — local dev / CI)
  │
  └──▶ ports/JiraCredentialRepository ──▶ adapters/persistence/
                                            JPA entity, AttributeConverter encrypts
                                            the token column before INSERT/UPDATE
                                            ▼
                                       PostgreSQL 18 (jira_site_config, jira_credential)
```

A reader can trace AUTH-01 end to end: browser submits email+token → `AuthController` → `AuthenticationService` → `JiraAuthPort` (real or stub) validates against Jira → on success, `AuthenticationService` issues access+refresh JWTs, sets them as cookies, and persists the encrypted token via `JiraCredentialRepository` → response carries no token material at all, only a success/failure body.

### Recommended Project Structure

```
src/main/java/com/<org>/releasemgmt/
├── domain/
│   └── auth/                         # JiraIdentity, JiraSiteConfig — plain records, zero imports
│       └── JiraAuthFailureReason.java  # enum: INVALID_CREDENTIALS, JIRA_UNREACHABLE, JIRA_RATE_LIMITED, ...
├── ports/
│   ├── JiraAuthPort.java             # validate(email, token) -> JiraIdentity | throws JiraAuthenticationException
│   ├── JiraCredentialRepository.java
│   └── JiraSiteConfigRepository.java
├── application/
│   ├── AuthenticationService.java    # orchestrates validate -> issue tokens -> persist credential
│   └── JiraSiteConfigService.java
├── adapters/
│   ├── jira/
│   │   ├── JiraRestAuthAdapter.java  # @Profile("!stub-jira"), RestClient -> /rest/api/3/myself
│   │   └── StubJiraAuthAdapter.java  # @Profile("stub-jira"), canned identities for local dev
│   ├── security/
│   │   ├── JwtTokenService.java      # NimbusJwtEncoder/Decoder wrapper, issues access+refresh pairs
│   │   ├── CookieBearerTokenResolver.java
│   │   └── AuthCookieWriter.java     # sets/clears access_token + refresh_token cookies with correct flags
│   ├── persistence/
│   │   ├── entity/JiraCredentialEntity.java   # encrypted_token column via @Convert
│   │   ├── entity/JiraSiteConfigEntity.java
│   │   ├── crypto/EncryptedTokenConverter.java  # @Component AttributeConverter<String,String>
│   │   └── repository/                # Spring Data JpaRepository interfaces + adapter impls
│   └── web/
│       ├── AuthController.java       # POST /api/auth/login, POST /api/auth/refresh, POST /api/auth/logout
│       ├── JiraSiteConfigController.java  # GET/PUT /api/settings/jira-site
│       ├── GlobalExceptionHandler.java    # extends ResponseEntityExceptionHandler, ProblemDetail
│       └── dto/
├── config/
│   ├── SecurityConfig.java
│   └── SpaForwardingConfig.java      # forwards unmatched non-API, non-dotted paths to index.html
└── ReleaseManagementApplication.java

src/main/resources/
├── db/migration/V1__init_schema.sql  # jira_site_config, jira_credential
└── static/                           # Vite production build lands here

frontend/                             # React app source (Vite), built by frontend-maven-plugin
```

### Pattern 1: Self-contained access + refresh JWTs (no server-side token store)

**What:** Both tokens are stateless, signed JWTs. The refresh token is not a database row — storing it server-side would silently reintroduce the session state that D-05/D-09 explicitly reject ("do not add a denylist," "no server-side session store"). The absolute 8-hour cap (D-06/D-07) is carried as an immutable claim copied forward unchanged on every refresh; only the idle-window expiry advances.

**When to use:** Exactly this project's D-07 shape — rolling idle window expressed via the refresh token, hard absolute cap that refreshing cannot extend.

**Example:**
```java
// adapters/security/JwtTokenService.java
public record TokenPair(String accessToken, String refreshToken, Instant absoluteExpiry) {}

TokenPair issue(String jiraAccountId, Instant absoluteExpiry) {
    Instant now = Instant.now();
    Jwt access = encode(jiraAccountId, now, now.plus(Duration.ofMinutes(10)), "access", absoluteExpiry);
    Jwt refresh = encode(jiraAccountId, now, now.plus(Duration.ofMinutes(30)), "refresh", absoluteExpiry);
    return new TokenPair(access.getTokenValue(), refresh.getTokenValue(), absoluteExpiry);
}

TokenPair refresh(Jwt refreshToken) {
    if (!"refresh".equals(refreshToken.getClaimAsString("token_type"))) {
        throw new InvalidTokenTypeException(); // reject an access token presented at /refresh
    }
    Instant absoluteExpiry = refreshToken.getClaim("absolute_exp"); // carried forward, never recomputed
    if (Instant.now().isAfter(absoluteExpiry)) {
        throw new SessionHardExpiredException(); // 8h cap reached — force full re-login
    }
    return issue(refreshToken.getSubject(), absoluteExpiry); // new idle window, same absolute cap
}

private Jwt encode(String sub, Instant iat, Instant exp, String tokenType, Instant absoluteExpiry) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(sub).issuedAt(iat).expiresAt(exp)
        .claim("token_type", tokenType)
        .claim("absolute_exp", absoluteExpiry.getEpochSecond())
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims));
}
```

**Trade-offs:** No way to force-revoke a session before either expiry fires — this is D-09's accepted trade-off, not an oversight. A compromised refresh-token cookie remains valid until its idle window or the absolute cap lapses.

### Pattern 2: Cookie-borne JWT via `oauth2ResourceServer` with a custom `BearerTokenResolver`

**What:** Use `spring-boot-starter-oauth2-resource-server` (which transitively pulls in `spring-security-oauth2-jose`, giving you `NimbusJwtEncoder` for issuing *and* `NimbusJwtDecoder` for validating) rather than `spring-boot-starter-oauth2-client` (that starter is for consuming a third-party OAuth2/OIDC provider — not needed here, Jira uses Basic auth per D-15/D-16, not OAuth). Point the resource-server DSL at a custom `BearerTokenResolver` that reads the `access_token` cookie instead of the `Authorization` header.

**Example:**
```java
// config/SecurityConfig.java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, BearerTokenResolver cookieResolver) throws Exception {
    http
        .csrf(csrf -> csrf.spa())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(oauth2 -> oauth2
            .bearerTokenResolver(cookieResolver)
            .jwt(jwt -> jwt.decoder(decoder)))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/login", "/api/settings/jira-site", "/actuator/health").permitAll()
            .anyRequest().authenticated());
    return http.build();
}

@Bean
BearerTokenResolver cookieBearerTokenResolver() {
    return request -> Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
        .filter(c -> "access_token".equals(c.getName()))
        .findFirst()
        .map(Cookie::getValue)
        .orElse(null);
}
```
[CITED: docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/bearer-tokens.html — custom `BearerTokenResolver` mechanism]
[CITED: docs.spring.io/spring-security/reference/servlet/exploits/csrf.html — `csrf.spa()`, Spring Security 7.1]

**Trade-offs:** `csrf.spa()`'s default `CookieCsrfTokenRepository.withHttpOnlyFalse()` deliberately makes the CSRF cookie (`XSRF-TOKEN`) readable by JS — that is correct and required (the SPA must read it to echo it back as `X-XSRF-TOKEN`); do not confuse this with the JWT cookies, which **must** stay `httpOnly=true`.

### Pattern 3: Self-contained AES-256-GCM envelope in one `AttributeConverter` column

**What:** Store `key_version || IV || ciphertext` as one opaque, self-describing value in a single TEXT/BYTEA column, encrypted/decrypted by a Spring-managed `AttributeConverter`. Per D-11, rotation is a manual, planned batch job (re-encrypt every row with the new key, bump `key_version`) — not a runtime multi-key lookup — so the converter always uses the one currently-active key from the env var; `key_version` is recorded purely as queryable metadata for that future rotation job, not consulted by the converter to pick a decryption key at read time.

**Example:**
```java
// adapters/persistence/crypto/EncryptedTokenConverter.java
@Component // Hibernate wires @Component converters via Spring's bean container automatically
@Converter
public class EncryptedTokenConverter implements AttributeConverter<String, String> {

    private final SecretKey key;      // AES-256 key, decoded once from env var at startup
    private final int keyVersion;     // e.g. 1 — recorded alongside, not used to pick the key

    public EncryptedTokenConverter(@Value("${app.credential-key}") String base64Key,
                                    @Value("${app.credential-key-version}") int keyVersion) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
        this.keyVersion = keyVersion;
    }

    @Override
    public String convertToDatabaseColumn(String plainToken) {
        if (plainToken == null) return null;
        byte[] iv = new byte[12];              // GCM standard IV length
        new SecureRandom().nextBytes(iv);       // fresh, random IV per write — never reused
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plainToken.getBytes(UTF_8));
        // envelope: version(1 byte) || iv(12 bytes) || ciphertext+tag, base64-encoded whole
        ByteBuffer buf = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
        buf.put((byte) keyVersion).put(iv).put(ciphertext);
        return Base64.getEncoder().encodeToString(buf.array());
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) return null;
        byte[] raw = Base64.getDecoder().decode(stored);
        ByteBuffer buf = ByteBuffer.wrap(raw);
        buf.get(); // stored key version — not used for key selection in v1, single active key
        byte[] iv = new byte[12]; buf.get(iv);
        byte[] ciphertext = new byte[buf.remaining()]; buf.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), UTF_8);
    }
}
```
```java
// entity — plain int column for queryable rotation status, set alongside the encrypted value
@Column(name = "encrypted_token", nullable = false)
@Convert(converter = EncryptedTokenConverter.class)
private String token;

@Column(name = "key_version", nullable = false)
private int keyVersion; // set by the service layer from the same active-version config at write time
```
[VERIFIED: JPA `AttributeConverter` is a standard JPA 2.1+ interface — confirmed via multiple independent sources this session; Spring's auto-wiring of `@Component`-annotated converters through Hibernate's `SpringBeanContainer` is a well-established Spring Boot pattern]

**Trade-offs:** The envelope format (version+IV+ciphertext in one column) is simpler than a three-column layout (separate `iv`/`key_version` columns) because the converter has no access to sibling columns — a genuine JPA `AttributeConverter` limitation, not a design preference. The plain `key_version` entity column duplicates what's in the envelope purely so a future rotation job can `SELECT` by version without decrypting every row.

### Pattern 4: SPA fallback so client-side routes survive a refresh

**What:** A catch-all forward to `index.html` for any path that isn't an API call and doesn't look like a static asset (no dot in the last segment), so React Router deep links don't 404.

**Example:**
```java
// config/SpaForwardingConfig.java
@Controller
public class SpaForwardingController {
    @RequestMapping(value = {"/{path:^(?!api|actuator)[^.]*}", "/{path:^(?!api|actuator)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
```

### Anti-Patterns to Avoid

- **Storing the refresh token server-side "to be safe":** directly contradicts D-05/D-09. If revocation is ever truly needed, that is a deliberate, separately-discussed scope change (a denylist), not something to sneak in as an implementation detail here.
- **Returning the Jira token in any DTO, even masked/partial:** D-13 is absolute — the settings screen offers replacement only. Enforce by never adding a `token` field to any response DTO in the first place, not by scrubbing it at serialization time.
- **Using the `Authorization` header pattern's default `DefaultBearerTokenResolver`:** it looks for the token in the header; the cookie-based custom resolver above is mandatory, not optional, for this project's D-08 cookie-based design.
- **Letting the CSRF cookie and the JWT cookies share configuration:** `XSRF-TOKEN` must be JS-readable (`httpOnly=false`) by design; `access_token`/`refresh_token` must be `httpOnly=true`. Don't copy one cookie's flags onto the other.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT signing/verification | A custom HMAC-signing utility | `NimbusJwtEncoder`/`NimbusJwtDecoder` via `spring-security-oauth2-jose` | Handles JWS serialization, claim validation, clock skew — a hand-rolled signer is exactly the kind of crypto primitive that's easy to get subtly wrong |
| CSRF protection | A custom double-submit-cookie implementation | Spring Security 7's `csrf.spa()` | New in Spring Security 7.1 specifically to remove the historical pain of wiring CSRF for SPAs correctly (BREACH protection, token refresh on login/logout) |
| AES-GCM encryption primitives | A custom cipher wrapper reinventing IV/nonce handling | `javax.crypto.Cipher` with `GCMParameterSpec`, one random IV per encrypt call | GCM's security guarantee depends entirely on never reusing an IV with the same key — this is a narrow, well-documented API, not a place for cleverness |
| Architecture boundary checks | Manual code review / a custom bytecode scanner | ArchUnit (core module, plain `@Test`) | Purpose-built for exactly this; see the JUnit 6 pitfall below for the one wrinkle |
| Structured HTTP error bodies | A bespoke `{error, message}` JSON shape | `ProblemDetail` (RFC 9457), Spring's `ResponseEntityExceptionHandler` | Standardized, has a `Content-Type: application/problem+json`, and Spring MVC already renders it correctly from any `@ExceptionHandler` |

**Key insight:** every item above is a place where "just write 20 lines to do this" is tempting on a greenfield project and every one of them is a documented, standard Spring/JDK facility one import away — the only genuine engineering decision left is *how they're wired together* for this project's specific cookie/JWT/CSRF/encryption combination, which is what the patterns above document concretely.

## Common Pitfalls

### Pitfall 1: `archunit-junit5` does not support JUnit 6 (Spring Boot 4.1's managed version)

**What goes wrong:** Spring Boot 4.1 manages the JUnit Jupiter/Platform/Vintage 6.x line (unified versioning as of JUnit 6.0). ArchUnit's `archunit-junit5` module (which provides `@AnalyzeClasses` and JUnit-engine-based rule discovery) targets JUnit Platform 1.x and, as of this research, does not run under Platform 6.x — this is an open, unresolved GitHub issue (TNG/ArchUnit#1556, filed 2025-11-25) with no shipped fix in the latest release (1.4.2, April 2026).
**Why it happens:** Most ArchUnit tutorials and existing codebases were written against JUnit 5, so copying that pattern (`@AnalyzeClasses(packages = "...")` on a test class) onto a Boot 4.1/JUnit 6 project can fail to run at all, or fail with a confusing engine-discovery error.
**How to avoid:** Depend only on the core `archunit` artifact (not `archunit-junit5`). Write the architecture tests as plain `@org.junit.jupiter.api.Test` methods that manually call `new ClassFileImporter().importPackages(...)` and `rule.check(importedClasses)`. This has zero dependency on ArchUnit's own JUnit-engine integration and works under any JUnit Jupiter version, including 6.x, because it only uses the stable `@Test` annotation contract.
**Warning signs:** A test class annotated `@AnalyzeClasses` fails to be discovered by `mvn test`, or Maven Surefire reports zero tests run from that class.
**Confidence:** HIGH that the incompatibility exists (open issue + absence from all recent release notes, checked this session); MEDIUM that the plain-`@Test` workaround is sufficient long-term — re-check `archunit-junit5`'s status before adopting it in a later phase once a fix ships.

### Pitfall 2: Spring Security 7's CSRF default change silently 403s every POST/PUT/DELETE

**What goes wrong:** Spring Security 7 changed CSRF handling defaults in a way that, without explicit configuration, makes an SPA's state-changing requests come back `403 Forbidden` with no obvious error — easy to mistake for a CORS or auth bug.
**Why it happens:** Older Spring Security tutorials (pre-7.1) show manual `CookieCsrfTokenRepository` + custom `CsrfTokenRequestAttributeHandler` wiring; that pattern still technically works but is easy to get subtly wrong (e.g., forgetting the BREACH-protection XOR handler), and copying Boot-2/3-era csrf snippets verbatim risks exactly this.
**How to avoid:** Use `csrf.spa()` exactly as shown in Pattern 2 — it is the maintained, correct-by-default configuration for this exact scenario as of Spring Security 7.1.
**Warning signs:** Sign-in works (GET-based flows fine) but `POST /api/auth/login` or the settings `PUT` return 403 with no application-level error body.

### Pitfall 3: Treating the Jira Cloud API token's expiry as readable via API

**What goes wrong:** Unlike Jira Data Center Personal Access Tokens (which return `expirationDuration`/`tokenExpirationDateTime` at creation) or org-admin token-management APIs, there is no confirmed consumer-facing Jira Cloud endpoint that returns *a signed-in user's own* API token's expiry date — Atlassian community threads asking exactly this question report it is not exposed. [CITED: community.atlassian.com/forums — "Do we get the API token expiry date through rest API"]
**Why it happens:** It's tempting to plan an "expires in N days" banner (already explicitly deferred per CONTEXT.md's Deferred Ideas) assuming the data is one API call away — it isn't, for this credential type.
**How to avoid:** This phase correctly reacts only on failure (D-04/Deferred Ideas already settle this) — do not add code that speculatively calls a non-existent expiry-lookup endpoint.
**Confidence:** MEDIUM (community-sourced, not a primary Atlassian doc page, but consistent with why the project explicitly deferred advance-expiry warnings).

### Pitfall 4: Local dev cookie flags block the whole auth flow over plain HTTP

**What goes wrong:** `Secure` cookies are dropped by browsers on non-HTTPS origins; a `SecurityConfig` hardcoded to always set `Secure=true` means sign-in silently fails to persist a session on `localhost:8080` during local development (Vite dev server proxying to the Spring Boot backend over HTTP).
**Why it happens:** Copying a production-hardened cookie config without a profile-conditional relaxation for local dev.
**How to avoid:** Make the `Secure` flag conditional on an active Spring profile (e.g., `!local`), and use Vite's dev-server proxy (`server.proxy` in `vite.config.ts` pointing at the Spring Boot backend) so the SPA and API are same-origin in dev too — avoids a second, harder CORS+cookie problem on top of the `Secure`-flag one. [CITED: multiple current WebSearch results on Vite proxy + cookie/CSRF local-dev friction]
**Warning signs:** Sign-in returns 200 but the next request is treated as unauthenticated; cookies never appear in the browser's dev-tools storage panel.

### Pitfall 5: Deprecated Jira `/search` endpoint patterns leaking into this phase's Jira client

**What goes wrong:** Not directly triggered by Phase 1 (no ticket search here), but the same `JiraRestAuthAdapter`/`RestClient` setup built now is the template Phase 2's `JiraTicketProvider` will copy. If the HTTP client scaffolding here doesn't already establish "always use the current, documented endpoint shape," Phase 2 inherits sloppy habits.
**How to avoid:** Even for the single `/rest/api/3/myself` call in this phase, build the `RestClient` bean with explicit timeouts and no assumption that Jira responses are complete/fast — establishes the pattern Phase 2 will need for `/search/jql`.
**Phase to address:** Mostly forward-looking for Phase 2, but the `RestClient` bean and its resilience wrapper (timeout at minimum; full Resilience4j retry/circuit-breaker can wait) should be shaped correctly here since AUTH-01 already makes exactly one live outbound Jira call.

## Code Examples

### Jira Cloud credential validation call
```java
// adapters/jira/JiraRestAuthAdapter.java
@Component
@Profile("!stub-jira")
public class JiraRestAuthAdapter implements JiraAuthPort {

    private final RestClient restClient; // base URL = configured jira_site_config.base_url

    @Override
    public JiraIdentity validate(String email, String apiToken) {
        String basic = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(UTF_8));
        try {
            JiraMyselfResponse body = restClient.get()
                .uri("/rest/api/3/myself")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .retrieve()
                .body(JiraMyselfResponse.class);
            return new JiraIdentity(body.accountId(), body.emailAddress(), body.displayName());
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_FORBIDDEN);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_UNREACHABLE);
        }
    }
}
// JiraMyselfResponse fields per Jira Cloud v3: accountId, emailAddress, displayName, active
// [MEDIUM confidence — cross-corroborated via WebSearch this session, not fetched verbatim
//  from developer.atlassian.com due to a truncated page fetch; re-verify field names against
//  a real response during Wave 0's WireMock stub setup]
```

### Structured error response (`ProblemDetail`, extending Phase 2's JIRA-06 needs)
```java
// adapters/web/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(JiraAuthenticationException.class)
    public ProblemDetail handleJiraAuthFailure(JiraAuthenticationException ex) {
        HttpStatus status = switch (ex.reason()) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case JIRA_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case JIRA_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case JIRA_UNREACHABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle("Jira credential rejected");
        pd.setDetail("The supplied Jira email/API token could not be validated against the configured site.");
        pd.setProperty("reason", ex.reason().name()); // machine-readable — Phase 2's JIRA-06 branches on this
        return pd;
    }
}
```
[CITED: docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html — `ProblemDetail`/`ResponseEntityExceptionHandler`, RFC 9457]

### Stub Jira adapter for local dev (Spring profile)
```java
// adapters/jira/StubJiraAuthAdapter.java
@Component
@Profile("stub-jira")
public class StubJiraAuthAdapter implements JiraAuthPort {
    @Override
    public JiraIdentity validate(String email, String apiToken) {
        if ("dev@example.com".equals(email) && "valid-token".equals(apiToken)) {
            return new JiraIdentity("dev-account-id", email, "Dev User");
        }
        throw new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS);
    }
}
```
Integration tests instead use WireMock stubbing the real endpoint shape:
```java
@WireMockTest
class JiraRestAuthAdapterTest {
    @Test
    void rejectsWithInvalidCredentialsOn401(WireMockRuntimeInfo wm) {
        stubFor(get("/rest/api/3/myself").willReturn(unauthorized()));
        // assert JiraAuthenticationException with INVALID_CREDENTIALS
    }
}
```

### Flyway baseline (V1) for this phase's two tables
```sql
-- src/main/resources/db/migration/V1__init_schema.sql
CREATE TABLE jira_site_config (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),  -- single-row table, enforced
    base_url    VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255) NOT NULL
);

CREATE TABLE jira_credential (
    jira_account_id  VARCHAR(255) PRIMARY KEY,   -- Jira's accountId — stable, unlike email
    jira_email       VARCHAR(255) NOT NULL,
    encrypted_token  TEXT NOT NULL,               -- version||IV||ciphertext envelope, base64
    key_version      SMALLINT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
No `refresh_token` table — per Pattern 1, refresh tokens are stateless JWTs, never persisted server-side.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Manual `CookieCsrfTokenRepository` + hand-wired `CsrfTokenRequestAttributeHandler` for SPA CSRF | `http.csrf(csrf -> csrf.spa())` | Spring Security 7.1 | Fewer moving parts to get wrong; use this over any pre-7.1 tutorial snippet |
| `RestTemplate` for outbound HTTP | Spring's `RestClient` | Spring Framework 6.1+ (`RestTemplate` maintenance-mode since Spring 5) | Already a project-wide constraint, applies to the Jira `/myself` call here |
| Legacy Jira `/rest/api/*/search` | `/rest/api/3/search/jql` + `/changelog` | Sunset through 2025, now 410 Gone | Not triggered in Phase 1 (no ticket search yet), but the `RestClient`/adapter shape built here is Phase 2's template |
| Global Azure DevOps PATs | Org-scoped PATs / Entra service principal | Retiring 1 Dec 2026 | Not triggered in Phase 1 (no ADO yet) |

**Deprecated/outdated:** `archunit-junit5`'s engine-based `@AnalyzeClasses` discovery is not "deprecated" but is currently a compatibility dead-end under JUnit 6 — treat any tutorial using it as needing the plain-`@Test` adaptation described in Pitfall 1.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Jira Cloud `/rest/api/3/myself` returns `accountId`, `emailAddress`, `displayName`, `active` fields | Code Examples — Jira credential validation | Low — mapper/DTO would need one field-name fix once verified against a real response or a captured WireMock fixture during Wave 0 |
| A2 | Jira Cloud personal API tokens' expiry is not readable via any consumer REST endpoint | Pitfall 3 | Low — if wrong, an expiry-warning feature becomes easy to add later; does not block this phase either way (already deferred by CONTEXT.md) |
| A3 | Symmetric HS256 self-issued JWT (shared secret from env) is the "smaller correct" choice over asymmetric RS256/ES256 | Standard Stack — Alternatives Considered | Medium — if the org's security policy mandates asymmetric keys for JWT signing regardless of same-process issue/validate, this is a straightforward swap (`NimbusJwtEncoder`/`Decoder` support both) but changes key-management/env-var shape |
| A4 | Hibernate auto-wires `@Component`-annotated `AttributeConverter`s via Spring's bean container with no extra configuration | Pattern 3 | Medium — if this doesn't hold in Hibernate 7/Boot 4.1 exactly as in prior Boot versions, the converter needs an explicit `HibernatePropertiesCustomizer` bean registration instead; verify with one Wave 0 unit test that injects a `@Value` into the converter and confirms it's non-null at runtime |
| A5 | The plain-`@Test` + core-ArchUnit workaround (Pitfall 1) fully replaces what `archunit-junit5` would have provided for this project's rule set | Pitfall 1 | Low — the workaround loses `archunit-junit5`'s nicer failure reporting/IDE integration, not any actual rule-checking capability; re-adopt `archunit-junit5` once TNG/ArchUnit#1556 ships |

**If this table is empty:** N/A — see rows above.

## Open Questions

1. **Exact field names in a real Jira Cloud `/myself` response for the target instance**
   - What we know: the general shape (`accountId`, `emailAddress`, `displayName`, `active`) from cross-corroborated search results.
   - What's unclear: whether this specific org's Jira Cloud site has any tenant-specific quirks (e.g., email visibility settings can null out `emailAddress` for some account types).
   - Recommendation: capture one real response (with a throwaway personal token against the actual configured site) as a WireMock fixture during Wave 0, before writing the mapper.

2. ~~**HS256 vs RS256 for the self-issued JWT**~~ — **RESOLVED 2026-07-28 by the user: HS256 with a shared secret from an env var**, mirroring the D-11 encryption-key pattern. Do not implement RS256.

3. ~~**JDK 17 (installed) vs JDK 25 (stack target)**~~ — **RESOLVED 2026-07-28 by the user: target Java 25.** A JDK 25 distribution must be installed before the build can compile; the plan must treat this as an explicit prerequisite step rather than assuming the host toolchain. `maven.compiler.release`/`<release>` is 25, and the Environment Availability table below is superseded on this point.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Backend build/run | Partial | 17.0.16 (Microsoft build) installed; recommended 25 not installed | Spring Boot 4.1 fully supports 17 as its floor — build on 17 now, or install a JDK 25 distribution before Wave 0 if the team wants to start on the target LTS immediately |
| Maven | Build tool | Yes | 3.9.11 | — |
| Gradle | Alternative build tool | No | — | Not needed; Maven is available and is this research's recommendation |
| Node.js | Frontend build (via frontend-maven-plugin, downloaded automatically) | Yes | 20.19.0 | Meets Vite 8's floor (20.19+) exactly; frontend-maven-plugin can also download its own pinned Node version independent of the host, so this isn't strictly load-bearing |
| Docker | Testcontainers (Postgres + optional WireMock container) | Yes | 29.1.3 (Docker Desktop) | — |
| PostgreSQL (native client) | Manual DB inspection during dev | No (`psql` not on PATH) | — | Not required — Testcontainers manages ephemeral Postgres for tests; a real dev Postgres instance can be provisioned via Docker directly (`docker run postgres:18`) without a local client, or a GUI tool used for manual inspection |
| Live Jira Cloud site + real API token | Manual/UAT verification of AUTH-01 against a real instance | Unknown — not available in this research session | — | Stub profile (`stub-jira`) + WireMock integration tests cover automated verification; manual UAT against a real site is required before considering AUTH-01 truly done, per the phase's own success criteria wording ("validates live against Jira") |

**Missing dependencies with no fallback:**
- None — every gap above has a documented, viable fallback.

**Missing dependencies with fallback:**
- JDK 25 not installed locally (fallback: build on the already-installed JDK 17, which Spring Boot 4.1 fully supports).
- No live Jira Cloud site/token in this environment (fallback: stub profile + WireMock cover automated tests; real-instance UAT is a separate, explicit manual step the plan should call out).

## Validation Approach

`workflow.nyquist_validation` is `false` in `.planning/config.json`, so the full test-framework/requirement-to-test-command mapping is not required. The phase description explicitly asks how each success criterion is verified, so a lighter mapping is provided here for the planner and later gates:

| Success Criterion | Test Type | How |
|---|---|---|
| 1. Jira Cloud site configurable and visible in UI | Unit (service) + integration (Testcontainers Postgres, Flyway applies `V1`) | `JiraSiteConfigService` unit test; repository integration test asserting the seeded row round-trips |
| 2. Sign-in validates live against Jira before granting access | Integration (WireMock stubbing `/rest/api/3/myself` 200) + manual UAT (real Jira Cloud site) | `JiraRestAuthAdapterTest` against WireMock for the automated path; one manual sign-in against a real instance before calling AUTH-01 done |
| 3. Invalid/expired/revoked token rejected with a clear error, not a crash | Integration (WireMock 401/403/timeout) + unit (`GlobalExceptionHandler` maps each `JiraAuthFailureReason` to the right `ProblemDetail`) | Table-driven test over all `JiraAuthFailureReason` values |
| 4. Credentials encrypted at rest, never in any API response | Unit (`EncryptedTokenConverter` round-trip, no Spring context needed) + integration (raw SQL query in a Testcontainers test asserting the stored column is not plaintext) + a contract test that serializes every response DTO and asserts no `token`-shaped field exists | Converter unit test is fast and framework-free; the "never in any response" property is best caught by a reflection-based DTO test, not manual review |
| 5. Token replacement doesn't lose releases/templates/history | Integration (Testcontainers): create credential row, replace token, assert row identity (`jira_account_id`) unchanged and `updated_at` bumped | No releases/templates exist yet in Phase 1 — the assertion is necessarily about row identity/stability, not cross-table survival, until Phase 3+ exist |

**Framework:** JUnit Jupiter (Boot-managed 6.x line) + AssertJ + Mockito for units; `@SpringBootTest` + Testcontainers `postgresql` module + `@ServiceConnection` for persistence integration tests; WireMock (standalone, `@WireMockTest` extension) for the Jira HTTP boundary. ArchUnit tests run as plain `@Test` methods per Pitfall 1 — include them in the same Maven `test` phase as everything else, no separate build step needed.

## Security Domain

ASVS Level 1 enforcement is on (`security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: "high"` per config).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | Yes | Jira-delegated credential check (no local password store); rate-limit/backoff on the sign-in endpoint is **not** built in this phase (no requirement calls for it) — flag as an accepted gap, not an oversight, consistent with D-01's "no allowlist, no group check" scope |
| V3 Session Management | Yes | Stateless JWT pair, httpOnly/Secure/SameSite cookies, absolute + idle expiry (Pattern 1) |
| V4 Access Control | Minimal | No role/permission model in this phase (D-03); every authenticated request is authorized identically — this is a deliberate scope boundary, not a gap |
| V5 Input Validation | Yes | Bean Validation (`spring-boot-starter-validation`) on sign-in DTO (email format, non-blank token) and site-config DTO (URL format) |
| V6 Cryptography | Yes | AES-256-GCM for the stored Jira token (Pattern 3), random IV per encryption, key from env var (D-11) — never a hand-rolled cipher mode |
| V7 Error Handling / Logging | Yes | `ProblemDetail` responses never include the raw Jira token or the JWT secret; logs must not print the Basic-auth header or cookie values verbatim — a concrete logging-hygiene check belongs in the plan's verification steps |
| V9 Communications | Yes | `Secure` cookie flag in non-local profiles; same-origin single-jar deployment removes most CORS attack surface by construction |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| CSRF against the same-origin SPA's state-changing endpoints (login, site-config update, token replace) | Tampering | `csrf.spa()` (Pattern 2) — do not disable CSRF for any of these endpoints even though they're same-origin |
| JWT `alg` confusion / accepting an attacker-supplied `alg: none` | Spoofing | `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256)` pins the expected algorithm explicitly — never build a decoder that trusts the token's own `alg` header |
| Refresh token replayed as an access token (or vice versa) | Elevation of Privilege | `token_type` claim checked explicitly in both the resource-server matcher and the refresh endpoint (Pattern 1) |
| AES-GCM IV reuse under the same key | Tampering (breaks confidentiality/integrity) | Fresh `SecureRandom` IV per encrypt call (Pattern 3) — never derive the IV from a counter or timestamp alone without a per-row random component |
| Jira Basic-auth header or JWT secret leaking into logs/error responses | Information Disclosure | Never log the `Authorization` header value; `ProblemDetail` bodies carry only the `reason` enum, never upstream response bodies verbatim |
| Cookie `Secure` flag accidentally disabled in production via a misconfigured profile | Information Disclosure | Fail-fast: assert the active profile forces `Secure=true` outside `local`/`stub-jira`, ideally via a startup check, not just documentation |

## Sources

### Primary (HIGH confidence)
- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html — `csrf.spa()`, `CookieCsrfTokenRepository`, BREACH protection (Spring Security 7.1)
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/bearer-tokens.html — custom `BearerTokenResolver`
- https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html — `ProblemDetail`, RFC 9457, `ResponseEntityExceptionHandler`
- https://spring.io/blog/2026/06/10/spring-boot-4/ — Spring Boot 4.1.0 GA
- Maven Central metadata (`frontend-maven-plugin`, ArchUnit) fetched this session
- `npm view <pkg> version` (Vite, TypeScript, React, TanStack Router/Query) fetched this session
- Direct shell probe of this dev machine (JDK, Maven, Node, Docker, Gradle, `psql`) this session
- .planning/research/STACK.md, ARCHITECTURE.md, PITFALLS.md, SUMMARY.md — project's own prior research (2026-07-27), treated as canonical per CONTEXT.md's canonical_refs

### Secondary (MEDIUM confidence)
- GitHub issue TNG/ArchUnit#1556 and archunit.org/news + GitHub releases — ArchUnit/JUnit 6 incompatibility, cross-checked across two independent fetches this session
- WebSearch aggregate on Jira Cloud `/myself` response shape (accountId/emailAddress/displayName/active) — multiple independent snippets agree, not fetched verbatim from a single primary page (fetch was truncated)
- Community thread on Jira Cloud API token expiry not being exposed via REST — atlassian community forum, not an official doc page
- WebSearch aggregate on Vite dev-proxy + cookie/CSRF local-dev friction — multiple independent sources agree

### Tertiary (LOW confidence)
- None carried forward as authoritative — all LOW-confidence findings above were either cross-verified to MEDIUM or explicitly flagged in the Assumptions Log instead of stated as fact.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against Maven Central/npm registry or direct shell probe this session
- Architecture: HIGH — Spring Security 7 CSRF/JWT patterns fetched directly from current official docs
- Pitfalls: MEDIUM-HIGH — the ArchUnit/JUnit 6 gap is HIGH confidence (verified, currently unresolved); the Jira `/myself` field shape and token-expiry-not-readable claims are MEDIUM (cross-corroborated search, not a single primary-doc fetch)

**Research date:** 2026-07-28
**Valid until:** 30 days for the backend/frontend stack facts (stable ecosystem); re-check the ArchUnit/JUnit 6 status specifically before Wave 0 starts, since that upstream issue could resolve at any time between now and implementation.
