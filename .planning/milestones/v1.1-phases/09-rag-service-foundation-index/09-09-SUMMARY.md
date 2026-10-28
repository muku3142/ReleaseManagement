---
phase: 09-rag-service-foundation-index
plan: 09
subsystem: rag-service
tags: [search, status, pgvector, ollama]
requires: [09-03, 09-04, 09-05, 09-06, 09-07]
provides:
  - POST /v1/collections/{c}/search (chunk-level ranked hits, metadata filters, k default 10 / max 50)
  - GET /v1/status (Ollama UP/DOWN plus caller's pending/failed queue depth)
affects: [rag-service]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/application/SearchService.java
    - rag-service/src/main/java/com/evision/ragservice/application/StatusService.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/SearchController.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/StatusController.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/SearchRequest.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/SearchResponse.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/StatusResponse.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/SearchApiIT.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/StatusApiIT.java
decisions:
  - Collection is resolved under the calling client before any Ollama call (404 with zero embed requests)
  - Controllers follow the 09-07 pattern (no class-level @Validated)
metrics:
  completed: 2026-10-13
  tasks: 2
---

# Phase 9 Plan 09: Search and Status Summary

Filtered chunk-level semantic search (query embedded with the QUERY purpose, equality/in-list filters ANDed, k capped at 50) plus a per-client status endpoint, with readiness proven independent of Ollama.

## Commits
- cf88d1d feat(09-09): add filtered chunk-level search endpoint
- 9c041af feat(09-09): add per-client status endpoint

## Verification
SearchApiIT (9) and StatusApiIT (4) pass; full rag-service suite `clean test` is 115/115 green.
Tests seed documents via DocumentStore.upsert plus IndexQueue.claim/complete (no dependency on plan 09-08).

## Deviations from Plan
None - plan executed as written. No shared files modified. Commits omit the Co-Authored-By trailer per the user's global CLAUDE.md rule.

## Self-Check: PASSED
