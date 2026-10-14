# Deployment runbook for release 4.2.0

This runbook describes how release 4.2.0 is deployed and rolled back.

## Environment ladder

Releases are promoted through the ladder DEV -> SIT -> UAT -> Staging -> Production.
A release must be deployed to each environment in order.

## Deployment steps

1. Announce the deployment window in the release channel.
2. Take a snapshot of the target environment database.
3. Stop the application service on the target environment.
4. Deploy the 4.2.0 package using the standard deployment job.
5. Start the application service and wait for the health check to report ready.
6. Rollback step: if the health check fails within 10 minutes, redeploy the previous package
   and restore the snapshot taken in step 2.

## Deployment record

- Release 4.2.0 was deployed to Staging on 2026-09-20.
- No deployment of 4.2.0 to Production is recorded in this runbook.

## Post-deployment

- Verify the login flow and the audit report export.
- Post the result in the release channel.

End of runbook.
