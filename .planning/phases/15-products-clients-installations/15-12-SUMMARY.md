---
phase: 15-products-clients-installations
plan: 12
subsystem: web-api
tags: [rest, installs, append-only, webmvc]
requires: [15-02, 15-07]
provides: [ClientDeploymentService, ClientDeploymentController, InstallHistoryResponse]
affects: [15-13]
requirements-completed: [PORT-04, PORT-05, PORT-07]
completed: 2026-10-22
---

# Phase 15 Plan 12: Install REST endpoints Summary

`/api/releases/{id}/installs` (GET history, POST bulk record, POST `/{installId}/corrections`) over the plan 07 ledger adapter, copying the PromotionService/PromotionController shape; actor comes only from `Principal.getName()`.

## Tasks
1. `ClientDeploymentService` (history, reachedProduction via `firstProductionSuccess().isPresent()`, record, correct) plus unit test - 0cbbba9
2. `ClientDeploymentController`, `RecordInstallRequest` (clientIds `@NotEmpty @Size(max=200)`, no actor field), `CorrectInstallRequest`, `InstallHistoryResponse`, WebMvc test - see the second feat(15-12) commit

## Verification
`ClientDeploymentServiceTest`, `ClientDeploymentControllerTest`, `PromotionControllerTest` all pass. The WebMvc test covers: contract JSON and newest-first order, body `recordedBy` ignored, empty/non-UUID/null/over-200 clientIds -> 400, INSTALL_CLIENTS_REJECTED 409 with `clients[]`, INSTALL_RELEASE_NOT_FROZEN 409, INSTALL_INSTALLED_AT_INVALID 422, INSTALL_NOT_FOUND 404, auth and CSRF. No Testcontainers ITs were needed (HTTP IT is plan 13).

## Deviations from Plan
None. GlobalExceptionHandler already mapped all install reasons (earlier wave), so it was not touched.

## Known Stubs
None.

## Self-Check: PASSED
