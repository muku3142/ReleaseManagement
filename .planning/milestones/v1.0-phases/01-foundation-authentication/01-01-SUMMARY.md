---
phase: 01-foundation-authentication
plan: 01
subsystem: backend-foundation
tags: [spring-boot-4.1, java-25, flyway, jpa, hexagonal-architecture, archunit, jira-site-config, problemdetail]
dependency-graph:
  requires: []
  provides:
    - "domain/jira/JiraSiteConfig record"
    - "ports/JiraSiteConfigRepository"
    - "application/JiraSiteConfigService (get/updateBaseUrl/seedIfAbsent)"
    - "GET|PUT /api/settings/jira-site"
    - "GlobalExceptionHandler ProblemDetail reason codes: SITE_NOT_CONFIGURED, VALIDATION_FAILED, INTERNAL_ERROR"
    - "Flyway V1__init_schema.sql (jira_site_config, jira_credential)"
    - "ArchitectureBoundaryTest (domain/ports framework-import ban, application->adapters ban)"
  affects:
    - "All later Phase 1 plans (02-08): build on this pom.xml, application.yml keys, package layout, and GlobalExceptionHandler reason-code registry"
tech-stack:
  added:
    - "Spring Boot 4.1.0 (Spring MVC, JPA/Hibernate 7, Validation, Actuator)"
    - "Java 25 (Microsoft build) at C:\\Users\\ucs\\.jdks\\jdk-25"
    - "Flyway via spring-boot-starter-flyway + flyway-database-postgresql"
    - "PostgreSQL 18 (compose.yaml + spring-boot-docker-compose)"
    - "ArchUnit 1.4.2 core (plain @Test, no archunit-junit5)"
    - "Testcontainers 2.0.5 (testcontainers-postgresql / testcontainers-junit-jupiter module names)"
    - "spring-boot-starter-webmvc-test for @WebMvcTest"
    - "WireMock 3.13.2 (dependency added, not yet used — reserved for plan 03's Jira client)"
  patterns:
    - "Hexagonal layering: domain/ -> ports/ -> application/ -> adapters/{persistence,web}/ -> config/, enforced by ArchUnit"
    - "RFC 9457 ProblemDetail with a reason extension property on every error response"
    - "Single-row config table pattern (CHECK id = 1) seeded via ApplicationRunner"
key-files:
  created:
    - pom.xml
    - compose.yaml
    - .gitignore
    - README.md
    - src/main/resources/application.yml
    - src/main/resources/application-local.yml
    - src/test/resources/application.yml
    - src/main/resources/db/migration/V1__init_schema.sql
    - src/main/java/com/evision/releasemgmt/ReleaseManagementApplication.java
    - src/main/java/com/evision/releasemgmt/domain/jira/JiraSiteConfig.java
    - src/main/java/com/evision/releasemgmt/ports/JiraSiteConfigRepository.java
    - src/main/java/com/evision/releasemgmt/application/JiraSiteConfigService.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/JiraSiteConfigEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/JiraSiteConfigJpaRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/JiraSiteConfigRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/JiraSiteConfigResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/UpdateJiraSiteConfigRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/main/java/com/evision/releasemgmt/config/JiraSiteConfigSeeder.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/JiraSiteConfigPersistenceIT.java
    - src/test/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigControllerTest.java
  modified: []
decisions:
  - "Testcontainers 2.x renamed the postgresql/junit-jupiter modules to testcontainers-postgresql/testcontainers-junit-jupiter — used the renamed artifact IDs at 2.0.5"
  - "Spring Boot 4.1 extracted Flyway autoconfiguration out of spring-boot-autoconfigure into a dedicated spring-boot-starter-flyway starter — raw flyway-core no longer autoconfigures on its own"
  - "Spring Boot 4.1 also extracted @WebMvcTest support into spring-boot-starter-webmvc-test, with the annotation package moved to org.springframework.boot.webmvc.test.autoconfigure"
  - "Surefire is configured with explicit includes (*Test.java, *Tests.java, *IT.java) so plain 'mvn test' runs the Testcontainers persistence IT — no Failsafe plugin is used in this project"
