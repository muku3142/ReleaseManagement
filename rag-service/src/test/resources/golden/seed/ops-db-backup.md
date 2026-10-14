# Database backup policy

This policy applies to all Orchard Ledger environments.

## Schedule

- Full backups run nightly.
- Incremental backups run every 4 hours during business days.

## Retention

- Backups are retained for 35 days.
- After 35 days, backups are deleted automatically by the retention job.

## Storage

- Backups are stored in the regional object store with encryption at rest.
- Access to backups is limited to the operations team.

## Restore

- Restore requests are raised through the operations queue.
- A restore test is performed once per quarter.

## Ownership

- Policy owner: operations team.
- Review cycle: yearly.

End of policy.
