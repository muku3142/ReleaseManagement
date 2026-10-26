---
phase: 15-products-clients-installations
plan: 08
subsystem: release-api
tags: [releases, products, portfolio]
requires: [15-04]
provides: [release product assignment API, products on release responses]
affects: [ReleaseService, ReleaseController]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateReleaseRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseSummaryResponse.java
  created:
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseProductResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseProductsRequest.java
requirements-completed: [PORT-03]
duration: ~25 min
completed: 2026-10-21
---

# Phase 15 Plan 08: Release product assignment API Summary

Release create now requires at least one active product (D-15-18), PUT /api/releases/{id}/products replaces the set at any status, and release and summary responses carry `products`.

## Tasks

Both tasks were committed together in 217a3ba. The controller calls the new `createWithTickets` signature, so a service-only commit would not have compiled.

1. **ReleaseService**: added `productsOf`, `productsOfReleases` and `setProducts` (no `requireOpen`). `createWithTickets` takes `productIds` and validates before Jira is called: empty gives `RELEASE_PRODUCT_REQUIRED`, unknown gives `ProductNotFoundException`, archived gives `PRODUCT_ARCHIVED`. After the insert it assigns the products and deletes the new release if that fails.
2. **API**: new `ReleaseProductResponse` and `ReleaseProductsRequest`. `CreateReleaseRequest.productIds` is optional at the DTO level, and null becomes an empty list so the service returns the dedicated 422. The list endpoint makes one batched `productsOfReleases` call. Added `@PutMapping("/{releaseId}/products")`.

## Verification

All of these passed, including the Testcontainers ITs (Docker was up):
- ReleaseServiceTicketsTest: 34 tests
- ReleaseServiceManualItemsTest: 10
- ReleaseControllerTest: 62
- DocumentReadinessServiceTest: 14
- ReleasePersistenceIT: 30
- ActorDisplaySeamIT: 5

New tests cover the service rejections, the compensating delete, `setProducts` on a FROZEN release, the product fields on GET, the empty `products` fallback on list, and the PUT 200/400/409 paths.

## Deviations from Plan

- The `@BeforeEach` stub of `products.findById(PRODUCT_ID)` in ReleaseServiceTicketsTest uses `lenient()`. Strict Mockito stubs would otherwise fail every test that does not create a release.
- No other deviations. `DocumentReadinessService` was untouched.

## Known Stubs

None.

## Self-Check: PASSED

Commit 217a3ba exists and the new DTO files are present.