metrics:
  duration_minutes: 34
  tasks_completed: 3
  files_created: 22
  completed_date: "2026-07-28"
---

# Phase 01 Plan 01: Foundation Scaffold & Jira Site Config Summary

Spring Boot 4.1 hexagonal backend scaffolded on Java 25 with an enforced domain/ports/adapters boundary, a Flyway-baselined PostgreSQL 18 schema, and the Jira-site-configuration vertical slice (Postgres to REST) shipped end-to-end behind the project's permanent `ProblemDetail` error shape.

## What Was Built

**Task 1 — JDK 25 + Maven scaffold.** JDK 25.0.4 (Microsoft build) installed to the fixed path `C:\Users\ucs\.jdks\jdk-25`; `JAVA_HOME` persisted via `setx` for future sessions. Every Maven command in this and later plans must still set `export JAVA_HOME="$HOME/.jdks/jdk-25"` inline in each Bash tool call, since the tool starts a fresh shell per call and does not inherit the `setx` change mid-session. `pom.xml` declares the Spring Boot 4.1.0 parent, `<java.version>25</java.version>` / `<maven.compiler.release>25</maven.compiler.release>`, and a file-activated `frontend` Maven profile (keyed on `frontend/package.json` existing) so the backend builds standalone today. `application.yml` declares every `app.*` key the whole phase needs (`app.jira.*`, `app.credential.*`, `app.jwt.*`, `app.security.*`) so no later plan has to edit this shared file. `ArchitectureBoundaryTest` uses ArchUnit's core module with plain `@Test` methods (not `archunit-junit5`, which doesn't yet support JUnit 6) and `allowEmptyShould(true)` on each rule so the rules pass cleanly today (empty `domain`/`ports`/`application` packages) and will start enforcing the moment later plans populate those packages.

**Task 2 — Flyway baseline + persistence slice.** `V1__init_schema.sql` creates `jira_site_config` (single-row, `CHECK (id = 1)`) and `jira_credential` (with `key_version`) — no session/refresh/denylist table, per D-05/D-09. `JiraSiteConfigService.seedIfAbsent` runs via an `ApplicationRunner` (`JiraSiteConfigSeeder`) reading `app.jira.default-base-url`, satisfying D-16 (site usable before anyone signs in). A Testcontainers-backed `@SpringBootTest` (`JiraSiteConfigPersistenceIT`) proves the seed, an update with a recorded actor, and the single-row invariant against a real Postgres 18 container.

**Task 3 — REST endpoints + error shape.** `GET|PUT /api/settings/jira-site` with `UpdateJiraSiteConfigRequest.baseUrl` pinned by regex to `https://<site>.atlassian.net` (SSRF control, threat T-01-01). `GlobalExceptionHandler` (`@RestControllerAdvice extends ResponseEntityExceptionHandler`) registers the three reason codes below and never echoes `ex.getMessage()` for unexpected errors. The controller resolves `java.security.Principal` for the acting user — currently always `null`/`"anonymous"` until plan 05 wires Spring Security, with no signature change needed then.

**Registered `reason` codes (extend, do not duplicate, in later plans):**
| Reason | HTTP Status | Trigger |
|--------|-------------|---------|
| `SITE_NOT_CONFIGURED` | 409 Conflict | `JiraSiteConfigService.get()` when no row exists |
| `VALIDATION_FAILED` | 400 Bad Request | Bean Validation failure (`@Valid` on any request body); response also carries a `fieldErrors` map |
| `INTERNAL_ERROR` | 500 Internal Server Error | Any uncaught exception; detail is a fixed generic string, the real exception is logged server-side only |

**End-to-end manual verification performed:** started the app with `mvn spring-boot:run -Dspring-boot.run.profiles=local` against the real `postgres:18` container from `compose.yaml`; confirmed `GET /api/settings/jira-site` returns the seeded URL, `PUT` with a non-Jira-Cloud URL returns 400, `PUT` with a valid Jira Cloud URL persists and round-trips on a subsequent `GET`, and `/actuator/health` reports `UP`.

