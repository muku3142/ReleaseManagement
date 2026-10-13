---
phase: 09-rag-service-foundation-index
plan: 05
subsystem: rag-service
tags: [jdbcclient, postgres, upsert, testcontainers]
requires: [09-02]
provides:
  - ClientStore, CollectionStore, DocumentStore JdbcClient adapters
affects: [09-06]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/ClientRepositoryAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/CollectionRepositoryAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/DocumentRepositoryAdapter.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/ClientPersistenceIT.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/CollectionDocumentPersistenceIT.java
key-decisions:
  - "Re-index and content change keep old chunks until replacements land"
requirements-completed: [RAG-01, RAG-02, RAG-04]
completed: 2026-10-13
---

# Phase 9 Plan 05: Client, collection and document persistence Summary

Three package-private JdbcClient adapters: hash-only client keys with revoke/rotate and once-a-minute last_used_at, client-scoped collection lookups and status counts, and a single-statement document upsert (unchanged content keeps status, changed or FAILED goes back to PENDING with lease cleared).

## Tasks
1. Client and collection adapters + ClientPersistenceIT (11 tests): 96766af
2. Document adapter + CollectionDocumentPersistenceIT (8 tests): 8e10305

## Verification
`mvn -f rag-service/pom.xml clean test -Dtest=CollectionDocumentPersistenceIT,ClientPersistenceIT,ArchitectureBoundaryTest`: 24 tests, 0 failures.

## Deviations from Plan
None. SQL is written as text blocks with named parameters only (no concatenation), so the status query is duplicated between listStatus and findStatus rather than assembled from fragments.

## Known Stubs
None.

## Self-Check: PASSED
