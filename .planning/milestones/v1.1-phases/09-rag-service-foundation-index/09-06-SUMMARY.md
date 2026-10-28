---
phase: 09-rag-service-foundation-index
plan: 06
subsystem: rag-service persistence
tags: [pgvector, jdbcclient, skip-locked, cosine-search]
requires: [09-02]
provides: [IndexQueue adapter, ChunkSearch adapter]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/VectorLiteral.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/IndexQueueRepositoryAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/ChunkSearchRepositoryAdapter.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/IndexQueuePersistenceIT.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/ChunkSearchPersistenceIT.java
requirements: [RAG-02, RAG-03, RAG-05]
completed: 2026-10-13
---

# Phase 9 Plan 06: Index queue and chunk search Summary

Native-SQL adapters: lease-based `FOR UPDATE SKIP LOCKED` claim with hash-guarded complete/fail, and exact cosine search with bound jsonb `@>` filters.

## Deviations from Plan

None in implementation. One test adjusted: the concurrency test claims batches of 5 per thread (not 10) so both threads are guaranteed a share; it asserts disjointness and a union of all 10.

## Verification

`IndexQueuePersistenceIT` (10 tests), `ChunkSearchPersistenceIT` (9 tests) and `ArchitectureBoundaryTest` pass.

## Commits

- 1d91316 queue adapter and VectorLiteral
- 6940e0a search adapter

## Self-Check: PASSED
