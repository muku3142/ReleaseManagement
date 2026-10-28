---
phase: 15-products-clients-installations
plan: 01
subsystem: persistence
tags: [flyway, postgres, append-only-ledger, backfill]
requires: []
provides:
  - "V14 portfolio schema: product, client, product_client, release_product, client_deployment"
  - "effective_client_deployment and current_client_release views (D-15-05 rule)"
  - "D-15-17 product backfill for existing releases"
affects: [phase-15 later plans, phase-16 matrix]
tech-stack:
  added: []
  patterns: [composite-FK invariants, trigger-enforced append-only ledger, pure-SQL backfill]
key-files:
  created:
    - src/main/resources/db/migration/V14__portfolio.sql
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ClientDeploymentSchemaIT.java
    - src/test/java/com/evision/releasemgmt/adapters/persistence/V14PortfolioMigrationIT.java
    - src/test/resources/db/v1_1-snapshot.sql
  modified: []
key-decisions:
  - "Backfill is INSERT-only SQL inside V14; no existing table is altered or updated"
  - "COLLATE C in the DISTINCT ON order makes the chosen spelling of case-variant keys deterministic (uppercase wins)"
metrics:
  completed: 2026-10-21
  tasks: 2
  files: 4
---

# Phase 15 Plan 01: Portfolio schema (V14) Summary

V14 delivers the whole portfolio storage contract in one migration: products/clients with archive columns, an append-only `client_deployment` ledger whose invariants live in composite FKs and a trigger, two views implementing the "current release per client and product" rule, and a pure-SQL backfill giving every existing release exactly one product.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. V14 + ClientDeploymentSchemaIT | 96ea469 | 11 invariant tests pass; PromotionSchemaIT still passes (16) |
| 2. Snapshot + V14PortfolioMigrationIT | ed1645b | Migration over synthetic v1.1 data passes |

Verification run: ClientDeploymentSchemaIT (11), V14PortfolioMigrationIT (1), PromotionSchemaIT (16), PromotionPersistenceIT (7), all green.

## Deviations from Plan

None - plan executed as written. The worktree base had to be reset to a39f058 first (it started on an older commit), as the plan required.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED
- V14__portfolio.sql, both ITs and v1_1-snapshot.sql exist; commits 96ea469 and ed1645b exist.
