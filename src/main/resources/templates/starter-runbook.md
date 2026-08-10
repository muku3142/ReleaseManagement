# Deployment runbook — {{release.name}} {{release.version}}

**Status:** {{release.status}}
**Jira project:** {{release.jiraProjectKey}}
**Last refreshed from Jira:** {{release.lastRefreshedAt}}

## What is being deployed

{{#items}}
### {{title}}

{{^isManual}}
{{key}} · {{issueType}} · {{status}}{{#resolution}} · {{resolution}}{{/resolution}}
{{/isManual}}
{{#isManual}}
Manual item — {{category}}
{{/isManual}}

{{description}}

{{^commits}}
_No linked code for this item._
{{/commits}}
{{#commits}}
- `{{id}}` {{message}} — {{author}}
{{/commits}}

{{/items}}

## Deploy steps

Ordered, and safe to re-run from the top if a step fails part-way.

1. Announce the deployment window and confirm no other change is in flight.
2. Take a database backup and record the backup identifier here.
3. Apply any database scripts listed under **What is being deployed** above, in the order they
   appear, checking the row counts each one reports.
4. Deploy the application artefacts for {{release.name}} {{release.version}}.
5. Apply the configuration changes listed above, then restart the affected services.
6. Confirm every service reports healthy before starting post-deploy validation.

## Post-deploy validation

Each check is pass/fail. Record the result next to it; do not proceed to sign-off with an
unrecorded check.

1. Health endpoint returns healthy for every deployed service.
2. Application logs show no new errors in the first ten minutes after restart.
3. Sign in as a normal user and complete one end-to-end transaction.
4. Spot-check one ticket from each section of **What is being deployed** against its acceptance
   criteria.
5. Confirm scheduled jobs and integrations resumed on their next tick.

## Rollback

**Trigger conditions — roll back if any one of these is true:**

- Any post-deploy validation check above fails and cannot be resolved inside the deployment window.
- Error rates or response times are materially worse than before the deploy.
- A database script reported an unexpected row count or failed part-way.
- A dependent system is unable to integrate with the deployed version.

**Rollback procedure:**

1. Announce the rollback and stop any traffic being routed to the new version.
2. Redeploy the previously released application artefacts.
3. Revert the configuration changes applied in step 5 of **Deploy steps**.
4. Restore the database from the backup taken in step 2 of **Deploy steps**, if and only if a
   database script ran. Note that restoring discards data written since the backup.
5. Re-run the post-deploy validation checks against the restored version.
6. Record what triggered the rollback so the release can be reassembled.
