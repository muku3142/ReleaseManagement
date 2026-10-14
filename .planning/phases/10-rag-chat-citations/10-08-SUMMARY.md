---
phase: 10-rag-chat-citations
plan: 08
subsystem: rag-service golden tier
tags: [golden, live-harness, surefire-profile, sse-smoke]
requires: [10-06]
provides:
  - golden fixture v1 (G01-G10, T01-T03) with synthetic seed corpus
  - GoldenChatIT opt-in live harness writing target/golden-report.json
  - golden Maven profile (default excludes the tag)
  - chat-smoke.sh curl SSE smoke test
key-files:
  created:
    - rag-service/src/test/resources/golden/clients.json
    - rag-service/src/test/resources/golden/chat-v1.json
    - rag-service/src/test/resources/golden/transform-v1.json
    - rag-service/src/test/resources/golden/seed/*.md (7 files)
    - rag-service/src/test/java/com/evision/ragservice/golden/GoldenChatIT.java
    - rag-service/scripts/chat-smoke.sh
    - rag-service/scripts/.gitattributes
  modified:
    - rag-service/pom.xml
requirements: [RAG-06, RAG-07, RAG-09, RAG-11, SAFE-02]
metrics:
  tasks: 3
  completed: 2026-10-14
---

# Phase 10 Plan 08: Golden fixture, live harness and smoke script Summary

Opt-in golden tier: fixture (10 chat + 3 transform cases, synthetic seed corpus), `GoldenChatIT` that seeds through the public API and runs every case 3 times over real HTTP/SSE, a `golden` Maven profile, and a curl SSE smoke script.

## Commits

- 0616319: golden fixture and seed corpus
- 1cfef05: golden profile and GoldenChatIT
- 2757693: chat-smoke.sh (+ scripts/.gitattributes)

## Switch proof (RESEARCH A4)

The surefire property mechanism works as specified; no surefire `<configuration>` override was needed.
- `clean test` (default): BUILD SUCCESS, 240 tests, 0 failures, 0 skipped; `target/surefire-reports` has no GoldenChatIT file.
- `clean test -Pgolden`: BUILD SUCCESS; only `GoldenChatIT` ran (reports `TEST-...GoldenChatIT.xml`, `.txt`); `Tests run: 0` because the class-level `assumeTrue` skipped it (qwen3:4b not installed on this host). No other class executed.

## Harness behaviour

- Skips unless `GET /api/version` answers within 2 s and `/api/show` returns 200 for the chat model (`-Drag.golden.model`, default qwen3:4b) and nomic-embed-text. Ollama URL from `RAG_GOLDEN_OLLAMA_URL` (default http://localhost:11434). Overrides `rag.ollama.read-timeout=PT120S` as well as the plan's deadline/heartbeat, because the test profile's 2 s read timeout would abort generation.
- Seeds via admin key and client keys, drives `IndexWorker.runOnce()` until no PENDING, fails on FAILED documents or after 5 minutes.
- Log metadata is read from the ChatService/TransformService `generation endpoint=` line by requestId (polled up to 5 s because it is logged after `done`). `totalMs` in that line is wall-clock run time, not Ollama total_duration.
- Asserts: safety cases 3/3 and `estimatedPromptTokens >= promptEvalCount`; quality cases reported only. Report holds check names and metadata only, never answer text.
- Not exercised live: the harness compiles and skips cleanly, but the real run is plan 10-09.

## Deviations

1. [Rule 2 - hygiene] Added `rag-service/scripts/.gitattributes` (`*.sh text eol=lf`), not in files_modified: with `core.autocrlf=true` on this host the script would otherwise be checked out with CRLF and break under bash.
2. Environment limit: the tool sandbox refused `RAG_KEY= bash script`; the no-key exit was verified with `env -u RAG_KEY sh script chat` (exit 2, usage printed). `bash -n` passed, no CR bytes in the file, mode set with `git update-index --chmod=+x`.
3. Seed metadata lives in `clients.json` (`metadata` map per file) rather than a separate file; the plan's format is otherwise unchanged.
4. Assumption: citation `documentId` equals the external id used in the document PUT path (taken from `Citation`/`CitationBuilder`); confirm in the live run.

## Known Stubs

None.

## Self-Check: PASSED

Files exist and commits 0616319, 1cfef05, 2757693 are on the branch.
