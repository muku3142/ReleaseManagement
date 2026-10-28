# Scheduled tasks

Exported 2026-10-25. All times are Cairo time (Africa/Cairo). Each `.md` file holds one task's settings in the header and the exact instructions it runs below that.

| File | Task | When | Uses |
|---|---|---|---|
| 01-daily-jira-digest.md | Daily Jira digest | Sun–Thu 08:44 | Jira (CNV) |
| 02-sprint-backlog-check.md | Sprint backlog check | Wed 09:41 | Jira (CNV) |
| 03-weekly-status-update.md | Weekly status update | Thu 13:41 | Jira (CNV) |
| 04-payments-regulation-watch.md | Payments regulation watch | Sun 08:36 | Web search |
| 05-saudi-gulf-investment-brief.md | Saudi and Gulf investment brief | Mon 09:33 | Web search |

## Where the results go
- A push notification to your phone when a run finishes
- A session per run in the Claude app, holding the full briefing
- One row per run on the PM Briefings Hub dashboard: https://claude.ai/artifact/EXbrCkiYM8PztrYXcTY1FG
- After the move to your computer, also `briefings/data/<task>.json` in the ReleaseManagement repo, which the new Briefings page at http://localhost:8080/briefings reads

## config.json
`config.json` is the settings file that goes in the repo at `briefings/config.json`. Once the tasks are moved to your computer, each run reads it first:
- `jira.site` / `jira.cloudId`: which Jira site to read. The sign-in itself comes from the Atlassian connector in Claude.
- `jira.projects`: the projects to summarise, for example `["CNV"]`.
- `tasks.<id>.projects`: a per-task override. `null` means use `jira.projects`.
- `tasks.<id>.enabled`: set to `false` to skip a task without deleting it.

Editing these files doesn't change the live tasks. The live tasks are stored in your Claude account, so to change one, ask Claude or edit it in the scheduled tasks list.
