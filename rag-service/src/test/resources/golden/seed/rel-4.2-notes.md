# Release 4.2.0 notes

Release 4.2.0 of the Orchard Ledger platform is the September 2026 maintenance release.

## Included tickets

- PROJ-101 "Fix login timeout after 15 minutes". Sessions now stay valid for the configured
  idle period instead of expiring after a fixed 15 minutes.
- PROJ-102 "Add CSV export to the audit report". The audit report page gains a Download CSV button.
- PROJ-107 "Upgrade the PDF library". The PDF rendering library moves to its next minor version.

## Quality and sign-off

- QA sign-off recorded on 2026-09-14 by the QA lead.
- No UAT sign-off is recorded yet for this release.

## Known issues

- None reported at the time of writing.

## Compatibility

- No database schema changes.
- No configuration changes are required for the three tickets above.
- Clients on 4.1.x can upgrade in place.

## Contacts

- Release manager: the release management team mailbox.
- Questions about PROJ-101, PROJ-102 and PROJ-107 go to the Orchard Ledger product team.

End of notes for release 4.2.0.
