---
phase: 06-azure-devops-enrichment
plan: 04
subsystem: settings
tags: [azure-devops, credential, settings, banner]
requires: [06-01, 06-02]
provides:
  - "GET/PUT /api/settings/azure-devops with validate-before-save and computed OK/EXPIRING/EXPIRED state"
  - "Settings Azure DevOps section and app-wide expiry banner"
affects: [06-05]
tech-stack:
  added: []
  patterns: ["validate live before persisting a credential", "query-driven banner (no external store)"]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/AdoCredentialService.java
    - src/main/java/com/evision/releasemgmt/domain/ado/AdoCredentialTokenRequiredException.java
    - src/main/java/com/evision/releasemgmt/adapters/web/AdoCredentialController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/UpdateAdoCredentialRequest.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/AdoCredentialResponse.java
    - frontend/src/api/adoCredential.ts
    - frontend/src/components/AdoCredentialBanner.tsx
    - frontend/tests/components/AdoCredentialBanner.test.tsx
    - frontend/tests/components/SettingsRoute.test.tsx
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
    - frontend/src/api/auth.ts
    - frontend/src/components/AppShell.tsx
    - frontend/src/routes/SettingsRoute.tsx
decisions:
  - "ADO failures map to 422/503/502, never 401, so the frontend does not read them as a Jira session failure"
  - "Settings section is a local AzureDevOpsSection component inside SettingsRoute.tsx"
metrics:
  tasks: 2
  completed: 2026-10-12
---

# Phase 6 Plan 04: ADO credential settings Summary

Settings form to PUT /api/settings/azure-devops that validates the PAT live before saving (or updates only the expiry when the token is blank), returns a computed state and never the token, plus an amber app-wide banner within 14 days of expiry.

## Commits
- 8ce376e: backend service, controller, DTOs, exception mapping, ArchUnit rule, tests
- dd45eb6: frontend hook, banner, Settings section, reason messages, tests

## Verification
- Backend targeted tests (AdoCredentialServiceTest, AdoCredentialControllerTest, ArchitectureBoundaryTest): 18 green. Full backend suite not run.
- Frontend: `npx tsc -b` clean; `npx vitest run` 232 passed (224 baseline + 8 new).

## Deviations from Plan
- `AdoRepoRef` lives in `domain.enrichment` (not `domain.ado`); the request DTO imports it from there.
- Controller GET test needed a jwt post-processor (unlike the Jira analog, GET here is authenticated).
- `npm ci` was needed in the worktree (node_modules missing); no other process was installing.
- No Co-Authored-By trailer on commits, per the explicit instruction and the user's global rule.
- SettingsRoute.test.tsx did not exist; created new.

## Known Stubs
None.

## Threat Flags
None beyond the plan's threat model.

## Self-Check: PASSED
