---
phase: 04-template-authoring
plan: 05
subsystem: api
tags: [imageio, multipart, spring-mvc, content-sniffing, template-crud]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 02
    provides: "Template aggregate, LogoImage/LogoMetadata records, TemplateRepository.saveLogo/deleteLogo/findLogo"
  - phase: 04-template-authoring
    plan: 04
    provides: "TemplateService, TemplateController, GlobalExceptionHandler with the Phase 04 reason-code taxonomy"
provides:
  - "LogoValidator: content-sniffed PNG/JPEG type validation with byte and pixel-dimension caps"
  - "LogoRejectionReason/LogoRejectedException: the three-reason exception shape for the SPA"
  - "POST/DELETE/GET /api/templates/{id}/logo: upload (doubles as replace), remove, download"
  - "Three LOGO_* ProblemDetail reason codes plus a MaxUploadSizeExceededException override"
affects: [04-06, 04-07, phase-05-document-generation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "javax.imageio ImageReader.getWidth/getHeight for header-only dimension reads, never ImageIO.read, to defeat decompression-bomb-shaped uploads"
    - "Overriding ResponseEntityExceptionHandler's own protected hook (handleMaxUploadSizeExceededException) instead of a plain @ExceptionHandler, when the exception is one Spring's base class already resolves internally"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/template/LogoRejectionReason.java
    - src/main/java/com/evision/releasemgmt/domain/template/LogoRejectedException.java
    - src/main/java/com/evision/releasemgmt/application/LogoValidator.java
    - src/test/java/com/evision/releasemgmt/application/LogoValidatorTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TemplateLogoControllerTest.java
  modified:
    - src/main/resources/application.yml
    - src/test/resources/application.yml
    - src/main/java/com/evision/releasemgmt/application/TemplateService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/test/java/com/evision/releasemgmt/application/TemplateServiceTest.java

key-decisions:
  - "uploadLogo carries no actor parameter - the logo table has no updated_by column (D-04-16), so there is nothing to record it into; carrying it anyway for symmetry would be a dead parameter. The controller's uploadLogo endpoint likewise takes no Principal, since it would have had no use."
  - "MaxUploadSizeExceededException is handled by overriding ResponseEntityExceptionHandler.handleMaxUploadSizeExceededException rather than a plain @ExceptionHandler - the superclass already resolves that exception through its own handleException dispatch, and a competing @ExceptionHandler for the same type is rejected at context-startup as ambiguous."

patterns-established:
  - "Header-only image dimension reads via ImageReader.getWidth(0)/getHeight(0) inside a try/finally that disposes the reader, catching IOException as a content-validation failure rather than letting it propagate raw"

requirements-completed: [TMPL-09]

# Metrics
duration: 25min
completed: 2026-08-09
---

# Phase 04 Plan 05: Template Logo Upload Summary

**LogoValidator sniffs PNG/JPEG magic bytes (rejecting SVG-named-.png by content), bounds byte size before touching content and reads pixel dimensions from the image header without ever calling ImageIO.read, wired into upload/replace/remove/download endpoints with three distinct LOGO_* reason codes.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-08-09T15:35:00+03:00
- **Completed:** 2026-08-09T16:00:00+03:00
- **Tasks:** 2
- **Files modified:** 11 (5 created, 6 modified)

## Accomplishments

- `LogoValidator` sniffs the PNG 8-byte signature and JPEG SOI marker before anything else runs;
  an SVG (or GIF/PDF/ZIP) named `.png` is refused with `INVALID_TYPE` regardless of filename or
  declared Content-Type. The byte-size cap (`app.template.logo.max-bytes`) is checked before any
  content inspection, so an oversized file costs nothing to reject. Pixel dimensions are read via
  `ImageReader.getWidth(0)`/`getHeight(0)` - the header only, never `ImageIO.read` - so a small file
  declaring an oversized width/height is rejected without a raster ever being allocated
  (T-04-25, decompression-bomb mitigation).
- `LogoValidatorTest` has 10 tests, one or more per `<behavior>` bullet: valid PNG, valid JPEG, SVG
  named `.png`, GIF/PDF/ZIP magic bytes, empty array, oversized bytes rejected before decoding,
  oversized declared dimensions, and a PNG-signature-but-unparseable stream (proving `INVALID_TYPE`,
  not a raw `IOException`, is what surfaces).
- `TemplateService` gained `uploadLogo`/`removeLogo`/`getLogo`; `TemplateController` gained
  `POST/DELETE/GET /{templateId}/logo`. Upload doubles as replace (`saveLogo` overwrites). Download
  sets `Content-Type` from the stored whitelisted value, `X-Content-Type-Options: nosniff`,
  `Content-Disposition: inline` and `Cache-Control: no-store` so stored bytes can never be
  reinterpreted as HTML/script by a browser (T-04-27).
- `GlobalExceptionHandler` maps the three `LogoRejectionReason` values to `LOGO_INVALID_TYPE` (415),
  `LOGO_TOO_LARGE` (413) and `LOGO_DIMENSIONS_EXCEEDED` (422), and overrides
  `handleMaxUploadSizeExceededException` (Spring's own multipart-ceiling rejection) to the same
  `LOGO_TOO_LARGE` reason, so a file above `spring.servlet.multipart.max-file-size` gets the same
  actionable copy as one above the application-level cap instead of a bare 500.
- `spring.servlet.multipart.max-file-size`/`max-request-size: 4MB` added to both `application.yml`
  files, above the 2 MB application cap so the app-level `TOO_LARGE` reason wins for anything between
  the two, per the plan's stated ordering.
- `TemplateLogoControllerTest` has 7 `@WebMvcTest` slice assertions: valid upload returns 200 with
  `logo.contentType`, each of the three rejection reasons maps to its distinct status/reason pair,
  remove returns 200 with `logo` null, download returns the exact bytes with `nosniff`, and
  downloading a template's logo when none exists returns 404 `TEMPLATE_NOT_FOUND`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Write the content-sniffing logo validator** - `23068ca` (test)
2. **Task 2: Wire the logo endpoints and the three rejection reason codes** - `926858d` (feat)

**Plan metadata:** pending (docs: complete plan, orchestrator commits after all worktree agents in this wave merge)

## Files Created/Modified

- `src/main/java/com/evision/releasemgmt/domain/template/LogoRejectionReason.java` - `INVALID_TYPE`/`TOO_LARGE`/`DIMENSIONS_EXCEEDED`
- `src/main/java/com/evision/releasemgmt/domain/template/LogoRejectedException.java` - reason-carrying exception, copies `JiraAuthenticationException`'s shape
- `src/main/java/com/evision/releasemgmt/application/LogoValidator.java` - content-sniffing validator with byte/dimension caps
- `src/test/java/com/evision/releasemgmt/application/LogoValidatorTest.java` - 10 tests covering every `<behavior>` bullet
- `src/main/resources/application.yml`, `src/test/resources/application.yml` - added `spring.servlet.multipart.max-file-size/max-request-size: 4MB`
- `src/main/java/com/evision/releasemgmt/application/TemplateService.java` - `uploadLogo`/`removeLogo`/`getLogo`
- `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java` - upload/remove/download endpoints
- `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` - `LOGO_*` reason codes, `handleMaxUploadSizeExceededException` override
- `src/test/java/com/evision/releasemgmt/adapters/web/TemplateLogoControllerTest.java` - 7 `@WebMvcTest` slice assertions
- `src/test/java/com/evision/releasemgmt/application/TemplateServiceTest.java` - constructor call updated for the new `LogoValidator` dependency

## Decisions Made

- **No actor parameter on `uploadLogo`.** The plan explicitly offered this choice: "if you would
  rather not carry an unused parameter, drop it and say so in the summary." Dropped from both
  `TemplateService.uploadLogo` and the controller's upload endpoint (no `Principal` parameter),
  since the logo table has no `updated_by` column to record it into.
- **`MaxUploadSizeExceededException` handled via override, not `@ExceptionHandler`.** The plan's own
  code sample used `@ExceptionHandler(MaxUploadSizeExceededException.class)`, but on this Spring Boot
  4.1 / Spring Framework 7 version `ResponseEntityExceptionHandler` (which `GlobalExceptionHandler`
  extends) already resolves that exception type through its own `handleException` dispatch via a
  protected `handleMaxUploadSizeExceededException` hook. A competing `@ExceptionHandler` for the same
  type fails context startup as "Ambiguous @ExceptionHandler method mapped." Overriding the
  superclass's hook - the same pattern already used for `handleMethodArgumentNotValid` in this file -
  resolves it cleanly with identical `LOGO_TOO_LARGE` behaviour.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed `TemplateServiceTest`'s broken constructor call**
- **Found during:** Task 2, first full-suite run after adding the `LogoValidator` dependency to `TemplateService`
- **Issue:** `TemplateServiceTest` (created by plan 04-04, predates this plan) constructed
  `TemplateService` with the old two-argument constructor; adding the required `LogoValidator`
  parameter broke compilation for the whole test module
- **Fix:** Updated the one constructor call site to pass `new LogoValidator(2097152, 2000, 2000)`
- **Files modified:** `src/test/java/com/evision/releasemgmt/application/TemplateServiceTest.java`
- **Verification:** `mvn -Dtest=TemplateServiceTest test` passes (9/9)
- **Committed in:** `926858d` (Task 2 commit)

**2. [Rule 1 - Bug] Replaced `@ExceptionHandler(MaxUploadSizeExceededException.class)` with an override**
- **Found during:** Task 2, `TemplateLogoControllerTest` context load
- **Issue:** The plan's `@ExceptionHandler` approach for `MaxUploadSizeExceededException` is ambiguous
  against `ResponseEntityExceptionHandler`'s own built-in resolution of that exception on this Spring
  version - Spring context refused to start with `IllegalStateException: Ambiguous @ExceptionHandler
  method mapped`
- **Fix:** Overrode the superclass's protected `handleMaxUploadSizeExceededException(...)` hook instead,
  matching the file's existing `handleMethodArgumentNotValid` override pattern
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java`
- **Verification:** `TemplateLogoControllerTest` context loads and all 7 tests pass; full suite green
- **Committed in:** `926858d` (Task 2 commit)

**3. [Rule 1 - Bug] Reworded a comment to avoid a duplicate `nosniff` grep match**
- **Found during:** Task 2, acceptance-criteria verification
- **Issue:** The plan's own suggested inline comment for the download endpoint contains the word
  "nosniff" in prose, which combined with the header literal made
  `grep -c "nosniff" TemplateController.java` return 2 instead of the plan's expected 1
- **Fix:** Reworded the comment to describe the header without repeating the literal word
- **Files modified:** `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java`
- **Verification:** `grep -c "nosniff" TemplateController.java` returns 1
- **Committed in:** `926858d` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 blocking-compilation, 2 bug/acceptance-criteria fixes)
**Impact on plan:** All three were required to make the plan's own acceptance criteria and a green
full suite achievable; none changed the plan's intended behaviour or scope.

## Issues Encountered

None beyond the deviations above. JDK 25 was available at `$HOME/.jdks/jdk-25`; Docker was running
for Testcontainers PostgreSQL 18.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `POST/DELETE/GET /api/templates/{id}/logo` and the `app.template.logo.*` limits (already published
  by `GET /api/templates/catalog` since plan 04-04) are ready for plan 04-07's frontend logo-upload UI.
- `LogoRejectedException`'s three reasons (`LOGO_INVALID_TYPE`/`LOGO_TOO_LARGE`/
  `LOGO_DIMENSIONS_EXCEEDED`) are a stable contract for the SPA to map to UI-SPEC's three distinct
  rejection sentences.
- Plan 04-06 owns the starter templates and seeder (`starter-*.md`, `TemplateSeeder`,
  `TemplateSeederIT`, `TemplateLifecycleIT`) - none of those files were touched here.
- No blockers identified.

## Threat Flags

None beyond the plan's own `<threat_model>` (T-04-24 through T-04-29), which this plan's
implementation satisfies directly: magic-byte sniffing for T-04-24, header-only dimension reads for
T-04-25, byte-size-first checking plus the multipart ceiling for T-04-26, `Content-Type` +
`X-Content-Type-Options: nosniff` + `Content-Disposition: inline` for T-04-27, and `safeFilename`'s
base-name-only truncation for T-04-28.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

All 5 created source/test files verified present on disk. Both task commits (`23068ca`,
`926858d`) verified present in `git log`. Full `mvn clean test` (JAVA_HOME=jdk-25) exits 0 with
417 tests passing (400 baseline + 17 new: 10 `LogoValidatorTest` + 7 `TemplateLogoControllerTest`),
including `ArchitectureBoundaryTest`.
