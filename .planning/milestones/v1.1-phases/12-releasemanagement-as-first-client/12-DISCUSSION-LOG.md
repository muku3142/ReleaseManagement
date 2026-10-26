# Phase 12: ReleaseManagement as First Client - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-15
**Phase:** 12-releasemanagement-as-first-client
**Areas discussed:** Collection layout, Freshness & backfill sweep, Tool set & what tools return, Scope of indexed data

| Area | Question | Options | Selected |
|------|----------|---------|----------|
| Collections | Layout | One per source type / One for everything / One per release | One per source type |
| Collections | Document ids | Stable natural ids / Per-release ids / You decide | Stable natural ids |
| Freshness | Latency | Seconds, best effort / Minutes, batched / You decide | Seconds, best effort |
| Freshness | Outage behaviour | Drop, sweep heals / Durable outbox / You decide | Drop it, sweep heals |
| Freshness | Sweep schedule | Startup + every 30 min / Nightly / Manual endpoint only | On startup + every 30 min |
| Tools | Tool output | Structured fields only / Fields + short excerpts / You decide | Structured fields only |
| Tools | Tool set | Full proposed set / Minimal core / You decide | Full proposed set |
| Scope | Releases indexed | All releases / Open + last N months / You decide | All releases |
| Scope | Switch default | Off / On | Off by default |
| Scope | Ticket comments | Only what screens show / No comments | Only what the screens show |

**Flagged during discussion:** rag-service metadata has no arrays (D-09-07), so a ticket in several releases needs an encoding for `release_id` filtering; left to research.

## Claude's Discretion
RAG client adapter shape and config, tool-loop placement and "how I answered" recording, tool schemas, pushed text format and metadata keys, after-commit wiring and sweep scheduling, collection creation on enable.

## Deferred Ideas
Durable outbox; per-user Jira project filtering; free-text tool excerpts; per-document AI prompts (Phase 14).
