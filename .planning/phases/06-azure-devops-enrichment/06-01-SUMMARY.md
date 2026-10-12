---
phase: 06-azure-devops-enrichment
plan: 01
subsystem: enrichment
tags: [jira, dev-status, azure-devops, domain-contracts]
requires: []
provides:
  - "domain/enrichment contracts (CodeLinks, DevStatusLinks, StatusHistory, rules)"
  - "JiraDevStatusPort + JiraDevStatusAdapter (summary-first, drift guard)"
  - "JiraTimestamps shared parser"
affects: [06-02, 06-03, 06-04, 06-05]
tech-stack:
  added: []
  patterns: ["interface-first domain records", "URL allow-list as SSRF guard", "summary-first envelope guard"]
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/enrichment/ (13 files)
    - src/main/java/com/evision/releasemgmt/ports/JiraDevStatusPort.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraTimestamps.java
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/jira/devstatus/JiraDevStatusAdapter.java (renamed from the spike adapter)
    - src/test/java/com/evision/releasemgmt/adapters/jira/devstatus/JiraDevStatusAdapterTest.java (renamed from the spike test)
key-decisions:
  - "Documents list only PRs whose source branch carries the ticket key; env-merge PRs stay visible on the release page"
  - "FAILED keeps last good rows but renders empty in documents (only LINKED reaches them)"
requirements-completed: [ADO-01, ADO-03, INSIGHT-03]
duration: ~25min
completed: 2026-10-12
---

# Phase 6 Plan 01: Enrichment contracts and dev-status port Summary

Pure enrichment domain records and rules, plus the 02-04 spike promoted to a production `JiraDevStatusPort` that turns the captured dev-status fixtures into 3 PRs (1 non-counting), key-tagged commits with author dates and 1 branch, with typed drift and HTTP failures.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. Domain contracts and pure rules | f7593ee | 13 domain files, 4 test classes (15 tests) |
| 2. JiraDevStatusPort + adapter + JiraTimestamps | 67fc45b | spike adapter and test git-mv'd; 9 WireMock tests + 3 timestamp tests |

Full backend suite: 661 tests, 0 failures (`mvn -P '!frontend' clean test`).

## Deviations from Plan

- TDD red commits were not made separately: tests and implementation were written together per task and committed once, with each task verified green. No behaviour impact.
- The spike's raw envelope-key assertions (`keys(body)` on JsonNode) were dropped, because the adapter no longer exposes JsonNode across the port. Schema drift is instead covered by the two DevStatusUnrecognisedException tests (empty detail, foreign `_instance.type`).
- Acceptance criterion `grep -rn "CNV-2896" src/test/java` prints nothing is not literally true: pre-existing occurrences in `TemplateSeederIT`, `GenerateThenExportSeamTest`, `TicketSearchPathTest` (and `SampleReleaseView` in main) predate this plan and are out of scope. None are in files touched here.
- Maven was invoked with `JAVA_HOME=... mvn` inline instead of `export` because the sandbox rejected the export form.

## Known Stubs

None.

## Threat Flags

None beyond the plan's threat model (T-06-01..03 mitigated: URL allow-list, field truncation, envelope guard).

## Self-Check: PASSED

Commits f7593ee and 67fc45b exist; created files present; no references to the spike adapter or `resolveNumericIssueId` remain in `src/`.
