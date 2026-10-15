---
phase: 12-releasemanagement-as-first-client
plan: 01
subsystem: config
tags: [assistant, configuration, compose]
requires: []
provides:
  - app.assistant.* configuration surface and master switch (default off)
  - AssistantConfig (conditional; enables scheduling and AssistantProperties)
affects: [12-02, 12-03, 12-04]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/config/AssistantProperties.java
    - src/main/java/com/evision/releasemgmt/config/AssistantConfig.java
    - src/test/java/com/evision/releasemgmt/config/AssistantPropertiesTest.java
  modified:
    - src/main/resources/application.yml
    - src/test/resources/application.yml
    - compose.app.yaml
    - .env.example
    - docs/docker.md
decisions:
  - "Collection names are a domain enum (plan 12-04), not configuration"
metrics:
  tasks: 2
  completed: 2026-10-15
---

# Phase 12 Plan 01: Assistant configuration surface Summary

`app.assistant.*` properties record with fail-fast validation and a redacted key, a conditional config class
(`@EnableScheduling`, no executor bean), and the switch wired through both ymls, compose, `.env.example` and docs.

## Commits
- 48161c4: feat(12-01) properties record, config, test
- b9d8149: feat(12-01) ymls, compose, env example, docs

## Verification
- AssistantPropertiesTest: 4 tests, 0 failures (`clean test-compile` plus that class only, per memory constraints).
- `docker compose -f compose.app.yaml config` with dummy values resolves the three RM_ASSISTANT_* vars; no new depends_on.
- Full backend suite NOT run here (orchestrator runs it after merge).

## Deviations from Plan
- Task 2 verify step called for the full `clean test`; skipped by instruction (memory). The context-start check with the
  switch off therefore rests on the orchestrator's post-merge run.
- Added two short WHY comments in application.yml (timeout and cap rationale).

## Known Stubs
None.

## Threat Flags
None.

## Self-Check: PASSED
