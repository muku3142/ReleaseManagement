---
phase: 13-assistant-screen-quality-gate
plan: 02
subsystem: frontend
tags: [assistant, sse, chat-store, react]
requires: []
provides:
  - apiFetchRaw in api/client.ts
  - api/assistant.ts (protocol types, parseSse, streamChat, statusLabel, status/freshness queries)
  - assistant/chatStore.ts (useChatSession, chatActions)
affects: [13-04]
tech-stack:
  added: []
  patterns: [useSyncExternalStore module store, hand-rolled SSE parser over fetch]
key-files:
  created:
    - frontend/src/api/assistant.ts
    - frontend/src/assistant/chatStore.ts
    - frontend/tests/api/assistant.test.ts
    - frontend/tests/assistant/chatStore.test.ts
  modified:
    - frontend/src/api/client.ts
key-decisions:
  - "apiFetchRaw shares buildInit/failure helpers with rawFetch; apiFetch behaviour unchanged"
  - "Abort during a stream is surfaced by checking signal.aborted after each read and cancelling the reader, so it works on real fetch and on synthetic streams"
requirements-completed: [ASST-02, ASST-07, ASST-08]
duration: 15min
completed: 2026-10-18
---

# Phase 13 Plan 02: Assistant data layer Summary

Fetch-based SSE client (POST, XSRF echo, session refresh-then-retry) plus an in-memory chat store with the 150-character hold-back, Stop, scope reset and completed-turn history.

## Tasks

| Task | Commit | Notes |
|------|--------|-------|
| 1 apiFetchRaw + api/assistant.ts | b681f9b | 14 tests (parser, split multi-byte, headers, refresh, 404, abort, labels) |
| 2 chatStore | f70838d | 21 tests covering every listed behaviour |

## Verification

- Frontend suite: 379 tests green (baseline 336 + 43 new); `tsc -b` clean.
- No `localStorage`/`sessionStorage`/`EventSource` in the new source files; a test spies on Storage and asserts zero calls.
- jsdom supports `ReadableStream` on `Response`, so no `@vitest-environment node` override was needed (assumption A5 held).

## Deviations from Plan

None in behaviour. TDD was done as test+implementation per task in a single commit each rather than separate RED/GREEN commits.

## Self-Check: PASSED
