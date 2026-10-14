# Release 4.3.0 notes

Release 4.3.0 of the Orchard Ledger platform is planned for October 2026.

## Included tickets

- PROJ-130 "Move nightly database backup to 02:00". The nightly database backup job now starts
  at 02:00 instead of midnight to avoid overlapping with the batch window.

## Status

- Development is complete for PROJ-130.
- The release is in the planning stage.
- There is no approval record and no change record for this release.

## Compatibility

- No schema changes.
- Operations teams should update their monitoring schedules for the new backup time.

## Contacts

- Questions go to the Orchard Ledger operations team.

## Open items

- Test evidence for PROJ-130 is being collected.
- The deployment runbook for 4.3.0 has not been written yet.

End of notes for release 4.3.0.
