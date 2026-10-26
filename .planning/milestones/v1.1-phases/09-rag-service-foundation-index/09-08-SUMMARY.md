---
phase: 09-rag-service-foundation-index
plan: 08
subsystem: rag-service
tags: [documents, indexing, worker, backoff]
requires: [09-03, 09-04, 09-05, 09-06, 09-07]
provides: [document push/get/delete API, IndexWorker]
affects: [09-09]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/application/DocumentService.java
    - rag-service/src/main/java/com/evision/ragservice/application/IndexWorker.java
    - rag-service/src/main/java/com/evision/ragservice/config/SchedulingConfig.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/DocumentController.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/RequestSizeLimitFilter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/PutDocumentRequest.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/dto/DocumentResponse.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/DocumentApiIT.java
    - rag-service/src/test/java/com/evision/ragservice/application/IndexWorkerIT.java
metrics:
  completed: 2026-10-13
---

# Phase 9 Plan 08: Document API and index worker Summary

PUT/GET/DELETE document endpoints that never touch Ollama on the request path, plus a non-transactional `@Scheduled` worker that embeds PENDING documents with worker-level exponential backoff (1 s to 5 min) on outages.

## Commits
- 474f674: document endpoints, size-limit filter, DocumentApiIT (8 tests)
- Task 2 commit: IndexWorker, SchedulingConfig, IndexWorkerIT (8 tests)

## Verification
Full `clean test` of rag-service: 118 tests, 0 failures.

## Deviations from Plan
None. Followed 09-07's controller pattern (no class-level `@Validated`). No shared files modified.

## Known Stubs
None.

## Self-Check: PASSED
