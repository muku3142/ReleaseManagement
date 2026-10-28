---
phase: 16-portfolio-dashboard-assistant-answers
plan: 05
subsystem: assistant
tags: [assistant, tools, rag-service]
requires: [16-03]
provides: [installs assistant tool, rag-service 6 tools / 1900 chars intake]
affects: [16-08]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/InstallsTool.java
    - src/test/java/com/evision/releasemgmt/application/assistant/tools/InstallsToolTest.java
  modified:
    - rag-service/src/main/resources/application.yml
    - rag-service/src/test/resources/application.yml
    - rag-service/src/test/java/com/evision/ragservice/config/ToolPropertiesTest.java
    - src/test/java/com/evision/releasemgmt/application/assistant/tools/ToolDefinitionsTest.java
decisions:
  - "installs reads PortfolioDashboardService.matrix(Set.of(), true) once per call, so answers equal the dashboard matrix"
  - "State and inProduction fields steer the answer shape; no prompt edits, promptHash unchanged"
metrics:
  tasks: 2
  completed: 2026-10-22
---

# Phase 16 Plan 05: installs assistant tool Summary

A sixth assistant tool, `installs`, answers "which release does client X run for product Y" (RUNS / NOT_INSTALLED / NOT_LINKED with releasesBehind and newest) and "which clients run release R" (current runners only, 25 rows with total and truncated), read live from the portfolio matrix. rag-service intake caps were raised to 6 tools / 1900 definition characters.

## Commits
- feat(16-05): let rag-service accept a sixth assistant tool
- feat(16-05): answer what clients run with a live installs assistant tool

## Tests
- rag-service: ToolPropertiesTest 6, ToolsFixtureTest 6, ChatServiceTest 54 passing
- Host: InstallsToolTest 13, ToolDefinitionsTest 2, AssistantServiceTest 22 passing
- The installs definition serializes to 342 characters; ToolDefinitionsTest asserts the six-tool total is within 1900.

## Deviations from Plan
None. Prompts untouched. Host application.yml untouched (tool description falls back to `description()`). Files kept CRLF.

## Deploy note
rag-service with the raised caps must run before or with an app offering six tools (plan 16-08).

## Known Stubs
None.

## Threat Flags
None. T-16-19..23 mitigated as planned (length checks, exact name match, structured fields only, row cap, caps raised with tests).

## Self-Check: PASSED
