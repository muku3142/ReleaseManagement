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

1. _Replace with the ordered deploy steps for this release._

## Post-deploy validation

1. _Replace with the checks that prove the deploy succeeded._

## Rollback

**Trigger conditions:** _Replace with the conditions that make rollback the right call._

1. _Replace with the ordered rollback steps._
