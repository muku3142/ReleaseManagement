---
phase: 15-products-clients-installations
plan: 09
subsystem: templates
tags: [mustache, placeholder-catalog, products, PORT-08]
requires: ["15-04"]
provides: ["products root section in the template view model", "frontend preview/drawer support for products"]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/template/view/ProductView.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/RenderContextMapper.java
    - src/main/java/com/evision/releasemgmt/application/RenderService.java
    - src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java
    - src/main/java/com/evision/releasemgmt/application/DocumentGenerationService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java
    - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java
    - frontend/src/components/TemplatePreview.tsx
    - frontend/src/components/PlaceholderDrawer.tsx
    - frontend/src/api/templates.ts
decisions:
  - "Root-level products section (not release.products); no joined-string field."
metrics:
  tasks: 2
  completed: 2026-10-21
---

# Phase 15 Plan 09: Products in templates Summary

Templates can now loop `{{#products}}{{name}}{{/products}}` over a release's products (already sorted by the repository), as Map-only values bounded by the existing collection cap, with the catalog, drift tests, browser preview and placeholder drawer all updated.

## Commits
- 252fae9: backend view model, mapper, cap, catalog, assembler signature and callers, tests
- 368d5ff: frontend scope union, preview context, "Product fields" drawer group, tests

## Verification
- Backend: ReleaseViewAssemblerTest, RenderServiceTest, PlaceholderCatalogTest, DocumentGenerationServiceTest, GenerateThenExportSeamTest, TemplateControllerTest, TemplateLogoControllerTest, TemplateLifecycleIT, TemplateSeederIT all pass (Docker was up).
- Frontend: `tsc -b` clean; TemplatePreview, PlaceholderDrawer, ChromePreview, TemplateDetailRoute tests pass (60).
- Full `vitest run` and the full backend suite were not run.

## Deviations from Plan
None in behaviour. Process notes: TemplateDetailRoute.test.tsx needed no edit; the plan's `npm run typecheck` was run as `npx tsc -b`.

## Threat model
T-15-31/32/33 mitigated as planned: Map with a single `name` String, no scanner/engine change, inert-name test, `checkBound(view.products().size())`.

## Known Stubs
None.

## Self-Check: PASSED
