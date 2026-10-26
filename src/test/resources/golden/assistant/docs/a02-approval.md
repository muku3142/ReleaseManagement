# Change approval request - Orders Platform 4.2.1

Requested for 2026-03-14 22:00 UTC to 2026-03-15 00:00 UTC on PROD. The change covers PROJ-412 and PROJ-415.

## Risk

Risk level: medium. The schema migration may take longer than planned on a large orders table. The deployment must not start before the database snapshot is complete. A second approver is not required for this change.

## Impact

- Downtime window: the API will be unavailable for up to 30 minutes.
- Customers will see a maintenance page and may retry after the window.
- Batch exports should not run during the window; they will resume automatically afterwards.
- No data will be lost. Reports might show stale numbers until 2026-03-15 02:00 UTC.

## Backout

The change can only be backed out before the schema migration. After it, backing out will require restoring the snapshot and must be approved by the release manager. A backout is not required if the smoke test passes.

## Sign-off

The change manager approves or rejects this request before the window opens. Silence is never consent.
