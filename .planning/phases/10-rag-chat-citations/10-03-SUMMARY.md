---
phase: 10-rag-chat-citations
plan: 03
subsystem: rag-service
tags: [retrieval, multi-collection, tenant-isolation]
requires: [10-01]
provides:
  - ChunkSearch.search(List<UUID> collectionIds, ...) single-query multi-collection retrieval
  - SearchHit.collectionId (first component)
  - CollectionService.requireAll with 404 parity
affects: [10-05]
key-files:
  modified:
    - rag-service/src/main/java/com/evision/ragservice/ports/ChunkSearch.java
    - rag-service/src/main/java/com/evision/ragservice/domain/SearchHit.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/persistence/ChunkSearchRepositoryAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/application/SearchService.java
    - rag-service/src/main/java/com/evision/ragservice/application/CollectionService.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/ChunkSearchPersistenceIT.java
    - rag-service/src/test/java/com/evision/ragservice/application/CollectionServiceTest.java
metrics:
  tasks: 2
  tests: 162 passing (baseline 158)
---

# Phase 10 Plan 03: Multi-collection retrieval Summary

Retrieval now takes a list of caller-owned collection ids (`c.collection_id IN (:collectionIds)`, one SQL query, cosine-ordered across all), each SearchHit carries its source collection id, and `CollectionService.requireAll` resolves names with the same CollectionNotFoundException for foreign and unknown names.

## Commits

- 73a9e2c: multi-collection ChunkSearch, SearchHit.collectionId, adapter, SearchService call site, IT cases (cross-collection ordering, tenant canary, filter across collections)
- requireAll and two CollectionServiceTest cases (second commit on branch)

## Deviations from Plan

None. `/search` JSON and openapi.json unchanged (OpenApiContractIT and SearchApiIT green).

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED

Full rag-service suite with clean: 162 tests, 0 failures, BUILD SUCCESS.
