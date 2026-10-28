Use /gsd-quick for this task (this repo's CLAUDE.md requires a GSD entry point before any edits). Then build the following.

# Task: Briefings page in ReleaseManagement

## Goal
Add a read-only **Briefings** page at `http://localhost:8080/briefings`. It shows the latest result and the run history of five scheduled Claude briefings. The briefings are written as JSON files into `briefings/data/` by scheduled tasks running on this computer. They are configured by `briefings/config.json`, which this task also creates.

The five briefings (ids are fixed):

| id | Name | Schedule (Africa/Cairo) |
|---|---|---|
| jira_digest | Daily Jira digest | Sun–Thu 08:44 |
| sprint_check | Sprint backlog check | Wed 09:41 |
| weekly_status | Weekly status update | Thu 13:41 |
| reg_watch | Payments regulation watch | Sun 08:36 |
| gulf_invest | Saudi & Gulf investment brief | Mon 09:33 |

## Data contract (written by the scheduled tasks, read by the app)
`briefings/data/<id>.json` is a JSON array of runs, oldest first, at most 60:
```json
[{"run_at":"2026-10-26T05:44:12Z","status":"attention","headline":"3 blockers and 2 stale stories in CNV","attention":5,"h1":"CNV-3120 blocked on bank config","h2":"...","h3":null}]
```
- `status` ∈ `ok | attention | quiet | failed | setup` (`setup` rows are placeholders, so ignore them)
- `headline` ≤ 140 chars, `h1..h3` ≤ 120 chars or null, `attention` is an integer ≥ 0 or null
- A missing file means "not run yet". It is not an error.

## Follow the existing architecture (hexagonal: domain / ports / application / adapters)
Read these first and mirror their style, naming, ProblemDetail handling and tests: `ProductController` + `ProductControllerTest`, `PortfolioDashboardController` + its test, `SecurityConfig`, `GlobalExceptionHandler`, `AssistantProperties`/`AssistantConfig` (for a `@ConfigurationProperties` record), `frontend/src/api/products.ts`, `frontend/src/routes/PortfolioRoute.tsx`, `frontend/src/components/DashboardTiles.tsx`, `frontend/tests/api/portfolio.test.ts`.

### Backend
1. **Domain** `domain/briefing`: `BriefingTask` (enum of the 5 ids with display name and schedule text), `BriefingRun` (record), `BriefingStatus` (enum), `BriefingFeed` (task + runs + `readError` flag).
2. **Port** `ports/BriefingReadPort`: `List<BriefingRun> runs(BriefingTask task)`.
3. **Adapter** `adapters/briefing/FileBriefingReadAdapter`:
   - reads `<app.briefings.dir>/<id>.json` **only for the 5 enum ids**, never a path built from request input
   - rejects files over 1 MB, caps at 60 runs, truncates text fields to the limits above, drops `setup` rows and malformed rows
   - malformed JSON or an IO error gives an empty list plus a readError flag. Never a 500, and never leak the file path or contents in the error.
   - missing directory or file gives an empty list (not an error)
4. **Config** `BriefingsProperties` record `app.briefings.dir`, default `${RM_BRIEFINGS_DIR:./briefings/data}` in `application.yml`.
5. **Application** `BriefingService.feeds()`: all 5 tasks in enum order, runs newest first.
6. **Web** `BriefingController` `GET /api/briefings` → `{ "tasks": [{ "id", "name", "schedule", "readError", "latest": run|null, "runs": [...] }] }`. It requires authentication (it already falls under `/api/**`). It is GET-only, with no write endpoints.

### Frontend
1. `frontend/src/api/briefings.ts`: types + `useBriefings()` via `apiFetch`, query key `['briefings']`, `refetchInterval` 5 minutes.
2. `frontend/src/routes/BriefingsRoute.tsx`:
   - header "Briefings" + one-line summary ("N of 5 briefings need attention · latest run <time>")
   - a card per task: name, schedule, status pill (All clear / Needs attention / Quiet / Run failed / Not run yet), attention count, headline, h1–h3 bullets, last run time in Africa/Cairo
   - a run history table across all tasks, newest first (Task, Run, Status, Headline, Needs attention)
   - loading, error-with-retry and empty states in the same style as PortfolioRoute; a card shows "Couldn't read this briefing's file" when `readError`
   - Tailwind classes consistent with the rest of the app, accessible (status is not conveyed by colour alone)
3. `router.tsx`: add `/briefings` under `authenticatedRoute`. `AppShell.tsx`: add a "Briefings" nav link after Portfolio.

### Config file and runtime
1. Create `briefings/config.json` (content at the bottom of this prompt) and `briefings/README.md` explaining each field and the data contract.
2. `.gitignore`: add `briefings/data/`. Keep `briefings/config.json` tracked.
3. `compose.app.yaml` app service: bind-mount `./briefings/data` read-only into the container (for example `/app/briefings/data:ro`) and set `RM_BRIEFINGS_DIR` to that path. Running `mvn spring-boot:run` from the repo root must also work with the default.
4. Create an empty `briefings/data/.gitkeep`, or have the adapter tolerate a missing dir (it must anyway).

## TDD (non-negotiable)
Write each failing test first, watch it fail, then implement. Never weaken or skip a test.

Success criteria. Map each to a test in a table in the GSD summary:
- **B1** Adapter returns runs for a valid file, newest first, max 60
- **B2** Missing file or dir gives an empty list, readError=false
- **B3** Malformed JSON gives an empty list, readError=true, no exception escapes
- **B4** File > 1 MB is rejected (readError=true)
- **B5** Over-length text is truncated; `setup` rows and rows with an unknown status are dropped
- **B6** Only the 5 enum ids are ever read (no path from input; assert no other file is opened)
- **B7** `GET /api/briefings` returns 200 with all 5 tasks in order when authenticated
- **B8** `GET /api/briefings` returns 401 ProblemDetail when unauthenticated; POST/PUT/DELETE are not mapped
- **F1** `useBriefings` calls `/api/briefings` and returns typed data
- **F2** Route renders a card per task with the right status label, incl. "Not run yet" for `latest: null`
- **F3** Route shows the summary count and the history table newest first
- **F4** Route shows the error state with Retry when the request fails, and the per-card read error message
- **F5** Nav contains a "Briefings" link to `/briefings`

## Verify before claiming done
- `mvn test` (at least the new tests plus the existing web-layer tests) is green
- `cd frontend && npm run test && npm run typecheck` is green
- Rebuild the stack on port 8080 the same way STATE.md describes, put one sample file in `briefings/data/jira_digest.json`, open `http://localhost:8080/briefings` and confirm that card renders. Then delete the sample file.
- Record it in `.planning` as a GSD quick task and update STATE.md's last activity.

## Constraints (from CLAUDE.md, still apply)
- Jira stays read-only. This feature doesn't call Jira at all.
- No expression language or templating on the briefing text: render it as plain text only, never `dangerouslySetInnerHTML`.

## briefings/config.json
```json
{
  "version": 1,
  "jira": {
    "site": "https://evisionegypt.atlassian.net",
    "cloudId": "0f11e50f-b5e1-41f3-af5c-8da235438ffc",
    "projects": ["CNV"],
    "includeOtherProjectsWhereIAmInvolved": true,
    "lookbackDays": 30
  },
  "workWeek": { "days": ["Sun", "Mon", "Tue", "Wed", "Thu"], "timezone": "Africa/Cairo" },
  "output": { "dataDir": "briefings/data", "keepRuns": 60, "alsoUpdateClaudeDashboard": true },
  "tasks": {
    "jira_digest": { "enabled": true, "projects": null, "staleAfterWorkingDays": 3, "dueSoonDays": 5 },
    "sprint_check": { "enabled": true, "projects": null, "backlogItemsWhenNoFutureSprint": 15 },
    "weekly_status": { "enabled": true, "projects": null },
    "reg_watch": {
      "enabled": true, "lookbackDays": 7, "deadlineHorizonDays": 90,
      "topics": ["ISO 20022 / CBPR+ / HVPS+", "Swift Standards Release", "Buna", "Central Bank of Egypt", "SAMA", "CBUAE", "FATF R.16"]
    },
    "gulf_invest": {
      "enabled": true, "lookbackDays": 7,
      "countries": ["Saudi Arabia", "UAE", "Qatar", "Kuwait", "Bahrain", "Oman"]
    }
  }
}
```

When finished, report: the criteria-to-test table, the commands you ran with results, the files changed, and anything you couldn't verify.
