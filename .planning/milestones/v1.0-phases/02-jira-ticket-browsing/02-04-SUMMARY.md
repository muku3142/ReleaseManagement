---
phase: 02-jira-ticket-browsing
plan: 04
subsystem: jira-adapter
tags: [jira, dev-status, azure-devops, spike, wiremock, contract-fixture]
requires:
  - phase: 02-jira-ticket-browsing
    provides: plan 01 JiraTicketReadException, JiraAccessContext
provides:
  - 02-DEVSTATUS-SPIKE.md Phase 6 scoping findings (verdict GO)
  - Redacted real-tenant dev-status contract fixtures
  - JiraDevStatusSpikeAdapter (numeric-id lookup then dev-status detail, JsonNode)
affects: [06-azure-devops-enrichment]
tech-stack:
  added: []
  patterns: [contract fixture loaded from file into WireMock stub]
key-files:
  created:
    - .planning/phases/02-jira-ticket-browsing/02-DEVSTATUS-SPIKE.md
    - src/main/java/com/evision/releasemgmt/adapters/jira/devstatus/JiraDevStatusSpikeAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/devstatus/JiraDevStatusSpikeAdapterTest.java
    - src/test/resources/fixtures/devstatus-cnv-2896-detail.json
    - src/test/resources/fixtures/devstatus-cnv-2896-repository.json
    - src/test/resources/fixtures/devstatus-cnv-2896-summary.json
  modified:
    - .planning/STATE.md
key-decisions:
  - "Verdict GO for discovery: dev-status returns ADO branches, PRs and commits; commit lists are incomplete (only key-referencing commits), so Phase 6 needs the ADO API for full commits"
  - "applicationType must be the summary byInstanceType key AzureRepos; wrong values return empty detail indistinguishable from no link"
requirements-completed: []
duration: n/a
completed: 2026-10-07
---

# Phase 2 Plan 4: Jira to ADO dev-status spike Summary

**Verdict: GO for discovery via `/rest/dev-status/1.0/issue/detail` (`applicationType=AzureRepos`), with a commit-coverage limitation: only commits whose message references the issue key are listed, so Phase 6 must use the Azure DevOps API for complete commit lists.** Findings: `02-DEVSTATUS-SPIKE.md`.

## Accomplishments
- Task 1 (human capture) completed earlier: numeric id 48939, working applicationType `AzureRepos`.
- Redacted three fixtures (branch/PR payload, repository/commits payload, summary), structure preserved; zero tenant, org, project, repo, author or ticket identifiers under `src/`.
- `JiraDevStatusSpikeAdapter` behind the adapter boundary, returning `JsonNode`, with the six-branch error cascade.
- Findings doc answers all three D-02-05 questions and records the empty-detail trap, env-merge PR noise and the `Problem/Incident` link type for INSIGHT-03.
- STATE.md blocker closed, one Decisions line added.

## Task Commits
1. Task 1: human-action capture (no commit)
2. Task 2: `4a1e280` feat(02-04): spike adapter and redacted fixtures
3. Task 3: `d0b5c80` docs(02-04): findings and STATE.md blocker closed

## Verification
- `JiraDevStatusSpikeAdapterTest`: 7 green. Full backend `mvn -P '!frontend' clean test`: 641 green (baseline 634 plus 7).

## Deviations from Plan

1. **[Rule 2 - coverage] Seventh test added**: `emptyDetailIsAnOrdinaryResultNotAnError` (plan listed six) to pin the wrong-applicationType trap; a further test covers the `repository` payload fixture. Plan's literal `@Test` count of 6 is therefore 7.
2. **Additional fixture** `devstatus-cnv-2896-repository.json` (commits payload), as directed by the orchestrator.
3. **Tests use `EXAMPLE-1` as the issue key** instead of CNV-2896 so `grep -r CNV-2896 src/` stays empty; fixture file names keep `cnv-2896` per the plan.
4. **Redaction extended**: org, project, repo GUIDs, PR ids/titles, avatar and `ari` values, commit messages (key-free message kept key-free), per the orchestrator's list.
5. **Empty `spike-capture/` directory remains**: its contents were deleted, but the directory itself could not be removed because it was the shell working directory (and a later removal was blocked by a safety check). It is empty and git-untracked; remove with `rmdir` when convenient.

## Issues Encountered
None blocking. No python on the machine; redaction was done with a node script.

## Next Phase Readiness
Phase 6 can be planned from `02-DEVSTATUS-SPIKE.md` without re-running discovery.

## Self-Check: PASSED