## Verification

- `mvn -v` reports Java 25.0.4 from the fixed JDK path.
- `mvn test` — 11 tests, all green: 3 ArchUnit boundary rules, 3 Testcontainers persistence IT assertions, 5 `@WebMvcTest` controller cases.
- `mvn -DskipTests package` succeeds.
- Manual `curl` verification against a live `spring-boot:run` instance (see above) — all 4 checks passed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ArchUnit rules failed on empty packages**
- **Found during:** Task 1 verification
- **Issue:** `domain/`, `ports/`, `application/` packages don't exist yet in Task 1 (only `ReleaseManagementApplication.java` exists); ArchUnit 1.4.2 defaults to failing a rule that matches zero classes.
- **Fix:** Added `.allowEmptyShould(true)` to all three rules so they pass cleanly today and start enforcing automatically once later tasks/plans populate those packages.
- **Files modified:** `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java`
- **Commit:** c41c514

**2. [Rule 1 - Bug] `JiraSiteConfigEntity` no-arg constructor was `protected`, blocking cross-package instantiation**
- **Found during:** Task 2 compilation
- **Issue:** `JiraSiteConfigRepositoryAdapter` (in `adapters.persistence`) could not call the `protected` no-arg constructor of `JiraSiteConfigEntity` (in `adapters.persistence.entity`) via method reference.
- **Fix:** Changed the constructor to `public`. JPA only requires a no-arg constructor to be at least `protected`; the adapter's cross-package usage requires `public`.
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/JiraSiteConfigEntity.java`
- **Commit:** e196c20

**3. [Rule 3 - Blocking] `org.testcontainers:postgresql`/`junit-jupiter` at `2.0.5` don't exist on Maven Central**
- **Found during:** Task 1 `mvn package` (dependency resolution failure)
- **Issue:** Testcontainers 2.x renamed these sub-modules with a `testcontainers-` prefix (`testcontainers-postgresql`, `testcontainers-junit-jupiter`); the plan's coordinates matched the pre-2.x naming.
- **Fix:** Updated `pom.xml` to the renamed artifact IDs at the same `2.0.5` version (confirmed present on Maven Central and in the `testcontainers-bom:2.0.5`).
- **Files modified:** `pom.xml`
- **Commit:** e196c20

**4. [Rule 3 - Blocking] Flyway never ran — Spring Boot 4.1 moved Flyway autoconfiguration out of `spring-boot-autoconfigure`**
- **Found during:** Task 2 `JiraSiteConfigPersistenceIT` (table did not exist)
- **Issue:** `spring-boot-autoconfigure-4.1.0.jar` contains zero Flyway-related classes; raw `flyway-core` + `flyway-database-postgresql` on the classpath no longer triggers any autoconfiguration. Confirmed a new dedicated `org.springframework.boot:spring-boot-starter-flyway` artifact exists at `4.1.0` (matches project's own STACK.md note on this exact starter, which the plan had not carried through to the pom.xml step).
- **Fix:** Replaced `flyway-core` with `spring-boot-starter-flyway` in `pom.xml`, keeping `flyway-database-postgresql` alongside it.
- **Files modified:** `pom.xml`
- **Commit:** e196c20

**5. [Rule 1 - Bug] `src/test/resources/application.yml` fully shadows `src/main/resources/application.yml` on the test classpath**
- **Found during:** Task 2 `JiraSiteConfigPersistenceIT` (placeholder resolution failure for `app.jira.default-base-url`)
- **Issue:** Spring Boot resolves `classpath:/application.yml` as a single resource lookup; when both `target/classes/application.yml` and `target/test-classes/application.yml` exist, only the first one found on the classpath (test-classes, which precedes classes) is loaded — it does not merge with `src/main/resources/application.yml`. The plan's test-only yml therefore left `app.jira.default-base-url` unresolved in tests.
- **Fix:** Added `app.jira.default-base-url: https://test.atlassian.net` directly to `src/test/resources/application.yml`, keeping the test fixture self-contained.
- **Files modified:** `src/test/resources/application.yml`
- **Commit:** e196c20

