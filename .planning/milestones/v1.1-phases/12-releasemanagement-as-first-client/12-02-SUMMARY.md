---
phase: 12-releasemanagement-as-first-client
plan: 02
subsystem: assistant-tools
tags: [assistant, tools, read-only]
requires: []
provides: [AssistantTool contract, ToolRegistry, ReleaseRef, releases tool, dashboard tool, ReleaseService.releasesContaining]
affects: [12-03, 12-09]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/AssistantTool.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/ToolOutput.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/ToolArgumentException.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/ToolRegistry.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/ReleaseRef.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/ReleasesTool.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/DashboardTool.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
    - src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseTicketItemJpaRepository.java
decisions:
  - releasesContaining reuses the existing summary projection by filtering search("") by the ids found, since toSummary maps a projection row, not an entity
metrics:
  tasks: 2
  completed: 2026-10-15
---

# Phase 12 Plan 02: Tool contract, releases and dashboard tools Summary

AssistantTool contract, sorted duplicate-rejecting ToolRegistry, exact-match ReleaseRef resolver, `releases` and `dashboard` tools returning capped (25 rows, `truncated`), free-text-free records, plus a read-only `ReleaseService.releasesContaining` over a derived `findByTicketKey` query (no migration).

## Deviations from Plan

- releasesContaining collects release ids from the item entities and filters `jpa.search("")` rows through the existing `toSummary` (an extra full summary query, acceptable at this scale; no new projection).
- Only the plan's own test classes were run (ReleaseRefTest, ReleasesToolTest, DashboardToolTest, ReleaseService*Test); the persistence ITs and the full suite were left to the orchestrator per the memory constraint. No test fake implemented ReleaseRepository, so none needed updating.

## Commits

- 359255f: contract, ReleaseRef, registry, releasesContaining
- 4ddb688: releases and dashboard tools with tests

## Self-Check: PASSED
