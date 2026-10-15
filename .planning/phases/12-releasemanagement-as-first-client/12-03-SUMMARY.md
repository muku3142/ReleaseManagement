---
phase: 12-releasemanagement-as-first-client
plan: 03
subsystem: assistant-tools
tags: [assistant, tools, read-only]
requires: [12-02]
provides: [tickets tool, deployments tool, documents tool, ToolDefinitionsTest]
affects: [12-07, 12-09]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/TicketsTool.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/DeploymentsTool.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/DocumentsTool.java
    - src/test/java/com/evision/releasemgmt/application/assistant/tools/TicketsToolTest.java
    - src/test/java/com/evision/releasemgmt/application/assistant/tools/DeploymentsToolTest.java
    - src/test/java/com/evision/releasemgmt/application/assistant/tools/DocumentsToolTest.java
    - src/test/java/com/evision/releasemgmt/application/assistant/tools/ToolDefinitionsTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/ReleasesTool.java
    - src/main/java/com/evision/releasemgmt/application/assistant/tools/DashboardTool.java
decisions:
  - Tools map to explicit records; note, correctionReason, description, comments and markdown are never read
  - Churn nulls stay JSON null; storyPoints is null unless it was actually read
metrics:
  tasks: 2
  completed: 2026-10-15
---

# Phase 12 Plan 03: tickets, deployments, documents tools Summary

Three structured-only tools (`tickets`, `deployments`, `documents`) completing the five-tool set, plus `ToolDefinitionsTest` guarding the Phase 11 intake limits (5 tools, descriptions <= 300, serialised definitions <= 1,500 chars, flat schema subset). Canary tests prove free text never reaches results.

## Deviations from Plan

- [Rule 3] The five definitions initially serialised to 1,733 chars (limit 1,500). Per the plan, descriptions were shortened, which required editing `ReleasesTool.java` and `DashboardTool.java` (12-02 files, outside this plan's files_modified). The "Values are authoritative" sentence was dropped from all five descriptions; the system prompt (12-07) should carry that instruction. Final total passes the test (exact figure not captured; <= 1,500).
- `TicketsTool` does not depend on PromotionService, EnvironmentLadderService, ActorResolutionService or Clock (unused); assignee/reporter pass through as Jira stores them. `DeploymentsTool` resolves recordedBy through ActorResolutionService (falls back to the raw account id, per `ResolvedActors.display`).
- Per the memory constraint only `*ToolTest`, `ToolDefinitionsTest` and `ReleaseRefTest` were run (all green); the full suite was left to the orchestrator.

## Commits

- 3863427: tickets and deployments tools with tests
- 8f47235: documents tool, ToolDefinitionsTest, shortened descriptions

## Self-Check: PASSED
