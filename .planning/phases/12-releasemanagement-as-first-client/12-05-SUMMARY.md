---
phase: 12-releasemanagement-as-first-client
plan: 05
subsystem: adapters
tags: [assistant, rag, restclient, wiremock]
requires: [12-01, 12-04]
provides:
  - RagIndexAdapter implementing RagIndexPort over rag-service /v1 (conditional on app.assistant.enabled)
  - RagRestClientConfig.ragClient factory (reusable by the chat client in 12-10) and ragIndexRestClient bean
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/config/RagRestClientConfig.java
    - src/main/java/com/evision/releasemgmt/adapters/rag/RagIndexAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/rag/RagIndexAdapterTest.java
metrics:
  tasks: 1
  completed: 2026-10-15
---

# Phase 12 Plan 05: rag-service index client Summary

Conditional bearer-authenticated `RestClient` (2 s connect / 5 s read, no redirects, HTTP/1.1) and `RagIndexAdapter`
(ensureCollection, put, delete with 404 as success); all failures become a metadata-only `RagServiceException`
(status 0 for timeouts and refused connections).

## Commits
- d5fefd1: feat(12-05) config, adapter, WireMock test

## Verification
- RagIndexAdapterTest: 8 tests, 0 failures (`clean test` of that class only, per memory constraints).
- Full suite not run (orchestrator runs it after merge).

## Deviations from Plan
- [Rule 1 - Bug] Forced `HttpClient.Version.HTTP_1_1`: the JDK client's default h2c upgrade on body-carrying PUTs failed
  against WireMock (status 0). Plain-http rag-service is HTTP/1.1 as well.
- Factory method is `public static ragClient(...)` (plan said private static) so the test, in another package, and 12-10 can reuse it.
- Test asserts the encoded path (`ticket%3AR%3APAY-1`) with `urlEqualTo`; URI template variables encode ':' as expected.
- The request omits the optional `url` field because `IndexDocument` carries none.

## Known Stubs
None.

## Threat Flags
None.

## Self-Check: PASSED
