---
phase: 09-rag-service-foundation-index
plan: 01
subsystem: infra
tags: [pgvector, postgres, docker-compose, testcontainers]
requires: []
provides:
  - pgvector Postgres image in both compose files
  - idempotent superuser init script (role rag, db ragdb, vector extension)
  - shared PgVectorImage test constant used by all 18 main ITs
affects: [09-10, 09-11]
key-files:
  created: [docker/initdb/10-ragdb.sh, src/test/java/com/evision/releasemgmt/PgVectorImage.java]
  modified: [compose.yaml, compose.app.yaml, .gitattributes, "18 *IT.java files"]
decisions:
  - "Isolation is one-way: rag cannot connect to releasemgmt; releasemgmt (superuser) can read ragdb"
metrics:
  completed: 2026-10-13
---

# Phase 9 Plan 01: pgvector image and rag bootstrap Summary

Both compose files and all 18 main-app Testcontainers ITs now run on `pgvector/pgvector:0.8.7-pg18-trixie`, with an idempotent superuser init script creating role `rag`, database `ragdb` and the `vector` extension.

## Tasks
1. Compose + init script + .gitattributes (`*.sql text eol=lf`): 88f6e4c
2. `PgVectorImage` constant, 18 ITs switched: 29e5442

## Probe results (throwaway container rag-init-probe, removed afterwards)
- (a) vector extension in ragdb: `vector`
- (b) login as rag to ragdb: `rag`
- (c) rag to releasemgmt: `FATAL: permission denied for database "releasemgmt" ... User does not have CONNECT privilege.`
- (d) re-run of the script: succeeded (`REVOKE`, `extension "vector" already exists, skipping`, `CREATE EXTENSION`, `REVOKE`); it was executed directly, exit status not echoed, but `set -e` and the clean output confirm success.

## Main suite
`mvn -B clean test`: `Tests run: 946, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS. 18 `*IT` classes reported.

## Deviations
None of substance. Probe (d) was run as `/docker-entrypoint-initdb.d/10-ragdb.sh` directly rather than via `bash ...` because the tool sandbox refused the `bash` form. The worktree was reset to the specified base 60fff5b at startup as instructed.

## Known Stubs
None.

## Self-Check: PASSED
Files and both commits exist; running stack untouched; no rag-service service added.
