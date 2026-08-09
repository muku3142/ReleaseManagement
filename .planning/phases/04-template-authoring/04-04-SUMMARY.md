---
phase: 04-template-authoring
plan: 04
subsystem: api
tags: [spring-mvc, rest, problemdetail, jmustache-consumer, template-crud]

# Dependency graph
requires:
  - phase: 04-template-authoring
    plan: 01
    provides: "ReleaseView/ItemView/GroupView view-model, PlaceholderCatalog.ENTRIES, SampleReleaseView, TemplateMalformedException/RenderBoundExceededException"
  - phase: 04-template-authoring
    plan: 02
    provides: "Template/ChromeFields/LogoMetadata aggregate, TemplateRepository port, TemplateNotFoundException/TemplateDuplicateException"
  - phase: 04-template-authoring
    plan: 03
    provides: "RenderService (bounded render entry point), RenderResult, ReleaseViewAssembler"
provides:
  - "TemplateService: template CRUD plus TMPL-04 save-time validation against SampleReleaseView"
  - "/api/templates REST surface: list/create/get/rename/save-content/activate/duplicate/delete"
  - "/api/templates/catalog: placeholder vocabulary plus backend-declared logo limits"
  - "/api/templates/render-context/{releaseId}: a real release's ReleaseView for browser preview"
  - "Four new ProblemDetail reason codes extending the Phase 01 taxonomy"