**6. [Rule 3 - Blocking] `@WebMvcTest` unresolved — Spring Boot 4.1 moved it into its own starter/package**
- **Found during:** Task 3 `JiraSiteConfigControllerTest` compilation
- **Issue:** `spring-boot-test-autoconfigure-4.1.0.jar` contains no MVC-test classes; Boot 4.1 introduced a dedicated `spring-boot-starter-webmvc-test` (and `spring-boot-webmvc-test`) artifact, moving `@WebMvcTest`/`@AutoConfigureMockMvc` to the package `org.springframework.boot.webmvc.test.autoconfigure`.
- **Fix:** Added `spring-boot-starter-webmvc-test` (test scope) to `pom.xml` and updated the import in `JiraSiteConfigControllerTest`.
- **Files modified:** `pom.xml`, `src/test/java/com/evision/releasemgmt/adapters/web/JiraSiteConfigControllerTest.java`
- **Commit:** af920bf

**7. [Rule 3 - Blocking] Plain `mvn test` skipped `JiraSiteConfigPersistenceIT`**
- **Found during:** Task 3 full-suite verification (`mvn test` ran 8 tests, not the expected 11)
- **Issue:** Maven Surefire's default include pattern is `**/*Test.java`, `**/*Tests.java`, `**/Test*.java` — it does not match `*IT.java` (that convention is normally run by the Failsafe plugin, which this project does not use). The plan's own acceptance criteria requires plain `mvn test` to run the whole suite including the Testcontainers IT.
- **Fix:** Added explicit Surefire `<includes>` covering `*Test.java`, `*Tests.java`, and `*IT.java`.
- **Files modified:** `pom.xml`
- **Commit:** af920bf

**8. [Environment] Docker Desktop daemon was not running**
- **Found during:** Task 2 first `JiraSiteConfigPersistenceIT` attempt
- **Issue:** Testcontainers could not reach the Docker API (`npipe` connection failure) — Docker Desktop was installed but not started.
- **Fix:** Started Docker Desktop (`Start-Process`) and waited for the daemon to accept connections before retrying.
- **Files modified:** None (environment-only).

**9. [Environment] `mvn spring-boot:run`'s Docker Compose auto-integration hit a Docker CLI/daemon API version negotiation error**
- **Found during:** Manual end-to-end verification (final `<verification>` step of the plan)
- **Issue:** `spring-boot-docker-compose`'s `docker inspect` subprocess call returned "500 Internal Server Error ... check if the server supports the requested API version" against this machine's Docker Desktop, even though the container itself started successfully and Testcontainers (a separate code path) connects to the same daemon without issue.
- **Fix (verification-only, no code change):** Started Postgres via `docker compose up -d` directly and re-ran `spring-boot:run` with `--spring.docker.compose.enabled=false` for the manual smoke test. Not a pom/code change — `spring.docker.compose.enabled` stays `true` in `application.yml` for normal developer use; this was a one-off local Docker CLI/daemon skew on this verification pass.

None of the above required an architectural decision (Rule 4) — all were either bugs in the plan's exact version pins/paths (corrected against live Maven Central / actual JAR contents) or local environment state (Docker not running).

## Known Stubs

None. Every piece of Task 1-3 functionality (JDK toolchain, schema, persistence, REST endpoints, error handling) is fully wired end-to-end with no placeholder/mock data path in the production code.

## Threat Flags

None beyond what the plan's own `<threat_model>` already covers (T-01-01 through T-01-06) — no new network endpoints, auth paths, or schema surfaces were introduced beyond what the plan specified.

## Self-Check: PASSED

All 22 created files verified present on disk. All 4 commits (c41c514, e196c20, af920bf, 900a1aa) verified in git log.
