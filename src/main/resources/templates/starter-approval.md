# Change approval — {{release.name}} {{release.version}}

| Field | Value |
|---|---|
| Release | {{release.name}} |
| Version | {{release.version}}  |
| Status | {{release.status}} |
| Created | {{release.createdAt}} |
| Frozen | {{release.frozenAt}} |

## Scope of change

{{#items}}
- {{title}}{{^isManual}} — {{key}} ({{issueType}}){{/isManual}}{{#isManual}} — manual item ({{category}}){{/isManual}}
{{/items}}
{{^items}}
_Nothing is in scope for this release._
{{/items}}

## Risk

**Overall risk rating:** Medium. Raise to High if this release contains a database script, a change
to authentication or authorisation, or a change to an external integration; lower to Low only if
every item above is a user-interface-only change.

- **Data risk.** Any database script in scope can alter or delete existing rows. Mitigated by the
  backup taken in the runbook's deploy steps.
- **Availability risk.** Services restart during deployment, so there is a short interruption for
  anyone using the system during the window.
- **Regression risk.** Changes in scope may affect behaviour that was not explicitly tested.
  Mitigated by the post-deploy validation checks in the runbook.
- **Integration risk.** Dependent systems may be running against the previous version's contract.

## Impact

- **Users affected:** everyone using {{release.name}} during and immediately after the deployment
  window.
- **Systems affected:** the services deployed for {{release.version}}, their database, and any
  system integrating with them.
- **Expected interruption:** the length of the service restart in the runbook's deploy steps.
- **Business impact if the change is not made:** the items listed under **Scope of change** remain
  unresolved until the next release.

## Backout plan

The backout plan is the **Rollback** section of this release's deployment runbook, and it is
authoritative — do not maintain a second, divergent copy here.

- **Who decides:** the release manager, against the runbook's trigger conditions.
- **Decision point:** at the end of post-deploy validation, before the deployment window closes.
- **Expected time to back out:** the same as the deploy, plus a database restore if a database
  script ran.
- **Data loss on backout:** none, unless a database restore is required, in which case data written
  after the backup is discarded.
