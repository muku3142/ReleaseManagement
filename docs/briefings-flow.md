# Briefings: scheduled tasks flow

How the five scheduled briefings reach the `/briefings` page: Claude produces them first, and the app falls back
for the three Jira briefings when Claude isn't working (Phase 17.1, decisions D-17.1-01..23).

Built so far: the Claude feed, the `/briefings` page, product Jira keys, the digest rules and the Settings UI
(17.1-01..05, 09, 10). Still to come: the Jira search client, sprint and weekly rules, run storage, the runner,
the scheduler and the settings API (17.1-06..08, 11..16). Sections below mark which is which.

## 1. The briefings

| Task | Name | Slot (Africa/Cairo) | Producer | App fallback |
|---|---|---|---|---|
| `jira_digest` | Daily Jira digest | Sun-Thu 08:44 | Claude, then app | Yes, per product |
| `sprint_check` | Sprint backlog check | Wed 09:41 | Claude, then app | Yes, per product |
| `weekly_status` | Weekly status update | Thu 13:41 | Claude, then app | Yes, per product |
| `reg_watch` | Payments regulation watch | Sun 08:36 | Claude only | No (needs web search) |
| `gulf_invest` | Saudi & Gulf investment brief | Mon 09:33 | Claude only | No (needs web search) |

## 2. The whole picture

```
  Claude account (primary)                          ReleaseManagement app (fallback)
  ------------------------                          --------------------------------

  Claude scheduled tasks                             Scheduler tick (every minute)       [to come: 17.1-14]
  (5 tasks, cron Africa/Cairo)                              |
          |                                                 | for each Jira task whose slot
          | writes one row per run                          | + grace period (30 min) has passed
          v                                                 v
  briefings/data/<task>.json  ---- read-only mount ---> Fallback check (per product)    [to come: 17.1-11/13]
  (array of runs, newest 60)       ./briefings ->            |
          |                        /app/briefings:ro         | Claude result for this slot
          |                                                  | missing or "failed"?
          |                                                  v
          |                                            Run the briefing in the app       [to come: 17.1-11]
          |                                            (Jira rules, no Anthropic)
          |                                                  |
          |                                                  v
          |                                            briefing_run table (V19)         [table built; writes to come]
          |                                                  |
          v                                                  v
  FileBriefingReadAdapter  [built]                   App runs per task and product
  (5 fixed ids, 1 MB cap, 60 runs,                          |
   truncation, bad rows dropped,                            |
   readError instead of a 500)                              |
          |                                                  |
          +------------------------+-------------------------+
                                   v
                     GET /api/briefings  [built; app results added in 17.1-12]
                                   |
                                   v
                     /briefings page  [built]
                     card per task: Claude result first,
                     then "App fallback" rows per product
```

## 3. One slot, step by step (Jira tasks only)

```
slot time reached (e.g. Sun 08:44 Cairo) and the grace period has passed
  |
  v
task enabled in Settings? --no--> nothing happens
  | yes
  v
for each ACTIVE product (archived products never run):
  |
  +-- product has no Jira project key? -----------> record "skipped" (NO_JIRA_PROJECT)
  |
  +-- Claude covers this product's key            (briefings/config.json: tasks.<id>.projects,
  |   AND Claude's row for this slot exists        or jira.projects when that is null)
  |   AND its status is not "failed"? -----------> record "skipped" (CLAUDE_COVERED)
  |
  +-- otherwise: run the app fallback
        |
        v
      scheduler user set and their stored Jira credential works?
        | no ----------------------------------------> record "needs_credential"
        | yes                                           ("Use my Jira credential" in Settings)
        v
      read Jira (read-only, /search/jql with cursor pagination)
        |
        v
      apply the task's rules
        - digest:  blockers, stale 3+ working days, not-ready stories,
                   changes since the last run, due within 5 days or overdue
        - sprint:  next sprint (or top 15 backlog items) rated Yes / Partly / No
        - weekly:  done / in progress / blocked / added this week,
                   committed vs done, overdue; On track / At risk / Off track
        |
        v
      assistant enabled? --yes--> self-hosted model writes the headline only,
        |                         under a short deadline; failure keeps the
        |                         rule-based headline (never fails the run)
        v
      store one run per (task, product, slot)   - a repeat tick can't duplicate it
      prune to "runs kept" (default 60) per task and product
```

The status of each run is one of: `ok`, `attention`, `quiet`, `failed`, `needs_credential`, `skipped`.

## 4. Manual run

```
Settings > Briefings > "Run briefing now"            [UI built; API to come: 17.1-15]
  -> POST /api/settings/briefings/tasks/{id}/run
  -> runs the app briefing for every active product with a Jira key,
     using saved settings, and IGNORES the Claude check
  -> a Jira credential problem is recorded on the run ("needs_credential");
     it is never returned as HTTP 401, so it can't sign you out
```

## 5. What controls it

| Setting | Where | Default |
|---|---|---|
| Slots (days and time) per task | Settings > Briefings (database) | from the table in section 1 |
| Grace period | Settings > Briefings | 30 min (1-240) |
| Thresholds (stale days, due-soon days, backlog items) | Settings, else `briefings/config.json` | 3 / 5 / 15 |
| Runs kept per task and product | Settings, else `briefings/config.json` | 60 (10-500) |
| Which Jira projects Claude covers | `briefings/config.json` (read-only in the app) | `["CNV"]` |
| Scheduler user | Settings: "Use my Jira credential" | not set |
| Claude data folder | `RM_BRIEFINGS_DIR` (shown read-only in Settings) | `./briefings/data` |
| Product Jira project keys | Portfolio page, one product per key | backfilled from each product's releases |

## 6. Trust boundaries

- Claude's JSON files are **untrusted data**: only the five fixed file names are read, never a path from a request
  or the database; files over 1 MB or malformed rows are dropped; all text is rendered as plain text.
- Jira is **read-only**; the scheduler uses the stored credential of the person who clicked "Use my Jira
  credential", never a service account.
- Nothing in the fallback calls Anthropic or any external model; the optional headline uses the self-hosted model.
