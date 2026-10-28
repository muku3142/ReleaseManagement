---
phase: 02-jira-ticket-browsing
plan: 01
subsystem: jira-read-foundation
tags: [jira-cloud, adf, error-taxonomy, problem-detail, jackson-3, bounded-traversal]
dependency-graph:
  requires:
    - "01-03: JiraAuthFailureReason taxonomy and JiraAuthenticationException"
    - "01-03: GlobalExceptionHandler's exhaustive no-default reason switch and its private problem(...) helper"
    - "01-07: frontend REASON_MESSAGES table in api/auth.ts — the single reason-to-text source"
  provides:
    - "JiraAuthFailureReason.RESULT_SET_TOO_LARGE: eighth reason, 422 UNPROCESSABLE_ENTITY, distinct copy in both backend detail and frontend REASON_MESSAGES"
    - "domain/jira/JiraTicketReadException: ticket-read failure type extending JiraAuthenticationException, inheriting the one existing @ExceptionHandler"
    - "adapters/jira/AdfFlattener: dependency-free ADF JsonNode -> plain-text-with-Markdown-markers walker, bounded at 5000 nodes / depth 50"
    - "app.jira.search.page-size=100 / max-pages=50 in BOTH application.yml files"
  affects:
    - "Wave 2 adapters (02-02/02-03/02-04): map every Jira HTTP failure to JiraTicketReadException; no parallel error shape"
    - "Wave 2 ticket adapter: flattens description/comment ADF via new AdfFlattener() — plain class, no bean wiring needed"
    - "Phase 5 document generation: the same flattened Markdown-marker string feeds generated documents"
    - "The pagination loop in wave 2 raises RESULT_SET_TOO_LARGE on breaching max-pages — the loud-failure half of the silent-truncation/infinite-loop pair"
tech-stack:
  added: []
  patterns:
    - "New JiraAuthFailureReason values are deliberately compile-breaking: GlobalExceptionHandler's switch has no default, so an added reason cannot ship without its ProblemDetail arm"
    - "Bounded recursive walk over untrusted external structure: node counter + depth parameter, breach appends a single visible '[content truncated]' marker and returns normally — never throws"
    - "Unknown-input degradation over rejection: unrecognised ADF node types keep their text content and walk their children; unrecognised marks keep the text and drop only the decoration"
    - "Config that wave-2 code will read must land in src/test/resources/application.yml too — that file shadows main's rather than merging"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/jira/JiraTicketReadException.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/AdfFlattenerTest.java
  modified:
    - src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthFailureReason.java
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
    - src/main/resources/application.yml
    - src/test/resources/application.yml
    - src/test/java/com/evision/releasemgmt/adapters/web/JiraAuthProblemDetailTest.java
    - src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java
    - frontend/src/api/auth.ts
    - frontend/src/api/auth.test.ts
decisions:
  - "D-PLAN-01-A applied as planned: JiraTicketReadException extends JiraAuthenticationException rather than sitting beside it, so Spring routes it to the single existing @ExceptionHandler and the eight-arm reason switch is never duplicated (ExceptionHandler count stays 4)"
  - "ArchitectureBoundaryTest's domain/ports framework-import ban extended with 'tools.jackson..' — the existing 'com.fasterxml.jackson..' entry does not cover this project's Jackson 3 databind, so JsonNode could have leaked into domain/ unnoticed once this phase starts handling ADF"
  - "AdfFlattener carries its traversal state in an int[]{nodeCount, truncatedFlag} threaded through the walk rather than mutable fields, keeping the class safely reusable across calls; depth is a plain method parameter as the plan required"
metrics:
  duration_minutes: 0
  tasks_completed: 2
  files_created: 3
  completed_date: "2026-08-03"
---

# Phase 02 Plan 01: Ticket-Read Failure Taxonomy & ADF Flattener Summary

Lands the two cross-cutting pieces every wave-2 adapter compiles against: one new reason code carried end-to-end through the Phase 1 error contract, and a bounded ADF-to-text flattener.

## What Was Built

**Task 1 — `RESULT_SET_TOO_LARGE` end-to-end + search bounds.** Appended the eighth value to `JiraAuthFailureReason`, which deliberately broke `GlobalExceptionHandler`'s exhaustive `default`-less switch until its arm was written: 422 `UNPROCESSABLE_ENTITY`, title "Too many matching tickets", detail matching the UI-SPEC copy verbatim. `JiraTicketReadException` extends `JiraAuthenticationException` and adds nothing but a constructor, so it inherits the one existing `@ExceptionHandler` — the handler count is unchanged at 4. The frontend gained one `REASON_MESSAGES` entry with the same copy, immediately after `JIRA_ERROR`, and `auth.test.ts`'s pairwise-distinctness test now proves the new copy is distinguishable from the generic Jira-error text — which is the entire point of the code existing rather than reusing `JIRA_ERROR`. `app.jira.search.page-size: 100` / `max-pages: 50` went into `src/main/resources/application.yml` **and** `src/test/resources/application.yml`, pre-empting the shadowing trap that bit plans 01-01 and 01-03. `ArchitectureBoundaryTest` now also bans `tools.jackson..` in `domain/` and `ports/`.

**Task 2 — Bounded ADF flattener.** `AdfFlattener` is a plain `public final class` with one public method, `String flatten(JsonNode)`, no Spring imports and no annotations — instantiable with `new`. It emits plain text with lightweight Markdown markers so the same string serves the browse UI now and Phase 5 document generation later. In-scope D-02-04 nodes: `doc`, `paragraph`, `text`, `hardBreak`, `bulletList`, `orderedList`, `listItem` (two-space indent per nesting level), `codeBlock`. Marks: `strong`, `em`, `code`, `link` (appends the href in parentheses), applied in array order. Everything unrecognised degrades — an unknown node contributes its own `text` field and its children are still walked, an unknown mark keeps the text and drops only the decoration. Bounds are `MAX_NODES = 5000` and `MAX_DEPTH = 50`; a breach sets a latch, appends exactly one `\n[content truncated]`, and unwinds normally. There is no `throw` anywhere in the file — a hostile or malformed ADF document must degrade visibly, not break ticket browsing. 13 unit tests cover every node, every mark, both degrade rules, and a programmatically built 60-deep tree.

## Verification

- `mvn clean test` — **133 tests, all green** (was 119 at phase start; +14: 13 `AdfFlattenerTest` + 1 `JiraAuthProblemDetailTest`). `clean` used deliberately, per the documented incremental-compilation trap.
- `cd frontend && npx vitest run` — **15/15 green**, 3 files.
- All Task 1 and Task 2 acceptance-criteria greps pass, including the ones that assert absence: `org.springframework` → 0, `com.fasterxml.jackson.databind` → 0, `throw ` → 0 in `AdfFlattener.java`.
- Commits: `85b3982` (Task 1), `e9f840a` (Task 2).

## Deviations from Plan

None. Both tasks were implemented as specified.

## Issues Encountered

None.

## Known Stubs

None. Both artifacts are complete and directly consumable by wave 2. Note that nothing *raises* `RESULT_SET_TOO_LARGE` yet — the pagination loop that breaches `max-pages` is wave 2's work; this plan only establishes the code, its HTTP mapping and its user-facing copy.

## Threat Flags

None beyond the plan's own threat model. The unbounded-traversal risk over untrusted ADF (the reason the bounds exist at all) is mitigated in-plan and proven by the over-deep-tree test.

## Self-Check: PASSED

All 3 created and 8 modified files verified on disk. Both task commits verified in `git log`.