affects: [04-05, 04-06, 04-07, phase-05-document-generation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Domain view-model record (ReleaseView) returned directly from a controller method instead of a duplicate wire DTO, when the domain shape is already flat and non-sensitive"
    - "Mockito.spy() around a real collaborator (RenderService) to assert interaction counts without mocking the behaviour under test, when the plan requires 'real renderer, not mocked'"

key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/TemplateService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateTemplateRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/RenameTemplateRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ChromeFieldsRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/SaveTemplateContentRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ChromeFieldsResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/LogoMetadataResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TemplateResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TemplateSummaryResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/TemplateListResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PlaceholderEntryResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/LogoLimitsResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/PlaceholderCatalogResponse.java
    - src/test/java/com/evision/releasemgmt/application/TemplateServiceTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/TemplateControllerTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/main/resources/application.yml
    - src/test/resources/application.yml

key-decisions:
  - "No ReleaseViewResponse DTO was created - GET /api/templates/render-context/{releaseId} returns the domain ReleaseView record directly, since it is already a flat, non-sensitive projection and a hand-written mirror record would just be a second thing to keep in sync with PlaceholderCatalog"
  - "TemplateServiceTest wraps a real RenderService (backed by a real MustacheFlexmarkRenderAdapter) in Mockito.spy() so the 'blank chrome fields make no validation call' behaviour can be asserted by interaction count without mocking the renderer the tests are meant to exercise"
  - "activate() intentionally does not update updatedBy/updatedAt - activation is not an edit of a template's content; Principal is still required at the controller so every mutating endpoint has a uniform audit shape"

patterns-established:
  - "Pattern: when a domain view-model record is already the exact wire shape a read endpoint needs, return it directly from the controller rather than hand-rolling a mirror DTO"

requirements-completed: [TMPL-01, TMPL-03, TMPL-04, TMPL-05, TMPL-06, TMPL-08]

# Metrics
duration: 15min
completed: 2026-08-09
---

# Phase 04 Plan 04: Template REST Surface Summary

**TemplateService (CRUD plus TMPL-04 save-time validation) and TemplateController expose the full `/api/templates` lifecycle, the placeholder catalog with backend-declared logo limits, and a real release's view-model for browser preview, with four new `ProblemDetail` reason codes extending the Phase 01 error taxonomy.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-08-09T14:37:00+03:00
- **Completed:** 2026-08-09T14:52:31+03:00
- **Tasks:** 3
- **Files modified:** 19 (16 created, 3 modified)

## Accomplishments

- `TemplateService` runs TMPL-04's save-time validation render against `SampleReleaseView.instance()`
  for the body and each of the four chrome text fields, warning on unknown placeholders without
  blocking the save (D-04-07) while letting `TemplateMalformedException`/`RenderBoundExceededException`
  propagate untouched to block it. `duplicate()` finds a free `"name copy [n]"` within the source's
  document type (capped at 50 attempts, then lets `TemplateDuplicateException` surface); `delete()`
  has no lifecycle guard, matching D-04-13.
- `TemplateController` covers every action UI-SPEC states 0 and 1 need: list/create/get/rename/
  save-content/activate/duplicate/delete, all under `/api/templates`, with every mutating endpoint
  taking `Principal` and recording the actor exactly like `ReleaseController.freeze`.
- `/api/templates/catalog` publishes `PlaceholderCatalog.ENTRIES` plus a backend-declared
  `logoLimits` object (`app.template.logo.max-bytes/max-width/max-height`, added to both
  `application.yml` files with values `2097152`/`2000`/`2000`), resolving UI-SPEC planning input #2
  so the frontend interpolates real limits into its rejection copy instead of hardcoding them.
- `/api/templates/render-context/{releaseId}` returns `ReleaseViewAssembler.assemble(release)`
  directly - the same view-model the render engine consumes - so the browser preview and the
  eventual server render can never disagree about the underlying data (D-04-09/D-04-10).
- `GlobalExceptionHandler` gained `TEMPLATE_NOT_FOUND` (404), `TEMPLATE_DUPLICATE` (409),
  `TEMPLATE_MALFORMED` (422, with a dynamic detail naming the offending field) and
  `RENDER_BOUND_EXCEEDED` (422, fixed UI-SPEC copy), all built through the existing `problem(...)`
  helper - no parallel error scheme was introduced.
- Deleting the only active template of a document type is exercised directly:
  `TemplateServiceTest.deleteOfTheActiveTemplateLeavesNoActiveTemplateForItsType` calls
  `service.delete()` with no guard and confirms `findByDocumentType` can legitimately return an
  empty/no-active list afterwards (UI-SPEC planning input #3) - nothing in the service or controller
  path special-cases "zero active templates" as an error.

## Open Items Resolved (per plan's `<open_items_this_plan_resolves>`)

1. **New reason codes** - `TEMPLATE_NOT_FOUND`, `TEMPLATE_DUPLICATE`, `TEMPLATE_MALFORMED`,
   `RENDER_BOUND_EXCEEDED` added to `GlobalExceptionHandler`, each with an actionable `detail`.
2. **Logo limits backend-declared and fetchable** - `GET /api/templates/catalog` returns
   `logoLimits.maxBytes/maxWidth/maxHeight/contentTypes`, sourced from
   `app.template.logo.*` properties (`@Value`-injected into the controller), not hardcoded on
   either side.
3. **Zero active templates is legal** - confirmed by `TemplateServiceTest` and by
   `TemplateRepository.delete`'s own contract ("no guard... leaves the type with none"); no path
   in `TemplateService`/`TemplateController` treats an absent active template as an error.
4. **Manual-item grouping inside `{{#groups}}`** - confirmed unchanged from plan 04-03:
   `ReleaseViewAssembler.groups()` groups manual items by their category label
   (`categoryLabel(ManualItemCategory)`, matching `frontend/src/api/releases.ts`'s
   `MANUAL_ITEM_CATEGORY_LABELS` exactly), and `PlaceholderCatalog`'s `groupName` description
   already reads "an issue type for ticket-backed items or a category for manual items" - the code
   and the catalog description agree, so this plan made no change here.

## Task Commits

Each task was committed atomically:

1. **Task 1: Write TemplateService with save-time validation** - `644b0a6` (feat)
2. **Task 2: Write the DTOs, the TemplateController and the catalog and render-context endpoints** - `eedab3c` (feat)
3. **Task 3: Extend GlobalExceptionHandler and write the controller slice test** - `067ab93` (feat)

**Plan metadata:** committed separately by orchestrator after wave merge (per parallel-execution contract, this executor does not touch STATE.md/ROADMAP.md).

## Files Created/Modified

- `src/main/java/com/evision/releasemgmt/application/TemplateService.java` - CRUD plus save-time validation
- `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java` - the `/api/templates` REST surface
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateTemplateRequest.java`, `RenameTemplateRequest.java`, `ChromeFieldsRequest.java`, `SaveTemplateContentRequest.java` - request DTOs with Bean Validation matching the V4 column widths
- `src/main/java/com/evision/releasemgmt/adapters/web/dto/ChromeFieldsResponse.java`, `LogoMetadataResponse.java`, `TemplateResponse.java`, `TemplateSummaryResponse.java`, `TemplateListResponse.java`, `PlaceholderEntryResponse.java`, `LogoLimitsResponse.java`, `PlaceholderCatalogResponse.java` - response DTOs
- `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` - four new reason codes extending the Phase 01 taxonomy
- `src/main/resources/application.yml`, `src/test/resources/application.yml` - `app.template.logo.max-bytes/max-width/max-height`, identical in both files
- `src/test/java/com/evision/releasemgmt/application/TemplateServiceTest.java` - 9 tests, one per behaviour bullet
- `src/test/java/com/evision/releasemgmt/adapters/web/TemplateControllerTest.java` - 9 `@WebMvcTest` slice-test assertions pinning status/reason pairs and the catalog/render-context read paths

## Decisions Made

- **No `ReleaseViewResponse` DTO.** The plan offered a choice; returning the domain `ReleaseView`
  directly from `GET /api/templates/render-context/{releaseId}` was the preferred option because a
  hand-written mirror record would be a second shape to keep in sync with `PlaceholderCatalog`. Not
  created, and not listed in `files_modified` above.
- **`TemplateServiceTest` uses `Mockito.spy()` on a real `RenderService`** (backed by a real
  `MustacheFlexmarkRenderAdapter`), not a mock, so the "blank chrome fields make no validation call"
  behaviour bullet can be asserted by interaction count (`verify(renderService, never())...`) while
  every other assertion in the same test class exercises the actual render/scan pipeline end to end.
- **`activate()` does not touch `updatedBy`/`updatedAt`.** Per the plan's own note, activation is not
  an edit of the template's content; `Principal` is still required at the controller so every
  mutating endpoint has the same audit shape even though this one particular call doesn't use the
  actor for a field write.

## Deviations from Plan

None - plan executed exactly as written, including the `<action>`-block guidance for the
`ReleaseViewResponse` decision point.

## Issues Encountered

None. JDK 25 was available at `$HOME/.jdks/jdk-25`; the full `mvn clean test` suite (400 tests, up
from the 382-test baseline: +9 `TemplateServiceTest`, +9 `TemplateControllerTest`) passed with zero
failures/errors, including `ArchitectureBoundaryTest`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `/api/templates` and `/api/templates/catalog` are ready for plan 04-05 (logo upload/enforcement,
  which reuses the same `app.template.logo.*` properties this plan published) and plan 04-06/04-07
  (frontend authoring screen, which consumes the catalog, render-context and CRUD endpoints built
  here).
- `TemplateMalformedException`'s field-naming convention (`"Body"` unprefixed, every chrome field
  prefixed with `"<Field>: "`) is now a stable contract for the SPA's "Show details" disclosure.
- No blockers identified.

## Threat Flags

None beyond the plan's own `<threat_model>` (T-04-18 through T-04-23), which this plan's
implementation satisfies directly: `@Size` bounds on every request DTO, `Principal` required on every
mutating endpoint, the acting user recorded via `principal.getName()`, `TemplateMalformedException`'s
detail sourced only from the scanner's own fixed sentences, and `/render-context/{releaseId}`
returning exactly `ReleaseViewAssembler`'s output with no ids/actor names/credentials.

---
*Phase: 04-template-authoring*
*Completed: 2026-08-09*

## Self-Check: PASSED

All 16 created files (14 main source + 2 test files) verified present and tracked via
`git ls-files`. All 3 task commits (`644b0a6`, `eedab3c`, `067ab93`) verified present in
`git log --all`. Full `mvn clean test` (JAVA_HOME=jdk-25) exits 0 with 400 tests passing
(382 baseline + 18 new), including `ArchitectureBoundaryTest`.
