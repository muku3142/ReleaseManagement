---
phase: 09-rag-service-foundation-index
plan: 04
subsystem: rag-service
tags: [ollama, restclient, wiremock, actuator]
requires: [09-02]
provides:
  - OllamaEmbeddingAdapter (EmbeddingPort over /api/embed)
  - OllamaRestClientConfig (JDK HttpClient, HTTP/1.1, connect+read timeouts)
  - OllamaHealthIndicator (not in readiness group)
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/config/OllamaRestClientConfig.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/ollama/OllamaHealthIndicator.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/ollama/OllamaEmbeddingAdapterTest.java
decisions:
  - "Ollama clients are forced to HTTP/1.1 (see deviation)"
requirements-completed: [RAG-02, RAG-03]
completed: 2026-10-13
---

# Phase 9 Plan 04: Ollama embedding adapter Summary

EmbeddingPort implemented against Ollama `/api/embed` (batched, ordered, `truncate:false`, no options/keep_alive, adapter-owned nomic prefixes) with 400 mapped to a content error and everything else (404, 5xx, timeout, refused, wrong count/dimension) mapped to an outage; plus a health indicator that never gates readiness.

## Tasks
1. Config, adapter, 15-test WireMock suite: 1ecb7c8
2. OllamaHealthIndicator (`org.springframework.boot.health.contributor`): 8a1daef

## Verification
`mvn -f rag-service/pom.xml clean test -Dtest='OllamaEmbeddingAdapterTest,SchemaPersistenceIT,ArchitectureBoundaryTest'`: 23 tests, 0 failures. Readiness include in application.yml untouched. No springframework.ai/langchain4j references.

## Deviations from Plan
**1. [Rule 3 - Blocking] Forced HTTP/1.1 on the JDK HttpClient.** The default client attempts an h2c upgrade on plain-http POSTs, which WireMock aborts ("connection aborted"). Ollama is HTTP/1.1, so pinning is correct for production too. Applied to the config bean, the health client and the test client.

## Known Stubs
None.

## Self-Check: PASSED
