---
phase: 10-rag-chat-citations
plan: 04
subsystem: rag-service
tags: [prompt, citations, refusal, safety]
requires: [10-01]
provides:
  - SourceDocument, TokenEstimator, Prompts (PROMPT_VERSION p10-v1), PromptBuilder, CitationBuilder, RefusalGuard
affects: [10-05]
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/application/SourceDocument.java
    - rag-service/src/main/java/com/evision/ragservice/application/TokenEstimator.java
    - rag-service/src/main/java/com/evision/ragservice/application/Prompts.java
    - rag-service/src/main/java/com/evision/ragservice/application/PromptBuilder.java
    - rag-service/src/main/java/com/evision/ragservice/application/CitationBuilder.java
    - rag-service/src/main/java/com/evision/ragservice/application/RefusalGuard.java
    - rag-service/src/test/java/com/evision/ragservice/application/PromptBuilderTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/CitationBuilderTest.java
    - rag-service/src/test/java/com/evision/ragservice/application/RefusalGuardTest.java
decisions:
  - Neutraliser loops to a fixpoint so spliced tokens like "<<<|im_start|><" cannot re-form
  - Citations take only integer ids from answer text; metadata always copied from SourceDocument
metrics:
  tasks: 2
  tests: 184 passing (baseline 158)
---

# Phase 10 Plan 04: Prompt, citation and refusal logic Summary

Pure, Spring-free classes carrying SAFE-02 (nonce fences, fixpoint neutraliser, rules only in the system message), RAG-07 (server-built citations, MARKED vs RETRIEVED basis, invented ids dropped and counted) and RAG-09 (RefusalGuard buffers until the NO_ANSWER prefix diverges; a truncated marker counts as a refusal). PromptBuilder budgets against num_ctx minus num_predict, dropping history oldest-first before any source, and never splits a turn.

## Commits

- ee7cfd8: SourceDocument, TokenEstimator, Prompts, PromptBuilder and tests
- Task 2 commit: CitationBuilder, RefusalGuard and tests

## Deviations from Plan

None - plan executed as written.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED

Full rag-service suite: 184 tests, 0 failures, BUILD SUCCESS.
