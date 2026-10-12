---
phase: 06-azure-devops-enrichment
plan: 02
subsystem: ado-credential-and-client
tags: [azure-devops, credential, restclient, fixtures, flyway]
requires: []
provides:
  - ado_credential singleton table (V8) with non-decrypting write/status paths
  - AdoCredentialRepository and AdoRepositoryPort (validate) ports
  - adoRestClient bean (redirects off) and AdoRestAdapter.validate
  - all Phase 6 app.* properties in both application.yml files
  - six redacted fixtures with synthetic identities
affects: [06-03, 06-04, 06-05, 06-08]
tech-stack:
  added: []
  patterns: [JPQL-only credential writes, onStatus before body decode, singleton row CHECK id = 1]
key-files:
  created:
    - src/main/resources/db/migration/V8__ado_credential.sql
    - src/main/java/com/evision/releasemgmt/domain/ado/ (AdoCredential, AdoCredentialStatus, AdoCredentialState, AdoFailureReason, AdoAccessException)
    - src/main/java/com/evision/releasemgmt/ports/AdoCredentialRepository.java
    - src/main/java/com/evision/releasemgmt/ports/AdoRepositoryPort.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/AdoCredentialRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/AdoCredentialEntity.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/AdoCredentialJpaRepository.java
    - src/main/java/com/evision/releasemgmt/config/AdoRestClientConfig.java
    - src/main/java/com/evision/releasemgmt/adapters/ado/AdoRestAdapter.java
    - src/test/resources/fixtures/ (jira-search-jql, jira-changelog-page1, jira-changelog-small-page, jira-statuses, ado-pr-commits-top3, ado-pr-commits-all)
  modified:
    - src/main/resources/application.yml
    - src/test/resources/application.yml
key-decisions:
  - "Expiry is user-entered; state (OK/EXPIRING/EXPIRED, 14-day window) is computed on read with no scheduler"
  - "3xx, 203, 401 and 403 all map to UNAUTHORIZED before any body is decoded; redirects are never followed"
requirements-completed: [ADO-04]
duration: ~25 min
completed: 2026-10-12
---

# Phase 6 Plan 02: ADO credential storage, client and fixtures Summary

One org-scoped Azure DevOps PAT is stored encrypted in a singleton row whose writes and status reads never decrypt, a dedicated ADO RestClient validates it in one Code-Read call, and the real-tenant captures are available as redacted fixtures.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. ado_credential storage | 8f4518d | V8, domain records, ports, entity, JPQL repository, adapter; IT incl. key-rotation test |
| 2. ADO client + validate + config | 70c1c6f | adoRestClient, AdoRestAdapter, 9 WireMock tests, all app.ado/enrichment/jira.changelog config |
| 3. Redacted fixtures | b8001b9 | six fixtures, scripted leak check passed |

Full suite: 658 tests, 0 failures (`mvn -P '!frontend' clean test`).

## Deviations from Plan

**1. [Rule 3 - Blocking] Domain/port files for Task 2 committed with Task 1**
`AdoFailureReason`, `AdoAccessException` and `AdoRepositoryPort` were created alongside Task 1's files and landed in the first commit. No behavioural impact.

**2. [Rule 1 - Test adjustment] Key-rotation assertion uses RuntimeException**
The plan says `IllegalStateException`; Hibernate may wrap the converter's exception, and the Jira analog asserts `RuntimeException` for the same reason, so the IT does too.

**3. [Rule 2 - Data safety] Redaction also nulls `tmpFromAccountId`/`tmpToAccountId`**
Raw changelog items carry these account-id fields, which the plan's rules did not name; they are set to null.

Otherwise the plan executed as written.

## Known Stubs

None. `AdoRepositoryPort` only has `validate`; 06-08 adds the pull-request-commits method as planned.

## Threat Flags

None beyond the plan's threat model.

## Notes for later plans

- The raw captures live only in the main checkout (git-excluded); the redaction script was kept in the session scratchpad, not the repo.
- `AdoCredentialRepositoryAdapter.findForUse` is the only decrypting path.

## Self-Check: PASSED
All created files exist; commits 8f4518d, 70c1c6f, b8001b9 present.
