# Briefings

Claude scheduled tasks write one result file per briefing into `briefings/data/`; the app reads them and shows them on the
Briefings page. `config.json` here is the shared configuration for the Claude tasks and the app.

## config.json

| Field | Meaning |
|---|---|
| `jira.projects` | Jira projects Claude covers. The app uses this list as the projects it covers too (D-17.1-04). |
| `tasks.<id>.projects` | Per-task override. `null` means use `jira.projects`. |
| `tasks.<id>.enabled` | Whether Claude runs this task. A task disabled on the Claude side writes no rows, so the app falls back for it every slot. |
| `tasks.jira_digest.staleAfterWorkingDays`, `dueSoonDays` | Thresholds. The app uses them as defaults; Settings > Briefings can override them (D-17.1-16). |
| `tasks.sprint_check.backlogItemsWhenNoFutureSprint` | Threshold, same rule as above. |
| `output.keepRuns` | How many runs are kept per file. App default; Settings can override. |

`jira.site`, `jira.cloudId`, `jira.includeOtherProjectsWhereIAmInvolved`, `workWeek`, the rest of `output.*` and the
`reg_watch` / `gulf_invest` settings are for the Claude tasks only; the app ignores them (D-17.1-06).

## Data contract

`briefings/data/<id>.json` is a JSON array of runs, **oldest first**, at most 60. `<id>` is one of `jira_digest`,
`sprint_check`, `weekly_status`, `reg_watch`, `gulf_invest`.

```json
[{"run_at":"2026-10-26T05:44:12Z","status":"attention","headline":"3 blockers and 2 stale stories in CNV","attention":5,"h1":"CNV-3120 blocked on bank config","h2":"...","h3":null}]
```

- `status` is `ok`, `attention`, `quiet`, `failed` or `setup`. `setup` rows are placeholders and are ignored.
- `headline` up to 140 characters; `h1`..`h3` up to 120 characters or null; `attention` is an integer >= 0 or null.
  Longer text is cut by the app; control and bidirectional characters are removed.
- A missing file means "not run yet", not an error. A malformed file, one over 1 MB, or a symlink is shown as unreadable.
- Write atomically: write a temp file, then rename it over `<id>.json`, so the app never reads a half-written file.
