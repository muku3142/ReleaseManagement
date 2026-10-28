---
phase: 11-tool-call-protocol
plan: 01
subsystem: rag-service config
tags: [config, security, tools]
requires: []
provides: [ToolProperties record bound from rag.tools.*, mandatory RAG_TOOL_SIGNING_SECRET]
affects: [compose.app.yaml, .env.example, docs/docker.md]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/config/ToolProperties.java
    - rag-service/src/test/java/com/evision/ragservice/config/ToolPropertiesTest.java
  modified:
    - rag-service/src/main/java/com/evision/ragservice/config/GenerationConfig.java
    - rag-service/src/main/resources/application.yml
    - rag-service/src/test/resources/application.yml
    - compose.app.yaml
    - .env.example
    - docs/docker.md
decisions:
  - Separate ToolProperties record so ChatProperties' 20-arg constructor is untouched
metrics:
  completed: 2026-10-15
---

# Phase 11 Plan 01: Tool protocol configuration Summary

`ToolProperties` (`rag.tools.*`) carries the HMAC signing secret plus round, call, tool, definition, result and num-predict caps. A secret under 32 characters fails startup, and `toString()` omits it.

## Tasks

1. ToolProperties record, GenerationConfig registration, ToolPropertiesTest (6 cases) - 6e6e23f
2. Values in both application.yml files, compose (`:?` fail-fast), .env.example, docs/docker.md - see git log (second commit)

## Verification

Full suite: 257 tests, 0 failures (251 baseline + 6 new). ChatProperties unchanged. No existing rag.chat value changed. No docker command run.

## Deviations from Plan

None. Edits to compose, docs and main yml are additive apart from two reworded sentences (compose header comment, docs intro) that the plan itself requested.

## Self-Check: PASSED
