---
phase: 02-jira-ticket-browsing
plan: 02
subsystem: jira-project-browsing
tags: [jira-cloud, auth-02, offset-pagination, tanstack-query, tickets-route]
dependency-graph:
  requires:
    - "01-05: JiraAccessContextService.currentContext() — the sole AUTH-02 credential resolution point"
    - "01-07: apiFetch + REASON_MESSAGES reason-to-copy table in frontend/src/api/auth.ts"
    - "01-07: authenticatedRoute in router.tsx and the AppShell nav"
    - "02-01: JiraTicketReadException as the single ticket-read failure type"
  provides:
    - "ports/JiraProjectPort: listProjects(access) and listVersions(access, projectId)"
    - "adapters/jira/JiraProjectAdapter: offset-paginated GET /rest/api/3/project/search, unpaginated GET /rest/api/3/project/{id}/versions"
    - "application/JiraProjectService: resolves currentContext() before every port delegation"
    - "GET /api/jira/projects and GET /api/jira/projects/{projectId}/versions"
    - "frontend /tickets route with the project and fix-version filter bar, plus its nav link"
  affects:
    - "02-05: TicketController sits beside JiraProjectController and reuses the same service/port shape"
    - "02-06: adds the Load-tickets button and results table onto this filter bar (same TicketsRoute.tsx file)"
    - "02-07: adds row expansion to the table plan 06 builds here"
    - "Phase 3 release assembly: consumes the selected project/fix-version pair as the release's scope input"
tech-stack:
  added: []
  patterns:
    - "Jira's /project/search is offset-paginated (startAt/maxResults/isLast) — deliberately NOT the cursor model /search/jql uses; the two must not be conflated"
    - "Every application-layer Jira read calls accessContextService.currentContext() itself; no method accepts an accountId, so a caller cannot select another user's credential"
    - "Dependent dropdown: the versions query is keyed on the selected projectId and disabled until one is chosen, so no request fires for an empty selection"
key-files:
  created:
    - src/main/java/com/evision/releasemgmt/domain/jira/JiraProjectSummary.java
    - src/main/java/com/evision/releasemgmt/domain/jira/JiraVersion.java
    - src/main/java/com/evision/releasemgmt/ports/JiraProjectPort.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraProjectAdapter.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraProjectSearchPage.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraProjectPayload.java
    - src/main/java/com/evision/releasemgmt/adapters/jira/JiraVersionPayload.java
    - src/main/java/com/evision/releasemgmt/application/JiraProjectService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/JiraProjectController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/JiraProjectResponse.java
    - src/main/java/com/evision/releasemgmt/adapters/web/dto/JiraVersionResponse.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/JiraProjectAdapterTest.java
    - src/test/java/com/evision/releasemgmt/adapters/web/JiraProjectControllerTest.java
    - frontend/src/api/jiraProjects.ts
    - frontend/src/routes/TicketsRoute.tsx
  modified:
    - frontend/src/router.tsx
    - frontend/src/components/AppShell.tsx
decisions:
  - "Project listing pages through /project/search on the offset model (startAt + isLast) rather than the cursor model — this endpoint genuinely is offset-paginated; only /search/jql uses nextPageToken cursors, and plan 03 owns that"
  - "Versions are read from /project/{id}/versions (unpaginated) rather than the paginated /version variant: the phase needs the full version list as filter input, and the unpaginated form returns it in one call"
  - "The filter bar ships pickers only — no Load-tickets button and no table, so no dead affordance reaches the user before plan 06 wires the search"
metrics:
  duration_minutes: 0
  tasks_completed: 3
  files_created: 15
  completed_date: "2026-08-04"
---

# Phase 02 Plan 02: Project & Fix-Version Pickers Summary

Delivers the first user-visible slice of Phase 2: a **Tickets** screen reachable from the nav, whose project picker is populated from the signed-in release manager's own Jira account and whose fix-version picker fills in once a project is chosen.

## What Was Built

**Task 1 — `JiraProjectPort` + live adapter.** `JiraProjectSummary(id, key, name)` and `JiraVersion(id, name, released, archived)` are plain domain records with no framework imports. `JiraProjectAdapter` reads `GET /rest/api/3/project/search` on Jira's **offset** pagination model — `startAt`/`maxResults` with `isLast` as the terminator — which is deliberately a different mechanism from the cursor pagination plan 03 builds for `/search/jql`; conflating the two is the trap this separation avoids. Versions come from the unpaginated `GET /rest/api/3/project/{projectId}/versions`. Every Jira HTTP failure maps to `JiraTicketReadException` with the reason taxonomy plan 01 established, so no parallel error shape exists.

**Task 2 — Service + REST endpoints.** `JiraProjectService` calls `accessContextService.currentContext()` before each of its two port delegations. This is where AUTH-02 stops being vacuous: the service exposes no overload taking an `accountId`, so the credential is always the signed-in principal's and a caller cannot select another user's. `JiraProjectController` exposes `GET /api/jira/projects` and `GET /api/jira/projects/{projectId}/versions`, mapping domain records to `JiraProjectResponse`/`JiraVersionResponse` DTOs.

**Task 3 — `/tickets` route and filter bar.** `TicketsRoute.tsx` (82 lines) renders the two dependent dropdowns via TanStack Query: the versions query is keyed on the chosen `projectId` and disabled until one exists, so no request fires for an empty selection. Jira failures render the reason-coded message through the existing `REASON_MESSAGES` table rather than leaving a silently blank dropdown. The route is registered as `ticketsRoute` under `authenticatedRoute` and linked from `AppShell`.

## Verification

- `mvn clean test` — green. `cd frontend && npm test` — 15/15 green.
- All must-have artifact and key-link greps pass: `/rest/api/3/project/search` present in the adapter, `accessContextService.currentContext()` appears twice in the service (once per read), `TicketsRoute.tsx` is 82 lines (min 60), `ticketsRoute` wired in `router.tsx`.
- Commits: `dde86b4` (Task 1), `72c0eb8` (Task 2), `35b40e9` (Task 3).

## Deviations from Plan

None in implementation. **Process deviation:** this plan's three tasks were executed and committed in a git worktree by an earlier session that was interrupted before writing this SUMMARY. The worktree branch was merged to `main` intact (merge `e8926ce`) and this summary was reconstructed from the plan, the three commits and the files on disk.

## Issues Encountered

None in this plan's own work. See plan 03's summary for the HTTP/2 test-harness issue that the interrupted session was debugging.

## Known Stubs

The filter bar has no "Load tickets" button and no results table — that is plan 06's scope, deliberately excluded here so no dead affordance ships. The JQL escape hatch from D-02-01 is also not present yet; plan 06 adds it.

## Threat Flags

None beyond the plan's threat model. The AUTH-02 boundary is the material one and it is enforced structurally (no accountId-accepting overload exists), not by convention.

## Self-Check: PASSED

All 15 created and 2 modified files verified on disk. All three task commits verified in `git log`.
