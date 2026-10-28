# Phase 10: RAG Chat & Citations - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-13
**Phase:** 10-rag-chat-citations
**Areas discussed:** Request limits & GPU contention, Chat request shape, Transform endpoint (RAG-11)
**Offered but not selected:** "I don't know" & citations (left to Claude's discretion with stated defaults)

---

## Request limits & GPU contention

| Question | Options | Selected |
|----------|---------|----------|
| Client over its in-flight limit | Reject with 429 / Short queue then reject / You decide | Reject with 429 (default 1 per client, configurable) |
| Indexing worker while chat generates | Pause indexing during chat / Let them share / You decide | Pause indexing during chat |
| Chat deadline | 120 s configurable / 60 s configurable / You decide | 120 s, configurable |
| Ollama down or model not pulled | Fail fast with distinct reasons / Open stream then error event / You decide | Fail fast, distinct reasons (503 OLLAMA_UNAVAILABLE / MODEL_NOT_AVAILABLE) |

---

## Chat request shape

| Question | Options | Selected |
|----------|---------|----------|
| What one request searches | Several collections + filters / One collection + filters / You decide | Several collections + filters |
| History handling | Caller sends turns, service trims / Caller trims, service rejects / You decide | Caller sends turns, service trims |
| Chat model choice | Service config only / Caller picks from allowlist / You decide | Service config only |
| Passages to the model | Service default, caller may lower / Fixed by service / You decide | Service default, caller may lower |

---

## Transform endpoint (RAG-11)

| Question | Options | Selected |
|----------|---------|----------|
| Instruction ownership | Caller free-form / Service presets + custom / You decide | Caller sends free-form instruction |
| Input size | Fit context window, reject beyond / Chunk and merge / You decide | Fit the context window, 413 beyond |
| Instruction-looking text in the document | Fence as data / Treat as trusted / You decide | Fence as data, like passages |
| Limits and events | Same as chat / Separate, longer deadline / You decide | Same limits, same events (minus citations) |

---

## Claude's Discretion

- Refusal: two layers (retrieval-score threshold + prompt instruction), fixed refusal answer flagged in `done`, empty citations.
- Citations: passage metadata only, deduplicated per document; research compares "all passages sent" vs "passages marked as used".
- Endpoint paths, payload shapes, Ollama option values, k bounds, history token budget, SSE mechanics, golden fixture format.
- Query rewriting from history: default off.

## Deferred Ideas

- Query rewriting for follow-ups (if the golden fixture needs it)
- Chunk-and-merge transforms
- Caller-selectable chat models
- Service-owned transform presets
- Waiting queue instead of 429
- Persisted conversations
